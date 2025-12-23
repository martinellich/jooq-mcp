package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JooqDocumentationService.
 * Run locally with: ./mvnw verify -DskipITs=false
 * Requires OPENAI_API_KEY and PINECONE_API_KEY environment variables.
 */
@SpringBootTest
class JooqDocumentationServiceIT {

    @Autowired
    private JooqDocumentationService jooqDocumentationService;

    @Test
    void searchDocumentation_withValidQuery_returnsResults() {
        String response = jooqDocumentationService.searchDocumentation("SELECT statement");

        assertNotNull(response);
        assertTrue(response.contains("Found") || response.contains("No results found"));
        if (response.contains("Found")) {
            assertTrue(response.contains("results for"));
        }
    }

    @Test
    void searchDocumentation_withEmptyQuery_returnsErrorMessage() {
        String response = jooqDocumentationService.searchDocumentation("");

        assertEquals("Please provide a search query to search the jOOQ documentation.", response);
    }

    @Test
    void searchDocumentation_withNullQuery_returnsErrorMessage() {
        String response = jooqDocumentationService.searchDocumentation(null);

        assertEquals("Please provide a search query to search the jOOQ documentation.", response);
    }

    @Test
    void searchDocumentation_withNoResults_indicatesNoResults() {
        String response = jooqDocumentationService.searchDocumentation("xyzabc123nonexistent");

        assertTrue(response.contains("No results found") && response.contains("xyzabc123nonexistent"));
    }

    @Test
    void searchDocumentation_responseIsTruncated() {
        String response = jooqDocumentationService.searchDocumentation("select");

        assertNotNull(response);
        assertTrue(response.length() <= 3000, "Response should be truncated to prevent buffer overflow, but was: " + response.length());
    }

    @Test
    void getSqlExamples_withValidTopic_returnsExamples() {
        String response = jooqDocumentationService.getSqlExamples("SELECT");

        assertNotNull(response);
        assertTrue(response.contains("jOOQ Examples for SELECT") ||
                response.contains("No SQL examples found"));
    }

    @Test
    void getSqlExamples_withEmptyTopic_returnsErrorMessage() {
        String response = jooqDocumentationService.getSqlExamples("");

        assertEquals("Please specify a SQL topic (e.g., SELECT, INSERT, UPDATE, DELETE, JOIN).", response);
    }

    @Test
    void getSqlExamples_withUnknownTopic_handlesGracefully() {
        String response = jooqDocumentationService.getSqlExamples("XYZUNKNOWN");

        assertTrue(response.contains("No SQL examples found") || response.contains("jOOQ Examples"));
    }

    @Test
    void getSqlExamples_responseIsTruncated() {
        String response = jooqDocumentationService.getSqlExamples("select");

        assertNotNull(response);
        assertTrue(response.length() <= 4000, "Response should be truncated to prevent buffer overflow, but was: " + response.length());
    }

    @Test
    void getCodeGenerationGuide_returnsContent() {
        String response = jooqDocumentationService.getCodeGenerationGuide();

        assertNotNull(response);
        assertTrue(response.length() > 0);
        assertTrue(response.length() <= 3500, "Response should not exceed 3500 characters, but was: " + response.length());
    }

    @Test
    void getDatabaseSupport_withValidDatabase_returnsContent() {
        String response = jooqDocumentationService.getDatabaseSupport("MySQL");

        assertNotNull(response);
        assertTrue(response.length() > 0);
        assertTrue(response.length() <= 3500, "Response should not exceed 3500 characters, but was: " + response.length());
    }

    @Test
    void getDatabaseSupport_withEmptyDatabase_returnsErrorMessage() {
        String response = jooqDocumentationService.getDatabaseSupport("");

        assertEquals("Please specify a database name (e.g., MySQL, PostgreSQL, Oracle, SQL Server).", response);
    }

    @Test
    void getQueryDslReference_withValidQueryType_returnsContent() {
        String response = jooqDocumentationService.getQueryDslReference("INSERT");

        assertNotNull(response);
        assertTrue(response.length() > 0);
        assertTrue(response.length() <= 3500, "Response should not exceed 3500 characters, but was: " + response.length());
    }

    @Test
    void getQueryDslReference_withEmptyQueryType_returnsErrorMessage() {
        String response = jooqDocumentationService.getQueryDslReference("");

        assertEquals("Please specify a query type (e.g., SELECT, INSERT, UPDATE, DELETE, MERGE).", response);
    }

    @Test
    void getAdvancedFeatures_withValidFeature_returnsContent() {
        String response = jooqDocumentationService.getAdvancedFeatures("transactions");

        assertNotNull(response);
        assertTrue(response.length() > 0);
        assertTrue(response.length() <= 3500, "Response should not exceed 3500 characters, but was: " + response.length());
    }

    @Test
    void getAdvancedFeatures_withEmptyFeature_returnsErrorMessage() {
        String response = jooqDocumentationService.getAdvancedFeatures("");

        assertEquals("Please specify an advanced feature (e.g., transactions, stored procedures, batch operations).", response);
    }
}
