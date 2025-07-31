package ch.martinelli.jooqmcp.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JooqDocumentationFetcher {

    private static final Logger logger = LoggerFactory.getLogger(JooqDocumentationFetcher.class);
    private static final String JOOQ_BASE_URL = "https://www.jooq.org/doc/3.21/manual/";
    private static final int TIMEOUT_MS = 10000;
    
    private final JooqDocumentationCrawler documentationCrawler;

    public JooqDocumentationFetcher(JooqDocumentationCrawler documentationCrawler) {
        this.documentationCrawler = documentationCrawler;
    }

    @Cacheable(value = "documentation", key = "#url")
    public String fetchDocumentationContent(String url) {
        try {
            logger.debug("Fetching documentation from: {}", url);
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("jOOQ-MCP-Server/1.0")
                    .get();

            // Extract main content
            Element content = doc.selectFirst("div.content, article, main");
            if (content == null) {
                content = doc.body();
            }

            // Clean up the content
            return cleanupContent(content);

        } catch (IOException e) {
            logger.error("Error fetching documentation from {}: {}", url, e.getMessage());
            return "Error fetching documentation: " + e.getMessage();
        }
    }

    public List<SearchResult> searchDocumentation(String query) {
        List<SearchResult> results = new ArrayList<>();
        
        // First, get URLs that match the query
        List<String> matchingUrls = documentationCrawler.searchUrls(query);
        
        // If we have matching URLs, search within those pages first
        if (!matchingUrls.isEmpty()) {
            for (String url : matchingUrls) {
                results.addAll(searchInPage(url, query));
                if (results.size() >= 20) {
                    break;
                }
            }
        }
        
        // If we don't have enough results, do a broader search
        if (results.size() < 10) {
            Map<String, JooqDocumentationCrawler.DocumentationPage> allPages = documentationCrawler.discoverAllUrls();
            
            for (String url : allPages.keySet()) {
                if (!matchingUrls.contains(url)) {
                    results.addAll(searchInPage(url, query));
                    if (results.size() >= 20) {
                        break;
                    }
                }
            }
        }

        // Sort results by relevance
        return results.stream()
            .sorted((r1, r2) -> {
                // Prioritize title matches
                boolean r1TitleMatch = r1.getTitle().toLowerCase().contains(query.toLowerCase());
                boolean r2TitleMatch = r2.getTitle().toLowerCase().contains(query.toLowerCase());
                if (r1TitleMatch != r2TitleMatch) {
                    return r1TitleMatch ? -1 : 1;
                }
                // Then by content length (shorter = more focused)
                return Integer.compare(r1.getContent().length(), r2.getContent().length());
            })
            .limit(10)
            .collect(Collectors.toList());
    }

    private List<SearchResult> searchInPage(String url, String query) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("jOOQ-MCP-Server/1.0")
                    .get();

            // Get page info for breadcrumb
            JooqDocumentationCrawler.DocumentationPage pageInfo = documentationCrawler.getPageInfo(url);
            String breadcrumb = pageInfo != null ? pageInfo.getBreadcrumb() : "";

            // Search all elements that might contain the query text
            Elements allElements = doc.select("h1, h2, h3, h4, p, li, code, pre, td");
            
            // Filter elements that contain the query (case-insensitive)
            String lowerQuery = query.toLowerCase();
            Elements matches = new Elements();
            
            for (Element element : allElements) {
                String text = element.text().toLowerCase();
                if (text.contains(lowerQuery)) {
                    matches.add(element);
                }
            }

            for (Element match : matches) {
                SearchResult result = new SearchResult();
                result.setTitle(getParentHeader(match));
                result.setContent(extractContext(match, query));
                result.setUrl(url + (match.id() != null && !match.id().isEmpty() ? "#" + match.id() : ""));
                result.setSection(breadcrumb);
                results.add(result);

                if (results.size() >= 5) { // Limit per page
                    break;
                }
            }

        } catch (IOException e) {
            logger.warn("Error searching page {}: {}", url, e.getMessage());
        }

        return results;
    }

    private String extractContext(Element element, String query) {
        String text = element.text();
        int queryIndex = text.toLowerCase().indexOf(query.toLowerCase());
        
        if (queryIndex == -1) {
            return text.length() > 200 ? text.substring(0, 200) + "..." : text;
        }
        
        // Extract context around the query
        int start = Math.max(0, queryIndex - 50);
        int end = Math.min(text.length(), queryIndex + query.length() + 150);
        
        String context = text.substring(start, end);
        if (start > 0) context = "..." + context;
        if (end < text.length()) context = context + "...";
        
        return context;
    }

    @Cacheable(value = "sqlExamples", key = "#topic")
    public String fetchSqlExamples(String topic) {
        String normalizedTopic = topic.toLowerCase().replace(" ", "-");
        String url = JOOQ_BASE_URL + "sql-building/" + normalizedTopic + "/";

        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("jOOQ-MCP-Server/1.0")
                    .get();

            // Extract code examples
            Elements codeBlocks = doc.select("pre code, div.code");
            if (codeBlocks.isEmpty()) {
                // Try alternative selectors
                codeBlocks = doc.select("pre, code.language-java, code.language-sql");
            }

            StringBuilder examples = new StringBuilder();
            examples.append("# jOOQ Examples for ").append(topic).append("\n\n");

            int exampleCount = 0;
            for (Element code : codeBlocks) {
                exampleCount++;
                examples.append("## Example ").append(exampleCount).append("\n");
                examples.append("```java\n");
                examples.append(code.text());
                examples.append("\n```\n\n");

                // Include surrounding context if available
                Element parent = code.parent();
                if (parent != null) {
                    Element prevSibling = parent.previousElementSibling();
                    if (prevSibling != null && prevSibling.tagName().equals("p")) {
                        examples.append("Context: ").append(prevSibling.text()).append("\n\n");
                    }
                }
            }

            return examples.toString();

        } catch (IOException e) {
            logger.error("Error fetching SQL examples for {}: {}", topic, e.getMessage());
            return "Unable to fetch SQL examples for " + topic + ". Please check if this is a valid jOOQ topic.";
        }
    }

    private String cleanupContent(Element content) {
        // Remove navigation, scripts, and other non-content elements
        content.select("nav, script, style, header, footer, .navigation, .sidebar").remove();

        // Extract text with basic formatting preserved
        StringBuilder cleaned = new StringBuilder();

        // Process all content in order to maintain structure
        processElementRecursively(content, cleaned, 0);

        return cleaned.toString().trim();
    }

    private void processElementRecursively(Element element, StringBuilder output, int depth) {
        for (Element child : element.children()) {
            String tag = child.tagName();
            
            switch (tag) {
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    int level = Integer.parseInt(tag.substring(1));
                    output.append("\n").append("#".repeat(level)).append(" ").append(child.text()).append("\n\n");
                    break;
                    
                case "p":
                    output.append(child.text()).append("\n\n");
                    break;
                    
                case "pre":
                    output.append("```\n").append(child.text()).append("\n```\n\n");
                    break;
                    
                case "code":
                    if (!child.parent().tagName().equals("pre")) {
                        output.append("`").append(child.text()).append("`");
                    }
                    break;
                    
                case "ul":
                case "ol":
                    Elements items = child.select("> li");
                    for (Element item : items) {
                        output.append("• ").append(item.text()).append("\n");
                    }
                    output.append("\n");
                    break;
                    
                case "section":
                case "div":
                case "article":
                    // Process nested content
                    processElementRecursively(child, output, depth + 1);
                    break;
                    
                case "table":
                    output.append("\n[Table with ").append(child.select("tr").size()).append(" rows]\n\n");
                    break;
                    
                default:
                    // For other elements, process their children
                    if (child.children().size() > 0) {
                        processElementRecursively(child, output, depth + 1);
                    }
            }
        }
    }

    public String fetchDocumentationContentWithAnchor(String url, String anchor) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("jOOQ-MCP-Server/1.0")
                    .get();

            // If anchor is specified, try to find that section
            if (anchor != null && !anchor.isEmpty()) {
                Element anchorElement = doc.getElementById(anchor.replace("#", ""));
                if (anchorElement != null) {
                    // Find the containing section
                    Element section = anchorElement;
                    while (section != null && !section.tagName().matches("section|article|div")) {
                        section = section.parent();
                    }
                    
                    if (section != null) {
                        return cleanupContent(section);
                    }
                }
            }

            // Fallback to regular content extraction
            return fetchDocumentationContent(url);
            
        } catch (IOException e) {
            logger.error("Error fetching documentation from {}: {}", url, e.getMessage());
            return "Error fetching documentation: " + e.getMessage();
        }
    }

    private String getParentHeader(Element element) {
        Element current = element;
        while (current != null) {
            if (current.tagName().matches("h[1-6]")) {
                return current.text();
            }
            current = current.previousElementSibling();
        }
        return "jOOQ Documentation";
    }

    public static class SearchResult {
        private String title;
        private String content;
        private String url;
        private String section;

        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
    }
}