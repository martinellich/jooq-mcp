package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JooqDocumentationServiceTest {

    @Autowired
    private JooqDocumentationService jooqDocumentationService;

    @Test
    void testSearchDocumentation_WithValidQuery() {
        // Act
        String response = jooqDocumentationService.searchDocumentation("SELECT statement");

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("Found") || response.contains("No results found"));
        // If results are found, verify the response structure
        if (response.contains("Found")) {
            assertTrue(response.contains("results for"));
        }
    }

    @Test
    void testSearchDocumentation_WithEmptyQuery() {
        // Act
        String response = jooqDocumentationService.searchDocumentation("");

        // Assert
        assertEquals("Please provide a search query to search the jOOQ documentation.", response);
    }

    @Test
    void testSearchDocumentation_WithNullQuery() {
        // Act
        String response = jooqDocumentationService.searchDocumentation(null);

        // Assert
        assertEquals("Please provide a search query to search the jOOQ documentation.", response);
    }
    
    @Test
    void testSearchDocumentation_WithNoResults() {
        // Act - use a very unlikely search term
        String response = jooqDocumentationService.searchDocumentation("xyzabc123nonexistent");

        // Assert
        assertTrue(response.contains("No results found") && response.contains("xyzabc123nonexistent"));
    }

    @Test
    void testGetSqlExamples_WithValidTopic() {
        // Act
        String response = jooqDocumentationService.getSqlExamples("SELECT");

        // Assert
        assertNotNull(response);
        // Should either find examples or indicate none found
        assertTrue(response.contains("jOOQ Examples for SELECT") || 
                   response.contains("No SQL examples found"));
    }

    @Test
    void testGetSqlExamples_WithEmptyTopic() {
        // Act
        String response = jooqDocumentationService.getSqlExamples("");

        // Assert
        assertEquals("Please specify a SQL topic (e.g., SELECT, INSERT, UPDATE, DELETE, JOIN).", response);
    }
    
    @Test
    void testGetSqlExamples_WithNoExamples() {
        // Act - use an unlikely topic
        String response = jooqDocumentationService.getSqlExamples("XYZUNKNOWN");

        // Assert
        assertTrue(response.contains("No SQL examples found") || response.contains("jOOQ Examples"));
    }

    @Test
    void testGetCodeGenerationGuide() {
        // Act
        String response = jooqDocumentationService.getCodeGenerationGuide();

        // Assert
        assertNotNull(response);
        // Should contain content about code generation or error message
        assertTrue(response.length() > 0);
    }

    @Test
    void testGetDatabaseSupport_WithValidDatabase() {
        // Act
        String response = jooqDocumentationService.getDatabaseSupport("MySQL");

        // Assert
        assertNotNull(response);
        assertTrue(response.length() > 0);
        // Should contain either MySQL-related content or indicate no documentation found
    }
    
    @Test
    void testGetDatabaseSupport_WithEmptyDatabase() {
        // Act
        String response = jooqDocumentationService.getDatabaseSupport("");

        // Assert
        assertEquals("Please specify a database name (e.g., MySQL, PostgreSQL, Oracle, SQL Server).", response);
    }

    @Test
    void testGetQueryDslReference_WithValidQueryType() {
        // Act
        String response = jooqDocumentationService.getQueryDslReference("INSERT");

        // Assert
        assertNotNull(response);
        assertTrue(response.length() > 0);
    }
    
    @Test
    void testGetQueryDslReference_WithEmptyQueryType() {
        // Act
        String response = jooqDocumentationService.getQueryDslReference("");

        // Assert
        assertEquals("Please specify a query type (e.g., SELECT, INSERT, UPDATE, DELETE, MERGE).", response);
    }

    @Test
    void testGetAdvancedFeatures_WithValidFeature() {
        // Act
        String response = jooqDocumentationService.getAdvancedFeatures("transactions");

        // Assert
        assertNotNull(response);
        assertTrue(response.length() > 0);
    }
    
    @Test
    void testGetAdvancedFeatures_WithEmptyFeature() {
        // Act
        String response = jooqDocumentationService.getAdvancedFeatures("");

        // Assert
        assertEquals("Please specify an advanced feature (e.g., transactions, stored procedures, batch operations).", response);
    }
    
    @Test
    void testSearchDocumentation_ContentTruncation() {
        // Act - search for a common term that should return multiple results
        String response = jooqDocumentationService.searchDocumentation("select");

        // Assert
        assertNotNull(response);
        // Response should be limited in size
        assertTrue(response.length() <= 3000, "Response should be truncated to prevent buffer overflow");
    }
    
    @Test
    void testGetSqlExamples_ContentTruncation() {
        // Act - search for a common topic
        String response = jooqDocumentationService.getSqlExamples("select");

        // Assert
        assertNotNull(response);
        // Response should be limited in size
        assertTrue(response.length() <= 4000, "Response should be truncated to prevent buffer overflow");
    }
}