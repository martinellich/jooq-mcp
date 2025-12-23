package ch.martinelli.jooqmcp.service;

import ch.martinelli.jooqmcp.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid search service combining semantic and keyword search using Reciprocal Rank Fusion.
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int RRF_CONSTANT = 60; // Standard RRF constant

    private final PineconeSearchService pineconeSearchService;

    @Value("${search.semantic-weight:0.7}")
    private double semanticWeight;

    @Value("${search.keyword-weight:0.3}")
    private double keywordWeight;

    @Value("${search.default-limit:5}")
    private int defaultLimit;

    public HybridSearchService(PineconeSearchService pineconeSearchService) {
        this.pineconeSearchService = pineconeSearchService;
    }

    /**
     * Perform hybrid search combining semantic and keyword results.
     */
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int effectiveLimit = limit > 0 ? limit : defaultLimit;

        logger.debug("Performing hybrid search for: {} (limit: {})", query, effectiveLimit);

        // Get more results initially for better fusion
        int fetchLimit = effectiveLimit * 3;

        List<SearchResult> semanticResults = pineconeSearchService.semanticSearch(query, fetchLimit);
        List<SearchResult> keywordResults = pineconeSearchService.keywordSearch(query, fetchLimit);

        logger.debug("Semantic results: {}, Keyword results: {}", semanticResults.size(), keywordResults.size());

        return mergeWithRRF(semanticResults, keywordResults, effectiveLimit);
    }

    /**
     * Perform hybrid search with default limit.
     */
    public List<SearchResult> search(String query) {
        return search(query, defaultLimit);
    }

    /**
     * Merge results using Reciprocal Rank Fusion algorithm.
     * RRF score = sum(weight / (k + rank)) for each result list
     */
    private List<SearchResult> mergeWithRRF(
            List<SearchResult> semantic,
            List<SearchResult> keyword,
            int limit) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, SearchResult> resultsById = new HashMap<>();

        // Score semantic results
        for (int i = 0; i < semantic.size(); i++) {
            SearchResult result = semantic.get(i);
            double rrfScore = semanticWeight / (RRF_CONSTANT + i);
            scores.merge(result.id(), rrfScore, Double::sum);
            resultsById.put(result.id(), result);
        }

        // Score keyword results
        for (int i = 0; i < keyword.size(); i++) {
            SearchResult result = keyword.get(i);
            double rrfScore = keywordWeight / (RRF_CONSTANT + i);
            scores.merge(result.id(), rrfScore, Double::sum);
            // Only add if not already present from semantic
            resultsById.putIfAbsent(result.id(), result);
        }

        // Sort by combined score and return top results
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    SearchResult original = resultsById.get(entry.getKey());
                    // Create new SearchResult with combined RRF score
                    return new SearchResult(
                            original.id(),
                            original.title(),
                            original.content(),
                            original.breadcrumb(),
                            entry.getValue(), // Use RRF score
                            original.language()
                    );
                })
                .toList();
    }
}
