package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "jooq.documentation.crawler.max-depth=4",
    "jooq.documentation.crawler.max-urls-per-section=10"
})
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
                   result.contains("Unable to fetch"));
        System.out.println("SQL Examples result:\n" + result);
    }
}