package ch.martinelli.jooqmcp.service;

import ch.martinelli.jooqmcp.model.DocumentChunk;
import ch.martinelli.jooqmcp.model.SearchResult;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for vector search operations using Pinecone.
 */
@Service
public class PineconeSearchService {

    private static final Logger logger = LoggerFactory.getLogger(PineconeSearchService.class);

    private final Index pineconeIndex;
    private final OpenAiEmbeddingService embeddingService;

    public PineconeSearchService(Index pineconeIndex, OpenAiEmbeddingService embeddingService) {
        this.pineconeIndex = pineconeIndex;
        this.embeddingService = embeddingService;
    }

    /**
     * Perform semantic search using vector similarity.
     */
    public List<SearchResult> semanticSearch(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        logger.debug("Performing semantic search for: {}", query);

        // Generate embedding for query
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);

        // Query Pinecone - using (topK, vector, includeValues, includeMetadata)
        QueryResponseWithUnsignedIndices response = pineconeIndex.queryByVector(
                limit,
                queryEmbedding,
                true,  // include values
                true   // include metadata
        );

        return convertToSearchResults(response.getMatchesList());
    }

    /**
     * Perform keyword-based search using metadata filtering.
     */
    public List<SearchResult> keywordSearch(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        logger.debug("Performing keyword search for: {}", query);

        // Extract keywords from query
        List<String> keywords = extractKeywords(query);

        if (keywords.isEmpty()) {
            return List.of();
        }

        // Generate embedding for query (still needed for vector search)
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);

        // Build filter for keyword matching in metadata
        // Pinecone filter: {"keywords": {"$in": [keyword1, keyword2, ...]}}
        Struct filter = buildKeywordFilter(keywords);

        // Query Pinecone with filter - using (topK, vector, namespace, filter, includeValues, includeMetadata)
        QueryResponseWithUnsignedIndices response = pineconeIndex.queryByVector(
                limit,
                queryEmbedding,
                "",    // default namespace
                filter,
                true,  // include values
                true   // include metadata
        );

        return convertToSearchResults(response.getMatchesList());
    }

    /**
     * Upsert document chunks to Pinecone.
     */
    public void upsertChunks(List<DocumentChunk> chunks, List<List<Float>> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Chunks and embeddings must have same size");
        }

        logger.info("Upserting {} chunks to Pinecone", chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            List<Float> embedding = embeddings.get(i);

            Map<String, Value> metadataMap = new HashMap<>();
            metadataMap.put("sectionId", Value.newBuilder().setStringValue(chunk.sectionId() != null ? chunk.sectionId() : "").build());
            metadataMap.put("title", Value.newBuilder().setStringValue(chunk.title() != null ? chunk.title() : "").build());
            metadataMap.put("breadcrumb", Value.newBuilder().setStringValue(chunk.breadcrumb() != null ? chunk.breadcrumb() : "").build());
            metadataMap.put("content", Value.newBuilder().setStringValue(chunk.content() != null ? chunk.content() : "").build());
            metadataMap.put("chunkIndex", Value.newBuilder().setNumberValue(chunk.chunkIndex()).build());
            metadataMap.put("language", Value.newBuilder().setStringValue(chunk.language() != null ? chunk.language() : "text").build());

            // Store keywords as a list
            if (chunk.keywords() != null && !chunk.keywords().isEmpty()) {
                Value.Builder listBuilder = Value.newBuilder();
                var listValue = com.google.protobuf.ListValue.newBuilder();
                for (String keyword : chunk.keywords()) {
                    listValue.addValues(Value.newBuilder().setStringValue(keyword).build());
                }
                metadataMap.put("keywords", Value.newBuilder().setListValue(listValue.build()).build());
            }

            Struct metadata = Struct.newBuilder().putAllFields(metadataMap).build();

            pineconeIndex.upsert(chunk.id(), embedding, null, null, metadata, null);

            if ((i + 1) % 100 == 0) {
                logger.info("Upserted {}/{} chunks", i + 1, chunks.size());
            }
        }

        logger.info("Completed upserting {} chunks", chunks.size());
    }

    private List<SearchResult> convertToSearchResults(List<ScoredVectorWithUnsignedIndices> matches) {
        List<SearchResult> results = new ArrayList<>();

        for (ScoredVectorWithUnsignedIndices match : matches) {
            Struct metadata = match.getMetadata();
            if (metadata == null) {
                continue;
            }

            String id = match.getId();
            String title = getStringValue(metadata, "title", "Untitled");
            String content = getStringValue(metadata, "content", "");
            String breadcrumb = getStringValue(metadata, "breadcrumb", "");
            String language = getStringValue(metadata, "language", "text");
            double score = match.getScore();

            results.add(new SearchResult(id, title, content, breadcrumb, score, language));
        }

        return results;
    }

    private String getStringValue(Struct metadata, String key, String defaultValue) {
        Value value = metadata.getFieldsOrDefault(key, null);
        if (value != null && value.hasStringValue()) {
            return value.getStringValue();
        }
        return defaultValue;
    }

    private List<String> extractKeywords(String query) {
        // Simple keyword extraction: split by whitespace and filter short words
        return List.of(query.toLowerCase().split("\\s+"))
                .stream()
                .filter(word -> word.length() > 2)
                .filter(word -> !isStopWord(word))
                .distinct()
                .toList();
    }

    private boolean isStopWord(String word) {
        return List.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "shall", "can", "this", "that", "these", "those", "what", "which", "who", "whom", "whose", "where", "when", "why", "how").contains(word);
    }

    private Struct buildKeywordFilter(List<String> keywords) {
        // Build filter: {"keywords": {"$in": [keyword1, keyword2, ...]}}
        var listBuilder = com.google.protobuf.ListValue.newBuilder();
        for (String keyword : keywords) {
            listBuilder.addValues(Value.newBuilder().setStringValue(keyword).build());
        }

        Struct inFilter = Struct.newBuilder()
                .putFields("$in", Value.newBuilder().setListValue(listBuilder.build()).build())
                .build();

        return Struct.newBuilder()
                .putFields("keywords", Value.newBuilder().setStructValue(inFilter).build())
                .build();
    }
}
