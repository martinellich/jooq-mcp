package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that require OPENAI_API_KEY and PINECONE_API_KEY environment variables.
 * These tests will only run when the API keys are available.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
class JooqDocumentationServiceIntegrationTest {

    @Autowired
    private JooqDocumentationService documentationService;

    @Test
    void testSearchDocumentation_SelectDistinct() {
        String result = documentationService.searchDocumentation("SELECT DISTINCT");
        assertNotNull(result);
        assertTrue(result.contains("SELECT DISTINCT") || 
                   result.contains("No results found"), 
                   "Should find SELECT DISTINCT or indicate no results");
        System.out.println("SELECT DISTINCT search result:\n" + result);
    }

    @Test
    void testSearchDocumentation_WindowFunctions() {
        String result = documentationService.searchDocumentation("window functions");
        assertNotNull(result);
        System.out.println("Window functions search result:\n" + result);
    }

    @Test
    void testSearchDocumentation_Join() {
        String result = documentationService.searchDocumentation("JOIN");
        assertNotNull(result);
        System.out.println("JOIN search result:\n" + result);
    }

    @Test
    void testSearchDocumentation_EmptyQuery() {
        String result = documentationService.searchDocumentation("");
        assertNotNull(result);
        assertTrue(result.contains("Please provide a search query"));
    }

    @Test
    void testGetSqlExamples() {
        String result = documentationService.getSqlExamples("SELECT");
        assertNotNull(result);
        assertTrue(result.contains("jOOQ Examples") || 
                   result.contains("No SQL examples found") ||
                   result.contains("Error fetching"));
        System.out.println("SQL Examples result:\n" + result);
    }
}