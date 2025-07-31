package ch.martinelli.jooqmcp.search;

import ch.martinelli.jooqmcp.util.TextProcessor;
import ch.martinelli.jooqmcp.service.LocalJooqDocumentationService.DocumentationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Inverted index for fast full-text search with TF-IDF scoring
 */
public class InvertedIndex {
    
    // Term -> Document ID -> Term Frequency
    private final Map<String, Map<String, Integer>> termFrequencies = new ConcurrentHashMap<>();
    
    // Document ID -> Document data
    private final Map<String, IndexedDocument> documents = new ConcurrentHashMap<>();
    
    // Document ID -> Total word count
    private final Map<String, Integer> documentWordCounts = new ConcurrentHashMap<>();
    
    // Phrase index for exact phrase matching
    private final Map<String, Set<String>> phraseIndex = new ConcurrentHashMap<>();
    
    // Total number of documents
    private int totalDocuments = 0;
    
    public static class IndexedDocument {
        private final String id;
        private final DocumentationSection section;
        private final List<String> titleTokens;
        private final List<String> contentTokens;
        private final Set<String> allTokens;
        private final List<String> phrases;
        
        public IndexedDocument(String id, DocumentationSection section) {
            this.id = id;
            this.section = section;
            this.titleTokens = TextProcessor.processText(section.getTitle());
            this.contentTokens = TextProcessor.processText(section.getContent());
            this.allTokens = new HashSet<>();
            this.allTokens.addAll(titleTokens);
            this.allTokens.addAll(contentTokens);
            this.phrases = TextProcessor.extractPhrases(section.getTitle() + " " + section.getContent());
        }
        
        public String getId() { return id; }
        public DocumentationSection getSection() { return section; }
        public List<String> getTitleTokens() { return titleTokens; }
        public List<String> getContentTokens() { return contentTokens; }
        public Set<String> getAllTokens() { return allTokens; }
        public List<String> getPhrases() { return phrases; }
    }
    
    public static class SearchMatch {
        private final IndexedDocument document;
        private final double score;
        private final Set<String> matchedTerms;
        private final Map<String, Integer> termMatches;
        
        public SearchMatch(IndexedDocument document, double score, Set<String> matchedTerms, Map<String, Integer> termMatches) {
            this.document = document;
            this.score = score;
            this.matchedTerms = matchedTerms;
            this.termMatches = termMatches;
        }
        
        public IndexedDocument getDocument() { return document; }
        public double getScore() { return score; }
        public Set<String> getMatchedTerms() { return matchedTerms; }
        public Map<String, Integer> getTermMatches() { return termMatches; }
    }
    
    /**
     * Add a document to the index
     */
    public void addDocument(DocumentationSection section) {
        String docId = section.getId() != null ? section.getId() : "doc_" + totalDocuments;
        IndexedDocument indexedDoc = new IndexedDocument(docId, section);
        
        documents.put(docId, indexedDoc);
        
        // Count all tokens for TF calculation
        List<String> allTokens = new ArrayList<>();
        allTokens.addAll(indexedDoc.getTitleTokens());
        allTokens.addAll(indexedDoc.getContentTokens());
        
        documentWordCounts.put(docId, allTokens.size());
        
        // Build term frequency map for this document
        Map<String, Integer> docTermFreq = new HashMap<>();
        for (String token : allTokens) {
            docTermFreq.merge(token, 1, Integer::sum);
        }
        
        // Update inverted index
        for (Map.Entry<String, Integer> entry : docTermFreq.entrySet()) {
            String term = entry.getKey();
            int frequency = entry.getValue();
            
            termFrequencies.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                          .put(docId, frequency);
        }
        
        // Index phrases
        for (String phrase : indexedDoc.getPhrases()) {
            phraseIndex.computeIfAbsent(phrase.toLowerCase(), k -> new HashSet<>()).add(docId);
        }
        
        totalDocuments++;
    }
    
    /**
     * Search the index with advanced scoring
     */
    public List<SearchMatch> search(String query, int maxResults) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Parse query into terms and phrases
        SearchQuery parsedQuery = parseQuery(query);
        
        // Find candidate documents
        Set<String> candidateDocIds = findCandidateDocuments(parsedQuery);
        
