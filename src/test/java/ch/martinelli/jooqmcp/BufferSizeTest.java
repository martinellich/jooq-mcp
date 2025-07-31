package ch.martinelli.jooqmcp;

import ch.martinelli.jooqmcp.service.JooqDocumentationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BufferSizeTest {

    @Autowired
    private JooqDocumentationService jooqDocumentationService;

    @Test
    public void testSearchDocumentationResponseSize() {
        String result = jooqDocumentationService.searchDocumentation("select");
        assertNotNull(result);
        assertTrue(result.length() <= 3000, "Response size should not exceed 3000 characters, but was: " + result.length());
    }

    @Test
    public void testGetSqlExamplesResponseSize() {
        String result = jooqDocumentationService.getSqlExamples("SELECT");
        assertNotNull(result);
        assertTrue(result.length() <= 3000, "Response size should not exceed 3000 characters, but was: " + result.length());
    }

    @Test
    public void testGetCodeGenerationGuideResponseSize() {
        String result = jooqDocumentationService.getCodeGenerationGuide();
        assertNotNull(result);
        assertTrue(result.length() <= 3500, "Response size should not exceed 3500 characters, but was: " + result.length());
    }

    @Test
    public void testGetDatabaseSupportResponseSize() {
        String result = jooqDocumentationService.getDatabaseSupport("MySQL");
        assertNotNull(result);
        assertTrue(result.length() <= 3500, "Response size should not exceed 3500 characters, but was: " + result.length());
    }

    @Test
    public void testGetQueryDslReferenceResponseSize() {
        String result = jooqDocumentationService.getQueryDslReference("SELECT");
        assertNotNull(result);
        assertTrue(result.length() <= 3500, "Response size should not exceed 3500 characters, but was: " + result.length());
    }

    @Test
    public void testGetAdvancedFeaturesResponseSize() {
        String result = jooqDocumentationService.getAdvancedFeatures("transactions");
        assertNotNull(result);
        assertTrue(result.length() <= 3500, "Response size should not exceed 3500 characters, but was: " + result.length());
    }
}