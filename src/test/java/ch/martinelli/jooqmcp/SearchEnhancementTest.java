package ch.martinelli.jooqmcp;

import ch.martinelli.jooqmcp.service.LocalJooqDocumentationService;
import ch.martinelli.jooqmcp.util.TextProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SearchEnhancementTest {

    @Autowired
    private LocalJooqDocumentationService localJooqDocumentationService;

    @Test
    public void testTextProcessorTokenization() {
        List<String> tokens = TextProcessor.tokenize("selectStatement from MyTable");
        assertTrue(tokens.contains("select"));
        assertTrue(tokens.contains("statement"));
        assertTrue(tokens.contains("from"));
        assertTrue(tokens.contains("my"));
        assertTrue(tokens.contains("table"));
    }

    @Test
    public void testTextProcessorStemming() {
        assertEquals("select", TextProcessor.stem("selecting"));
        assertEquals("query", TextProcessor.stem("queries")); // queries -> query (ies -> y rule)
        assertEquals("connec", TextProcessor.stem("connection")); // connection -> connec (removes -tion)
    }

    @Test
    public void testSynonymExpansion() {
        Set<String> synonyms = TextProcessor.expandWithSynonyms("select");
        assertTrue(synonyms.contains("query"));
        assertTrue(synonyms.contains("find"));
        assertTrue(synonyms.contains("retrieve"));
    }

    @Test
    public void testFuzzyMatching() {
        double score = TextProcessor.fuzzyMatchScore("select", "selct");
        assertTrue(score > 0.8); // Should have high similarity despite typo
        
        double exactScore = TextProcessor.fuzzyMatchScore("select", "select");
        assertEquals(1.0, exactScore);
    }

    @Test
    public void testEnhancedSearch() {
        List<LocalJooqDocumentationService.SearchResult> results = 
            localJooqDocumentationService.searchDocumentation("select query");
        
        assertFalse(results.isEmpty());
        
        // Check that results have relevance scores
        for (LocalJooqDocumentationService.SearchResult result : results) {
            assertTrue(result.getRelevanceScore() > 0);
            assertNotNull(result.getTitle());
            assertNotNull(result.getContent());
        }
    }

    @Test
    public void testPhrasesExtraction() {
        List<String> phrases = TextProcessor.extractPhrases("select statement from table");
        assertTrue(phrases.contains("select statement"));
        assertTrue(phrases.contains("statement from"));
        assertTrue(phrases.contains("from table"));
    }

    @Test
    public void testTermHighlighting() {
        String highlighted = TextProcessor.highlightTerms("This is a select statement", Set.of("select"));
        assertTrue(highlighted.contains("**select**"));
    }

    @Test
    public void testSearchWithTypos() {
        // Test that search can handle typos
        List<LocalJooqDocumentationService.SearchResult> results = 
            localJooqDocumentationService.searchDocumentation("slect qurey");
        
        // Should still find results despite typos
        assertFalse(results.isEmpty());
    }

    @Test
    public void testMultiTermSearch() {
        List<LocalJooqDocumentationService.SearchResult> results = 
            localJooqDocumentationService.searchDocumentation("database connection");
        
        assertFalse(results.isEmpty());
        
        // Results should be scored and sorted by relevance
        if (results.size() > 1) {
            assertTrue(results.get(0).getRelevanceScore() >= results.get(1).getRelevanceScore());
        }
    }
}