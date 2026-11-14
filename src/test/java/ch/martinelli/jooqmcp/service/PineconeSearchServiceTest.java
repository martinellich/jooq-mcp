package ch.martinelli.jooqmcp.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PineconeSearchServiceTest {

    @Test
    void queryKeywordsMatchIndexedIdentifiers() {
        assertEquals(List.of("fetchinto"), PineconeSearchService.extractKeywords("fetchInto()"));
        assertEquals(List.of("dsl", "using"), PineconeSearchService.extractKeywords("DSL.using"));
        assertEquals(List.of("map", "fetchinto", "pojo"), PineconeSearchService.extractKeywords("how to map with fetchInto to a POJO?"));
    }
}
