package ch.martinelli.jooqmcp.model;

import java.util.List;

/**
 * Represents a chunk of documentation content for vector storage in Pinecone.
 */
public record DocumentChunk(
        String id,              // Unique chunk identifier
        String sectionId,       // Original section anchor
        String title,           // Section title
        String breadcrumb,      // Navigation path: "Overview > Getting Started"
        String content,         // Chunk text content
        int chunkIndex,         // Index within section (for overlap)
        String language,        // "java", "sql", "xml", "text"
        List<String> keywords   // Extracted keywords for hybrid search
) {
    /**
     * Create a chunk ID based on section ID and chunk index.
     */
    public static String createId(String sectionId, int chunkIndex) {
        String normalizedSectionId = sectionId != null ? sectionId.replaceAll("[^a-zA-Z0-9-]", "_") : "section";
        return normalizedSectionId + "_chunk_" + chunkIndex;
    }
}
