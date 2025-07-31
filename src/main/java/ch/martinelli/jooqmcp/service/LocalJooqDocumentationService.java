package ch.martinelli.jooqmcp.service;

import ch.martinelli.jooqmcp.search.InvertedIndex;
import ch.martinelli.jooqmcp.util.TextProcessor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class LocalJooqDocumentationService {

    private static final Logger logger = LoggerFactory.getLogger(LocalJooqDocumentationService.class);
    
    @Value("classpath:docs/manual-single-page.html")
    private Resource documentationFile;
    
    private Document fullDocument;
    private Map<String, DocumentationSection> sectionsByTitle = new ConcurrentHashMap<>();
    private Map<String, DocumentationSection> sectionsById = new ConcurrentHashMap<>();
    private List<String> allSectionTexts = new ArrayList<>();
    private Map<String, List<CodeExample>> codeExamplesByTopic = new ConcurrentHashMap<>();
    private InvertedIndex searchIndex = new InvertedIndex();
    private Map<String, List<SearchResult>> searchCache = new ConcurrentHashMap<>();

    public static class DocumentationSection {
        private final String id;
        private final String title;
        private final String content;
        private final int level;
        private final String breadcrumb;
        private final List<CodeExample> codeExamples;

        public DocumentationSection(String id, String title, String content, int level, String breadcrumb) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.level = level;
            this.breadcrumb = breadcrumb;
            this.codeExamples = new ArrayList<>();
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public int getLevel() { return level; }
        public String getBreadcrumb() { return breadcrumb; }
        public List<CodeExample> getCodeExamples() { return codeExamples; }
    }

    public static class CodeExample {
        private final String code;
        private final String context;
        private final String language;

        public CodeExample(String code, String context, String language) {
            this.code = code;
            this.context = context;
            this.language = language;
        }

        public String getCode() { return code; }
        public String getContext() { return context; }
        public String getLanguage() { return language; }
    }

    public static class SearchResult {
        private final String title;
        private final String content;
        private final String section;
        private final double relevanceScore;
        private final Set<String> matchedTerms;

        public SearchResult(String title, String content, String section, double relevanceScore) {
            this(title, content, section, relevanceScore, Collections.emptySet());
        }
        
        public SearchResult(String title, String content, String section, double relevanceScore, Set<String> matchedTerms) {
            this.title = title;
            this.content = content;
            this.section = section;
            this.relevanceScore = relevanceScore;
            this.matchedTerms = matchedTerms != null ? matchedTerms : Collections.emptySet();
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getSection() { return section; }
        public double getRelevanceScore() { return relevanceScore; }
        public Set<String> getMatchedTerms() { return matchedTerms; }
    }

    @PostConstruct
    public void initialize() {
        try {
            logger.info("Loading jOOQ documentation from local HTML file...");
            long startTime = System.currentTimeMillis();
            
            fullDocument = Jsoup.parse(documentationFile.getInputStream(), "UTF-8", "");
            parseDocumentSections();
            buildSearchIndex();
            buildInvertedIndex();
            
            long loadTime = System.currentTimeMillis() - startTime;
            Map<String, Object> indexStats = searchIndex.getStatistics();
            logger.info("Documentation loaded successfully in {}ms. Found {} sections with {} code examples. Index stats: {}", 
                loadTime, sectionsByTitle.size(), codeExamplesByTopic.values().stream().mapToInt(List::size).sum(), indexStats);
            
        } catch (IOException e) {
            logger.error("Failed to load documentation file", e);
            throw new RuntimeException("Cannot initialize documentation service", e);
        }
    }

    private void parseDocumentSections() {
        Elements headers = fullDocument.select("h1, h2, h3, h4, h5, h6");
        List<String> breadcrumbStack = new ArrayList<>();
        
        for (Element header : headers) {
            int level = Integer.parseInt(header.tagName().substring(1));
            String title = header.text().trim();
            String id = header.id();
            
            if (title.isEmpty()) continue;
            
            // Update breadcrumb stack
            while (breadcrumbStack.size() >= level) {
                breadcrumbStack.remove(breadcrumbStack.size() - 1);
            }
            breadcrumbStack.add(title);
            
            String breadcrumb = String.join(" > ", breadcrumbStack);
            String sectionContent = extractSectionContent(header);
            
            DocumentationSection section = new DocumentationSection(id, title, sectionContent, level, breadcrumb);
            
            // Extract code examples from this section
            extractCodeExamples(header, section);
            
            sectionsByTitle.put(title.toLowerCase(), section);
            if (id != null && !id.isEmpty()) {
                sectionsById.put(id, section);
            }
            
            allSectionTexts.add(title + " " + sectionContent);
        }
    }

    private String extractSectionContent(Element header) {
        StringBuilder content = new StringBuilder();
        Element nextElement = header.nextElementSibling();
        
        while (nextElement != null && !nextElement.tagName().matches("h[1-6]")) {
            if (nextElement.tagName().equals("p")) {
                content.append(nextElement.text()).append("\n\n");
            } else if (nextElement.tagName().equals("ul") || nextElement.tagName().equals("ol")) {
                Elements items = nextElement.select("li");
                for (Element item : items) {
                    content.append("• ").append(item.text()).append("\n");
                }
                content.append("\n");
            } else if (!nextElement.tagName().equals("pre") && !nextElement.tagName().equals("code")) {
                String text = nextElement.text().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n\n");
                }
            }
            nextElement = nextElement.nextElementSibling();
        }
        
        return content.toString().trim();
    }

    private void extractCodeExamples(Element header, DocumentationSection section) {
        Element nextElement = header.nextElementSibling();
        
        while (nextElement != null && !nextElement.tagName().matches("h[1-6]")) {
            if (nextElement.tagName().equals("pre")) {
                Element codeElement = nextElement.selectFirst("code");
                String code = codeElement != null ? codeElement.text() : nextElement.text();
                String language = detectLanguage(code);
                String context = extractCodeContext(nextElement);
                
                CodeExample example = new CodeExample(code, context, language);
                section.getCodeExamples().add(example);
                
                // Index by topic keywords
                indexCodeExampleByTopics(example, section.getTitle());
            }
            nextElement = nextElement.nextElementSibling();
        }
    }

    private String detectLanguage(String code) {
        if (code.contains("DSL.select") || code.contains("create.") || code.contains("import org.jooq")) {
            return "java";
        } else if (code.contains("SELECT") || code.contains("INSERT") || code.contains("UPDATE") || code.contains("DELETE")) {
            return "sql";
        } else if (code.contains("<") && code.contains(">")) {
            return "xml";
        }
        return "text";
    }

    private String extractCodeContext(Element codeElement) {
        Element prevElement = codeElement.previousElementSibling();
        if (prevElement != null && prevElement.tagName().equals("p")) {
            return prevElement.text();
        }
        
        Element nextElement = codeElement.nextElementSibling();
        if (nextElement != null && nextElement.tagName().equals("p")) {
            return nextElement.text();
        }
        
        return "";
    }

    private void indexCodeExampleByTopics(CodeExample example, String sectionTitle) {
        String[] topics = {
            "select", "insert", "update", "delete", "join", "where", "group", "order", 
            "having", "union", "subquery", "transaction", "batch", "stored", "procedure"
        };
        
        String codeAndTitle = (example.getCode() + " " + sectionTitle).toLowerCase();
        
        for (String topic : topics) {
            if (codeAndTitle.contains(topic)) {
                codeExamplesByTopic.computeIfAbsent(topic, k -> new ArrayList<>()).add(example);
            }
        }
    }

    private void buildSearchIndex() {
        // Pre-build commonly searched terms for faster lookup
        Map<String, List<DocumentationSection>> commonTerms = new HashMap<>();
        String[] searchTerms = {
            "select", "insert", "update", "delete", "join", "where", "group by", "order by",
            "transaction", "batch", "code generation", "dialect", "dsl", "record", "table"
        };
        
        for (String term : searchTerms) {
            commonTerms.put(term, searchSections(term, Integer.MAX_VALUE));
        }
    }
    
    private void buildInvertedIndex() {
        // Add all sections to the inverted index
        for (DocumentationSection section : sectionsByTitle.values()) {
            searchIndex.addDocument(section);
        }
    }

    public List<SearchResult> searchDocumentation(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        
        // Check cache first
        List<SearchResult> cachedResults = searchCache.get(normalizedQuery);
        if (cachedResults != null) {
            return cachedResults;
        }
        
        // Use advanced search with inverted index
        List<InvertedIndex.SearchMatch> matches = searchIndex.search(query, 10);
        
        List<SearchResult> results = matches.stream()
            .map(match -> {
                DocumentationSection section = match.getDocument().getSection();
                String snippet = createEnhancedSnippet(section.getContent(), normalizedQuery, match.getMatchedTerms());
                return new SearchResult(section.getTitle(), snippet, section.getBreadcrumb(), 
                                     match.getScore(), match.getMatchedTerms());
            })
            .collect(Collectors.toList());
        
        // Cache results for frequently searched terms
        if (results.size() > 0) {
            searchCache.put(normalizedQuery, results);
            
            // Limit cache size
            if (searchCache.size() > 100) {
                String oldestKey = searchCache.keySet().iterator().next();
                searchCache.remove(oldestKey);
            }
        }
        
        return results;
    }

    private List<DocumentationSection> searchSections(String query, int limit) {
        String lowerQuery = query.toLowerCase();
        
        return sectionsByTitle.values().stream()
            .filter(section -> 
                section.getTitle().toLowerCase().contains(lowerQuery) ||
                section.getContent().toLowerCase().contains(lowerQuery) ||
                section.getBreadcrumb().toLowerCase().contains(lowerQuery))
            .sorted((s1, s2) -> {
                boolean s1TitleMatch = s1.getTitle().toLowerCase().contains(lowerQuery);
                boolean s2TitleMatch = s2.getTitle().toLowerCase().contains(lowerQuery);
                if (s1TitleMatch != s2TitleMatch) {
                    return s1TitleMatch ? -1 : 1;
                }
                return Integer.compare(s1.getLevel(), s2.getLevel());
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    private double calculateRelevanceScore(DocumentationSection section, String query) {
        double score = 0.0;
        String title = section.getTitle().toLowerCase();
        String content = section.getContent().toLowerCase();
        
        // Title exact match
        if (title.equals(query)) score += 100;
        // Title contains query
        else if (title.contains(query)) score += 50;
        
        // Content contains query
        long contentMatches = content.split(query, -1).length - 1;
        score += contentMatches * 10;
        
        // Boost shorter sections (more focused)
        score += Math.max(0, 50 - section.getContent().length() / 20);
        
        // Boost higher-level sections
        score += (7 - section.getLevel()) * 5;
        
        return score;
    }

    private String createSnippet(String content, String query) {
        if (content.length() <= 200) {
            return content;
        }
        
        int queryIndex = content.toLowerCase().indexOf(query.toLowerCase());
        if (queryIndex == -1) {
            return content.substring(0, 200) + "...";
        }
        
        int start = Math.max(0, queryIndex - 50);
        int end = Math.min(content.length(), queryIndex + query.length() + 150);
        
        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        
        return snippet;
    }
    
    private String createEnhancedSnippet(String content, String query, Set<String> matchedTerms) {
        if (content.length() <= 300) {
            return TextProcessor.highlightTerms(content, matchedTerms);
        }
        
        // Find the best position to create snippet around
        int bestPosition = findBestSnippetPosition(content.toLowerCase(), query, matchedTerms);
        
        int start = Math.max(0, bestPosition - 100);
        int end = Math.min(content.length(), bestPosition + 200);
        
        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        
        return TextProcessor.highlightTerms(snippet, matchedTerms);
    }
    
    private int findBestSnippetPosition(String content, String query, Set<String> matchedTerms) {
        // Try to find position with most matched terms
        int bestPosition = 0;
        int maxMatches = 0;
        
        // Check every 50 characters
        for (int i = 0; i < content.length() - 200; i += 50) {
            String window = content.substring(i, Math.min(content.length(), i + 200));
            int matches = 0;
            
            for (String term : matchedTerms) {
                if (window.contains(term.toLowerCase())) {
                    matches++;
                }
            }
            
            if (matches > maxMatches) {
                maxMatches = matches;
                bestPosition = i + 100; // Center of window
            }
        }
        
        // Fallback to query position if no matches found
        if (maxMatches == 0) {
            int queryIndex = content.indexOf(query);
            if (queryIndex != -1) {
                bestPosition = queryIndex;
            }
        }
        
        return bestPosition;
    }

    public List<CodeExample> getCodeExamples(String topic) {
        String normalizedTopic = topic.toLowerCase().trim();
        return codeExamplesByTopic.getOrDefault(normalizedTopic, Collections.emptyList());
    }

    public String getDocumentationContent(String topic) {
        // Try exact title match first
        DocumentationSection section = sectionsByTitle.get(topic.toLowerCase());
        if (section != null) {
            return formatSection(section);
        }
        
        // Try searching
        List<DocumentationSection> results = searchSections(topic, 1);
        if (!results.isEmpty()) {
            return formatSection(results.get(0));
        }
        
        return "No documentation found for topic: " + topic;
    }

    private String formatSection(DocumentationSection section) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("# ").append(section.getTitle()).append("\n\n");
        formatted.append("**Section:** ").append(section.getBreadcrumb()).append("\n\n");
        
        // Limit content to prevent buffer overflow
        String content = section.getContent();
        if (content.length() > 1500) {
            content = content.substring(0, 1500) + "...\n\n[Content truncated for size limits]";
        }
        formatted.append(content).append("\n\n");
        
        if (!section.getCodeExamples().isEmpty()) {
            formatted.append("## Code Examples\n\n");
            // Limit to max 2 examples to prevent buffer overflow
            int maxExamples = Math.min(2, section.getCodeExamples().size());
            for (int i = 0; i < maxExamples; i++) {
                CodeExample example = section.getCodeExamples().get(i);
                formatted.append("### Example ").append(i + 1).append("\n");
                if (!example.getContext().isEmpty() && example.getContext().length() <= 150) {
                    formatted.append(example.getContext()).append("\n\n");
                }
                
                // Limit code example size
                String code = example.getCode();
                if (code.length() > 500) {
                    code = code.substring(0, 500) + "\n// ... [Code truncated]";
                }
                
                formatted.append("```").append(example.getLanguage()).append("\n");
                formatted.append(code).append("\n");
                formatted.append("```\n\n");
                
                // Stop if response getting too large
                if (formatted.length() > 2500) {
                    formatted.append("[Additional examples truncated to prevent buffer overflow]\n");
                    break;
                }
            }
            
            if (section.getCodeExamples().size() > maxExamples) {
                formatted.append(String.format("[%d additional examples available - use more specific search terms]\n", 
                    section.getCodeExamples().size() - maxExamples));
            }
        }
        
        // Final safety check - ensure total response doesn't exceed limit
        String result = formatted.toString();
        if (result.length() > 3000) {
            result = result.substring(0, 3000) + "\n\n[Response truncated due to size limits]";
        }
        
        return result;
    }
}