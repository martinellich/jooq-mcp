package ch.martinelli.jooqmcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JooqDocumentationService {
    
    private static final Logger logger = LoggerFactory.getLogger(JooqDocumentationService.class);
    private static final String JOOQ_BASE_URL = "https://www.jooq.org/doc/3.21/manual/";
    
    private final JooqDocumentationFetcher documentationFetcher;

    public JooqDocumentationService(JooqDocumentationFetcher documentationFetcher) {
        this.documentationFetcher = documentationFetcher;
    }

    @Tool(description = "Search jOOQ documentation for specific topics, features, or SQL operations. Returns relevant documentation sections.")
    public String searchDocumentation(String query) {
        logger.info("Searching jOOQ documentation for: {}", query);
        
        if (query == null || query.trim().isEmpty()) {
            return "Please provide a search query to search the jOOQ documentation.";
        }
        
        try {
            List<JooqDocumentationFetcher.SearchResult> results = documentationFetcher.searchDocumentation(query);
            
            if (results.isEmpty()) {
                return String.format("No results found for '%s' in jOOQ documentation. Try different keywords or check the manual at %s", query, JOOQ_BASE_URL);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("Found %d results for '%s':\n\n", results.size(), query));
            
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                JooqDocumentationFetcher.SearchResult result = results.get(i);
                response.append(String.format("%d. **%s**\n", i + 1, result.getTitle()));
                response.append(String.format("   %s\n", result.getContent().length() > 200 ? 
                    result.getContent().substring(0, 200) + "..." : result.getContent()));
                response.append(String.format("   Section: %s\n\n", result.getSection()));
            }
            
            return response.toString();
        } catch (Exception e) {
            logger.error("Error searching documentation", e);
            return "Error searching jOOQ documentation. Please try again later.";
        }
    }

    @Tool(description = "Get SQL query building examples for a specific topic (e.g., SELECT, INSERT, UPDATE, DELETE, JOIN, subqueries)")
    public String getSqlExamples(String topic) {
        logger.info("Getting SQL examples for topic: {}", topic);
        
        if (topic == null || topic.trim().isEmpty()) {
            return "Please specify a SQL topic (e.g., SELECT, INSERT, UPDATE, DELETE, JOIN).";
        }
        
        try {
            return documentationFetcher.fetchSqlExamples(topic);
        } catch (Exception e) {
            logger.error("Error fetching SQL examples", e);
            return String.format("Error fetching SQL examples for '%s'. Please try a different topic or check the manual at %s", topic, JOOQ_BASE_URL);
        }
    }

    @Tool(description = "Get jOOQ code generation guide and configuration examples")
    public String getCodeGenerationGuide() {
        logger.info("Fetching jOOQ code generation guide");
        
        try {
            String url = JOOQ_BASE_URL + "code-generation/";
            String content = documentationFetcher.fetchDocumentationContent(url);
            
            return "# jOOQ Code Generation Guide\n\n" + content;
        } catch (Exception e) {
            logger.error("Error fetching code generation guide", e);
            return "Error fetching code generation guide. For manual access, visit: " + JOOQ_BASE_URL + "code-generation/";
        }
    }

    @Tool(description = "Get database-specific support information and SQL dialect details for a specific database (e.g., MySQL, PostgreSQL, Oracle, SQL Server)")
    public String getDatabaseSupport(String database) {
        logger.info("Getting database support information for: {}", database);
        
        if (database == null || database.trim().isEmpty()) {
            return "Please specify a database name (e.g., MySQL, PostgreSQL, Oracle, SQL Server).";
        }
        
        try {
            String normalizedDb = database.toLowerCase().replace(" ", "-");
            String url = JOOQ_BASE_URL + "sql-dialects/" + normalizedDb + "/";
            String content = documentationFetcher.fetchDocumentationContent(url);
            
            return String.format("# jOOQ Support for %s\n\n%s", database, content);
        } catch (Exception e) {
            logger.error("Error fetching database support info", e);
            return String.format("Error fetching support information for '%s'. Supported databases include MySQL, PostgreSQL, Oracle, SQL Server, H2, and many others.", database);
        }
    }

    @Tool(description = "Get jOOQ Query DSL reference for specific query types (e.g., SELECT, INSERT, UPDATE, DELETE, MERGE)")
    public String getQueryDslReference(String queryType) {
        logger.info("Getting Query DSL reference for: {}", queryType);
        
        if (queryType == null || queryType.trim().isEmpty()) {
            return "Please specify a query type (e.g., SELECT, INSERT, UPDATE, DELETE, MERGE).";
        }
        
        try {
            String normalizedType = queryType.toLowerCase().replace(" ", "-");
            String url = JOOQ_BASE_URL + "sql-building/" + normalizedType + "-statement/";
            String content = documentationFetcher.fetchDocumentationContent(url);
            
            return String.format("# jOOQ %s Statement Reference\n\n%s", queryType.toUpperCase(), content);
        } catch (Exception e) {
            logger.error("Error fetching Query DSL reference", e);
            return String.format("Error fetching DSL reference for '%s'. Available query types: SELECT, INSERT, UPDATE, DELETE, MERGE.", queryType);
        }
    }

    @Tool(description = "Get information about jOOQ's advanced features like transactions, stored procedures, or batch operations")
    public String getAdvancedFeatures(String feature) {
        logger.info("Getting advanced feature documentation for: {}", feature);
        
        if (feature == null || feature.trim().isEmpty()) {
            return "Please specify an advanced feature (e.g., transactions, stored procedures, batch operations).";
        }
        
        try {
            String normalizedFeature = feature.toLowerCase().replace(" ", "-");
            String section = "sql-execution"; // Most advanced features are in this section
            
            // Map common features to their documentation paths
            if (normalizedFeature.contains("transaction")) {
                section = "sql-execution/transaction-management";
            } else if (normalizedFeature.contains("stored") || normalizedFeature.contains("procedure")) {
                section = "sql-execution/stored-procedures";
            } else if (normalizedFeature.contains("batch")) {
                section = "sql-execution/batch-execution";
            }
            
            String url = JOOQ_BASE_URL + section + "/";
            String content = documentationFetcher.fetchDocumentationContent(url);
            
            return String.format("# jOOQ Advanced Features: %s\n\n%s", feature, content);
        } catch (Exception e) {
            logger.error("Error fetching advanced features documentation", e);
            return String.format("Error fetching documentation for '%s'. Common advanced features include: transactions, stored procedures, batch operations, streaming, reactive execution.", feature);
        }
    }
}