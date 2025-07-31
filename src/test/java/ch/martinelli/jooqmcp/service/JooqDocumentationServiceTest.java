package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqDocumentationServiceTest {

    @Mock
    private JooqDocumentationFetcher documentationFetcher;

    @InjectMocks
    private JooqDocumentationService jooqDocumentationService;

    @BeforeEach
    void setUp() {
        // Setup is handled by MockitoExtension
    }

    @Test
    void testSearchDocumentation_WithValidQuery() {
        // Arrange
        String query = "SELECT statement";
        List<JooqDocumentationFetcher.SearchResult> mockResults = new ArrayList<>();
        JooqDocumentationFetcher.SearchResult result = new JooqDocumentationFetcher.SearchResult();
        result.setTitle("SELECT Statement");
        result.setContent("This section covers SELECT statements in jOOQ...");
        result.setSection("sql-building");
        mockResults.add(result);

        when(documentationFetcher.searchDocumentation(query)).thenReturn(mockResults);

        // Act
        String response = jooqDocumentationService.searchDocumentation(query);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("Found 1 results"));
        assertTrue(response.contains("SELECT Statement"));
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
    void testGetSqlExamples_WithValidTopic() {
        // Arrange
        String topic = "SELECT";
        String expectedExamples = "# jOOQ Examples for SELECT\n\n## Example 1\n```java\nDSL.select().from(TABLE).fetch();\n```";
        
        when(documentationFetcher.fetchSqlExamples(topic)).thenReturn(expectedExamples);

        // Act
        String response = jooqDocumentationService.getSqlExamples(topic);

        // Assert
        assertEquals(expectedExamples, response);
    }

    @Test
    void testGetSqlExamples_WithEmptyTopic() {
        // Act
        String response = jooqDocumentationService.getSqlExamples("");

        // Assert
        assertEquals("Please specify a SQL topic (e.g., SELECT, INSERT, UPDATE, DELETE, JOIN).", response);
    }

    @Test
    void testGetCodeGenerationGuide() {
        // Arrange
        String expectedGuide = "# Code Generation Guide\n\nThis guide covers...";
        when(documentationFetcher.fetchDocumentationContent(anyString())).thenReturn(expectedGuide);

        // Act
        String response = jooqDocumentationService.getCodeGenerationGuide();

        // Assert
        assertTrue(response.contains("jOOQ Code Generation Guide"));
        assertTrue(response.contains(expectedGuide));
    }

    @Test
    void testGetDatabaseSupport_WithValidDatabase() {
        // Arrange
        String database = "MySQL";
        String expectedContent = "MySQL is fully supported...";
        when(documentationFetcher.fetchDocumentationContent(anyString())).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getDatabaseSupport(database);

        // Assert
        assertTrue(response.contains("jOOQ Support for MySQL"));
        assertTrue(response.contains(expectedContent));
    }

    @Test
    void testGetQueryDslReference_WithValidQueryType() {
        // Arrange
        String queryType = "INSERT";
        String expectedContent = "INSERT statements in jOOQ...";
        when(documentationFetcher.fetchDocumentationContent(anyString())).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getQueryDslReference(queryType);

        // Assert
        assertTrue(response.contains("jOOQ INSERT Statement Reference"));
        assertTrue(response.contains(expectedContent));
    }

    @Test
    void testGetAdvancedFeatures_WithValidFeature() {
        // Arrange
        String feature = "transactions";
        String expectedContent = "Transaction management in jOOQ...";
        when(documentationFetcher.fetchDocumentationContent(anyString())).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getAdvancedFeatures(feature);

        // Assert
        assertTrue(response.contains("jOOQ Advanced Features: transactions"));
        assertTrue(response.contains(expectedContent));
    }
}