package ch.martinelli.jooqmcp.cli;

import ch.martinelli.jooqmcp.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingGeneratorCommandTest {

    @Test
    void chunkIdsStayUniqueAcrossSubheadingsOfOneSection() throws IOException {
        String manual = """
                <a id="section"></a>
                ## 1. Section

                Intro

                ### First

                First text

                ### Second

                Second text
                """;

        List<String> ids = parse(manual).stream().map(DocumentChunk::id).toList();

        assertEquals(List.of("section_chunk_0", "section_chunk_1", "section_chunk_2"), ids);
    }

    @Test
    void longSectionsAreChunkedWithoutTruncation() throws IOException {
        String manual = "<a id=\"long\"></a>\n## 1. Long\n\n" + "word ".repeat(2000) + "end\n";

        List<DocumentChunk> chunks = parse(manual);

        assertTrue(chunks.size() > 10);
        assertTrue(chunks.getLast().content().endsWith("end"));
    }

    @Test
    void embeddingTextStartsWithBreadcrumb() throws IOException {
        String manual = """
                <a id="section"></a>
                ## 1. Section

                ### Oracle

                SELECT 1
                """;

        DocumentChunk chunk = parse(manual).getFirst();

        assertEquals("Section > Oracle\n\nSELECT 1", EmbeddingGeneratorCommand.embeddingText(chunk));
    }

    private static List<DocumentChunk> parse(String manual) throws IOException {
        EmbeddingGeneratorCommand command = new EmbeddingGeneratorCommand(null, null);
        ReflectionTestUtils.setField(command, "chunkSize", 1000);
        ReflectionTestUtils.setField(command, "chunkOverlap", 200);
        ReflectionTestUtils.setField(command, "documentationFile",
                new ByteArrayResource(manual.getBytes(StandardCharsets.UTF_8)));
        return command.parseDocumentation();
    }
}
