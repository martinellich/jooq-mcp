package ch.martinelli.jooqmcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JooqDocumentationService {
    
    private static final Logger logger = LoggerFactory.getLogger(JooqDocumentationService.class);
    private final LocalJooqDocumentationService localDocumentationService;

    public JooqDocumentationService(LocalJooqDocumentationService localDocumentationService) {
        this.localDocumentationService = localDocumentationService;
    }

    @Tool(description = "Search jOOQ documentation for specific topics, features, or SQL operations. Returns relevant documentation sections.")
    public String searchDocumentation(String query) {
        logger.info("Searching jOOQ documentation for: {}", query);
        
        if (query == null || query.trim().isEmpty()) {
            return "Please provide a search query to search the jOOQ documentation.";
        }
        
        try {
            List<LocalJooqDocumentationService.SearchResult> results = localDocumentationService.searchDocumentation(query);
            
            if (results.isEmpty()) {
                return String.format("No results found for '%s' in jOOQ documentation. Try different keywords.", query);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("Found %d results for '%s':\n\n", results.size(), query));
            
            for (int i = 0; i < Math.min(3, results.size()); i++) {
                LocalJooqDocumentationService.SearchResult result = results.get(i);
                response.append(String.format("%d. **%s**\n", i + 1, result.getTitle()));
                
                // Limit content size to prevent buffer overflow
                String content = result.getContent();
                if (content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                response.append(String.format("   %s\n", content));
                response.append(String.format("   Section: %s\n\n", result.getSection()));
                
                // Prevent response from getting too large
                if (response.length() > 2000) {
                    response.append("[Additional results truncated - try more specific search terms]\n");
                    break;
                }
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
            List<LocalJooqDocumentationService.CodeExample> examples = localDocumentationService.getCodeExamples(topic);
            
            if (examples.isEmpty()) {
                return String.format("No SQL examples found for '%s'. Try different keywords like SELECT, INSERT, UPDATE, DELETE, JOIN.", topic);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("# jOOQ Examples for %s\n\n", topic));
            
            // Limit to fewer examples to prevent buffer overflow
            int maxExamples = Math.min(3, examples.size());
            for (int i = 0; i < maxExamples; i++) {
                LocalJooqDocumentationService.CodeExample example = examples.get(i);
                response.append(String.format("## Example %d\n", i + 1));
                
                if (!example.getContext().isEmpty() && example.getContext().length() <= 200) {
                    response.append(example.getContext()).append("\n\n");
                }
                
                // Limit code size to prevent buffer overflow
                String code = example.getCode();
                if (code.length() > 500) {
                    code = code.substring(0, 500) + "\n// ... [Code truncated]";
                }
                
                response.append("```").append(example.getLanguage()).append("\n");
                response.append(code);
                response.append("\n```\n\n");
                
                // Prevent response from getting too large
                if (response.length() > 2500) {
                    response.append("[Additional examples truncated to prevent buffer overflow]\n");
                    break;
                }
            }
            
            if (examples.size() > maxExamples) {
                response.append(String.format("[%d additional examples available - use more specific search terms]\n", 
                    examples.size() - maxExamples));
            }
            
            return response.toString();
        } catch (Exception e) {
            logger.error("Error fetching SQL examples", e);
            return String.format("Error fetching SQL examples for '%s'. Please try a different topic.", topic);
        }
    }

    @Tool(description = "Get jOOQ code generation guide and configuration examples")
    public String getCodeGenerationGuide() {
        logger.info("Fetching jOOQ code generation guide");
        
        try {
            String content = localDocumentationService.getDocumentationContent("code generation");
            // Ensure content doesn't exceed safe limits
            if (content.length() > 3000) {
                content = """
                        %s
                        
                        [Content truncated for size limits]""".formatted(content.substring(0, 3000));
            }
            return content;
        } catch (Exception e) {
            logger.error("Error fetching code generation guide", e);
            return "Error fetching code generation guide. Please try a different search term.";
        }
    }

    @Tool(description = "Get database-specific support information and SQL dialect details for a specific database (e.g., MySQL, PostgreSQL, Oracle, SQL Server)")
    public String getDatabaseSupport(String database) {
        logger.info("Getting database support information for: {}", database);
        
        if (database == null || database.trim().isEmpty()) {
            return "Please specify a database name (e.g., MySQL, PostgreSQL, Oracle, SQL Server).";
        }
        
        try {
            String searchTerm = database + " dialect";
            String content = localDocumentationService.getDocumentationContent(searchTerm);
            
            if (content.startsWith("No documentation found")) {
                // Try alternative search terms
                content = localDocumentationService.getDocumentationContent(database);
            }
            
            // Ensure content doesn't exceed safe limits
            if (content.length() > 3000) {
                content = """
                        %s
                        
                        [Content truncated for size limits]""".formatted(content.substring(0, 3000));
            }
            
            return content;
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
            String searchTerm = queryType + " statement";
            String content = localDocumentationService.getDocumentationContent(searchTerm);
            
            if (content.startsWith("No documentation found")) {
                // Try alternative search terms
                content = localDocumentationService.getDocumentationContent(queryType);
            }
            
            // Ensure content doesn't exceed safe limits
            if (content.length() > 3000) {
                content = content.substring(0, 3000) + "\n\n[Content truncated for size limits]";
            }
            
            return content;
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
            String content = localDocumentationService.getDocumentationContent(feature);
            
            // Ensure content doesn't exceed safe limits
            if (content.length() > 3000) {
                content = content.substring(0, 3000) + "\n\n[Content truncated for size limits]";
            }
            
            return content;
        } catch (Exception e) {
            logger.error("Error fetching advanced features documentation", e);
            return String.format("Error fetching documentation for '%s'. Common advanced features include: transactions, stored procedures, batch operations, streaming, reactive execution.", feature);
        }
    }
}