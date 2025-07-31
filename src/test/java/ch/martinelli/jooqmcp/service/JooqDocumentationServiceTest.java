package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqDocumentationServiceTest {

    @Mock
    private LocalJooqDocumentationService localDocumentationService;

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
        List<LocalJooqDocumentationService.SearchResult> mockResults = new ArrayList<>();
        LocalJooqDocumentationService.SearchResult result = new LocalJooqDocumentationService.SearchResult(
            "SELECT Statement",
            "This section covers SELECT statements in jOOQ...",
            "sql-building",
            100.0
        );
        mockResults.add(result);

        when(localDocumentationService.searchDocumentation(query)).thenReturn(mockResults);

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
    void testSearchDocumentation_WithNoResults() {
        // Arrange
        String query = "nonexistent";
        when(localDocumentationService.searchDocumentation(query)).thenReturn(Collections.emptyList());

        // Act
        String response = jooqDocumentationService.searchDocumentation(query);

        // Assert
        assertEquals("No results found for 'nonexistent' in jOOQ documentation. Try different keywords.", response);
    }

    @Test
    void testGetSqlExamples_WithValidTopic() {
        // Arrange
        String topic = "SELECT";
        List<LocalJooqDocumentationService.CodeExample> mockExamples = new ArrayList<>();
        LocalJooqDocumentationService.CodeExample example = new LocalJooqDocumentationService.CodeExample(
            "DSL.select().from(TABLE).fetch();",
            "Basic SELECT example",
            "java"
        );
        mockExamples.add(example);
        
        when(localDocumentationService.getCodeExamples(topic)).thenReturn(mockExamples);

        // Act
        String response = jooqDocumentationService.getSqlExamples(topic);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("jOOQ Examples for SELECT"));
        assertTrue(response.contains("DSL.select().from(TABLE).fetch();"));
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
        // Arrange
        String topic = "UNKNOWN";
        when(localDocumentationService.getCodeExamples(topic)).thenReturn(Collections.emptyList());

        // Act
        String response = jooqDocumentationService.getSqlExamples(topic);

        // Assert
        assertEquals("No SQL examples found for 'UNKNOWN'. Try different keywords like SELECT, INSERT, UPDATE, DELETE, JOIN.", response);
    }

    @Test
    void testGetCodeGenerationGuide() {
        // Arrange
        String expectedGuide = "# Code Generation\n\nThis guide covers jOOQ code generation...";
        when(localDocumentationService.getDocumentationContent("code generation")).thenReturn(expectedGuide);

        // Act
        String response = jooqDocumentationService.getCodeGenerationGuide();

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("Code Generation"));
    }

    @Test
    void testGetDatabaseSupport_WithValidDatabase() {
        // Arrange
        String database = "MySQL";
        String expectedContent = "# MySQL Support\n\nMySQL is fully supported...";
        when(localDocumentationService.getDocumentationContent(anyString())).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getDatabaseSupport(database);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("MySQL"));
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
        // Arrange
        String queryType = "INSERT";
        String expectedContent = "# INSERT Statement\n\nINSERT statements in jOOQ...";
        when(localDocumentationService.getDocumentationContent(anyString())).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getQueryDslReference(queryType);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("INSERT"));
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
        // Arrange
        String feature = "transactions";
        String expectedContent = "# Transactions\n\nTransaction management in jOOQ...";
        when(localDocumentationService.getDocumentationContent(feature)).thenReturn(expectedContent);

        // Act
        String response = jooqDocumentationService.getAdvancedFeatures(feature);

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("Transaction"));
    }
    
    @Test
    void testGetAdvancedFeatures_WithEmptyFeature() {
        // Act
        String response = jooqDocumentationService.getAdvancedFeatures("");

        // Assert
        assertEquals("Please specify an advanced feature (e.g., transactions, stored procedures, batch operations).", response);
    }
    
    @Test
    void testSearchDocumentation_WithException() {
        // Arrange
        String query = "test";
        when(localDocumentationService.searchDocumentation(query)).thenThrow(new RuntimeException("Test error"));

        // Act
        String response = jooqDocumentationService.searchDocumentation(query);

        // Assert
        assertEquals("Error searching jOOQ documentation. Please try again later.", response);
    }
    
    @Test
    void testGetSqlExamples_WithException() {
        // Arrange
        String topic = "SELECT";
        when(localDocumentationService.getCodeExamples(topic)).thenThrow(new RuntimeException("Test error"));

        // Act
        String response = jooqDocumentationService.getSqlExamples(topic);

        // Assert
        assertEquals("Error fetching SQL examples for 'SELECT'. Please try a different topic.", response);
    }
}