        if (candidateDocIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Score each candidate document
        List<SearchMatch> matches = new ArrayList<>();
        for (String docId : candidateDocIds) {
            IndexedDocument doc = documents.get(docId);
            if (doc != null) {
                double score = calculateScore(doc, parsedQuery);
                if (score > 0) {
                    Set<String> matchedTerms = findMatchedTerms(doc, parsedQuery);
                    Map<String, Integer> termMatches = calculateTermMatches(doc, parsedQuery);
                    matches.add(new SearchMatch(doc, score, matchedTerms, termMatches));
                }
            }
        }
        
        // Sort by score (descending) and return top results
        return matches.stream()
                .sorted(Comparator.comparingDouble(SearchMatch::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Parse query into structured format
     */
    private SearchQuery parseQuery(String query) {
        SearchQuery searchQuery = new SearchQuery();
        
        // Extract exact phrases (quoted strings)
        List<String> phrases = new ArrayList<>();
        String processedQuery = query;
        
        // Simple phrase extraction (could be enhanced with proper parsing)
        if (query.contains("\"")) {
            String[] parts = query.split("\"");
            for (int i = 1; i < parts.length; i += 2) {
                if (!parts[i].trim().isEmpty()) {
                    phrases.add(parts[i].trim().toLowerCase());
                    processedQuery = processedQuery.replace("\"" + parts[i] + "\"", "");
                }
            }
        }
        
        // Process remaining terms
        List<String> terms = TextProcessor.processText(processedQuery);
        
        // Expand terms with synonyms
        Set<String> expandedTerms = new HashSet<>();
        for (String term : terms) {
            expandedTerms.addAll(TextProcessor.expandWithSynonyms(term));
        }
        
        searchQuery.setTerms(new ArrayList<>(expandedTerms));
        searchQuery.setPhrases(phrases);
        searchQuery.setOriginalQuery(query);
        
        return searchQuery;
    }
    
    /**
     * Find documents that contain at least some query terms
     */
    private Set<String> findCandidateDocuments(SearchQuery query) {
        Set<String> candidates = new HashSet<>();
        
        // Find documents matching terms
        for (String term : query.getTerms()) {
            Map<String, Integer> termDocs = termFrequencies.get(term);
            if (termDocs != null) {
                candidates.addAll(termDocs.keySet());
            }
            
            // Also try fuzzy matching for typo tolerance
            for (String indexedTerm : termFrequencies.keySet()) {
                double similarity = TextProcessor.fuzzyMatchScore(term, indexedTerm);
                if (similarity > 0.8) { // 80% similarity threshold
                    Map<String, Integer> fuzzyDocs = termFrequencies.get(indexedTerm);
                    if (fuzzyDocs != null) {
                        candidates.addAll(fuzzyDocs.keySet());
                    }
                }
            }
        }
        
        // Find documents matching phrases
        for (String phrase : query.getPhrases()) {
            Set<String> phraseDocs = phraseIndex.get(phrase);
            if (phraseDocs != null) {
                if (candidates.isEmpty()) {
                    candidates.addAll(phraseDocs);
                } else {
                    candidates.retainAll(phraseDocs); // AND operation for phrases
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Calculate TF-IDF based score for a document
     */
    private double calculateScore(IndexedDocument doc, SearchQuery query) {
        double score = 0.0;
        
        // TF-IDF scoring for terms
        for (String term : query.getTerms()) {
            double termScore = calculateTermScore(doc, term);
            score += termScore;
        }
        
        // Boost for phrase matches
        for (String phrase : query.getPhrases()) {
            if (doc.getPhrases().contains(phrase)) {
                score += 50.0; // Significant boost for exact phrase matches
            }
        }
        
        // Title match boost
        String lowerTitle = doc.getSection().getTitle().toLowerCase();
        String lowerQuery = query.getOriginalQuery().toLowerCase();
        if (lowerTitle.contains(lowerQuery)) {
            score += 30.0;
        }
        
        // Section level boost (higher-level sections are more important)
        score += (7 - doc.getSection().getLevel()) * 2.0;
        
        // Content length normalization (prefer focused content)
        int contentLength = doc.getSection().getContent().length();
        if (contentLength > 0 && contentLength < 1000) {
            score += 10.0; // Boost for concise sections
        }
        
        return score;
    }
    
    /**
     * Calculate TF-IDF score for a specific term in a document
     */
    private double calculateTermScore(IndexedDocument doc, String term) {
        Map<String, Integer> termDocs = termFrequencies.get(term);
        if (termDocs == null || !termDocs.containsKey(doc.getId())) {
            return 0.0;
        }
        
        // Term Frequency
        int termFreq = termDocs.get(doc.getId());
        int docWordCount = documentWordCounts.getOrDefault(doc.getId(), 1);
        double tf = (double) termFreq / docWordCount;
        
        // Inverse Document Frequency
        int docsWithTerm = termDocs.size();
        double idf = Math.log((double) totalDocuments / docsWithTerm);
        
        // TF-IDF score
        double tfidfScore = tf * idf;
        
        // Boost if term appears in title
        if (doc.getTitleTokens().contains(term)) {
            tfidfScore *= 3.0;
        }
        
        return tfidfScore * 100.0; // Scale up for easier comparison
    }
    
    /**
     * Find which terms from the query matched in the document
     */
    private Set<String> findMatchedTerms(IndexedDocument doc, SearchQuery query) {
        Set<String> matched = new HashSet<>();
        
        for (String term : query.getTerms()) {
            if (doc.getAllTokens().contains(term)) {
                matched.add(term);
            }
        }
        
        return matched;
    }
    
    /**
     * Calculate how many times each query term appears in the document
     */
    private Map<String, Integer> calculateTermMatches(IndexedDocument doc, SearchQuery query) {
        Map<String, Integer> matches = new HashMap<>();
        
        for (String term : query.getTerms()) {
            Map<String, Integer> termDocs = termFrequencies.get(term);
            if (termDocs != null && termDocs.containsKey(doc.getId())) {
                matches.put(term, termDocs.get(doc.getId()));
            }
        }
        
        return matches;
    }
    
    /**
     * Get statistics about the index
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", totalDocuments);
        stats.put("totalTerms", termFrequencies.size());
        stats.put("totalPhrases", phraseIndex.size());
        stats.put("averageDocumentLength", documentWordCounts.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        return stats;
    }
    
    /**
     * Query representation
     */
    private static class SearchQuery {
        private List<String> terms = new ArrayList<>();
        private List<String> phrases = new ArrayList<>();
        private String originalQuery;
        
        public List<String> getTerms() { return terms; }
        public void setTerms(List<String> terms) { this.terms = terms; }
        public List<String> getPhrases() { return phrases; }
        public void setPhrases(List<String> phrases) { this.phrases = phrases; }
        public String getOriginalQuery() { return originalQuery; }
        public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
    }
}