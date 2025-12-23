package ch.martinelli.jooqmcp.model;

/**
 * Unified search result from hybrid search (semantic + keyword).
 */
public record SearchResult(
        String id,              // Document/chunk ID
        String title,           // Section title
        String content,         // Content snippet
        String breadcrumb,      // Navigation path
        double score,           // Combined relevance score
        String language         // Content language type
) {
    /**
     * Create a search result with default language.
     */
    public SearchResult(String id, String title, String content, String breadcrumb, double score) {
        this(id, title, content, breadcrumb, score, "text");
    }
}
