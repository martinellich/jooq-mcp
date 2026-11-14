package ch.martinelli.jooqmcp.cli;

import ch.martinelli.jooqmcp.model.DocumentChunk;
import ch.martinelli.jooqmcp.service.OpenAiEmbeddingService;
import ch.martinelli.jooqmcp.service.PineconeSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time CLI command to generate embeddings and seed Pinecone index.
 * Run with: ./mvnw spring-boot:run -Dspring-boot.run.arguments="--embedding.generate=true"
 */
@Component
public class EmbeddingGeneratorCommand implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingGeneratorCommand.class);

    private static final String[] JOOQ_KEYWORDS = {
            "jooq", "dsl", "select", "insert", "update", "delete", "merge",
            "join", "where", "group", "order", "having", "union", "subquery",
            "transaction", "batch", "stored", "procedure", "function",
            "codegen", "dialect", "mysql", "postgresql",
            "oracle", "sqlserver", "h2", "mariadb", "sqlite",
            "record", "table", "field", "condition", "result", "query",
            "fetch", "execute", "bind", "parameter", "expression"
    };

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    // Pinecone allows 40 KB of metadata per vector, the content takes about 1 KB of it
    private static final int MAX_KEYWORDS = 200;

    @Value("${embedding.generate:false}")
    private boolean generateEmbeddings;

    @Value("${embedding.chunk-size:1000}")
    private int chunkSize;

    @Value("${embedding.chunk-overlap:200}")
    private int chunkOverlap;

    @Value("${embedding.manual-url}")
    private Resource documentationFile;

    private final OpenAiEmbeddingService embeddingService;
    private final PineconeSearchService pineconeSearchService;

    public EmbeddingGeneratorCommand(
            OpenAiEmbeddingService embeddingService,
            PineconeSearchService pineconeSearchService) {
        this.embeddingService = embeddingService;
        this.pineconeSearchService = pineconeSearchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!generateEmbeddings) {
            logger.debug("Embedding generation disabled (embedding.generate=false)");
            return;
        }

        logger.info("Starting embedding generation process...");

        try {
            // Parse documentation
            List<DocumentChunk> chunks = parseDocumentation();
            logger.info("Parsed {} chunks from documentation", chunks.size());

            // Generate embeddings
            List<String> contents = chunks.stream()
                    .map(EmbeddingGeneratorCommand::embeddingText)
                    .toList();

            logger.info("Generating embeddings for {} chunks...", contents.size());
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(contents);

            // Replace the index content, so that chunks of the previous manual don't linger
            logger.info("Deleting existing chunks from Pinecone...");
            pineconeSearchService.deleteAllChunks();

            // Upsert to Pinecone
            logger.info("Upserting {} chunks to Pinecone...", chunks.size());
            pineconeSearchService.upsertChunks(chunks, embeddings);

            logger.info("Embedding generation completed successfully!");
            logger.info("Total chunks indexed: {}", chunks.size());

        } catch (Exception e) {
            logger.error("Error during embedding generation", e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }

    /**
     * The breadcrumb gives chunks that are mostly code the context of their section, for example the dialect.
     */
    static String embeddingText(DocumentChunk chunk) {
        return chunk.breadcrumb() + "\n\n" + chunk.content();
    }

    List<DocumentChunk> parseDocumentation() throws IOException {
        logger.info("Loading documentation from: {}", documentationFile.getDescription());

        List<ManualMarkdownParser.Section> sections;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(documentationFile.getInputStream(), StandardCharsets.UTF_8))) {
            sections = ManualMarkdownParser.parse(reader);
        }

        List<DocumentChunk> allChunks = new ArrayList<>();
        // Subheadings share the id of their manual section, so chunk indexes continue across them
        Map<String, Integer> nextChunkIndex = new HashMap<>();

        for (ManualMarkdownParser.Section section : sections) {
            // Detect language from content
            String language = detectLanguage(section.content());

            // Create chunks with overlap
            int firstChunkIndex = nextChunkIndex.getOrDefault(section.id(), 0);
            List<DocumentChunk> sectionChunks = createChunks(
                    section.id(), section.title(), section.breadcrumb(), section.content(),
                    language, firstChunkIndex
            );
            nextChunkIndex.put(section.id(), firstChunkIndex + sectionChunks.size());

            allChunks.addAll(sectionChunks);
        }

        return allChunks;
    }

    private String detectLanguage(String content) {
        if (content.contains("DSL.select") || content.contains("create.") || content.contains("import org.jooq")) {
            return "java";
        } else if (content.contains("SELECT") || content.contains("INSERT") || content.contains("UPDATE") || content.contains("DELETE")) {
            return "sql";
        } else if (content.contains("<") && content.contains(">") && content.contains("/")) {
            return "xml";
        }
        return "text";
    }

    /**
     * Keywords for the lexical part of the hybrid search: the jOOQ terms from a fixed list plus the API
     * identifiers in the text, so that a query for a method name like fetchInto finds the chunk that uses it.
     * Everything is lowercased, as the query side lowercases its terms too.
     */
    static List<String> extractKeywords(String title, String content) {
        String combined = title + " " + content;
        String lowerCombined = combined.toLowerCase(Locale.ROOT);

        Set<String> keywords = new LinkedHashSet<>();
        for (String keyword : JOOQ_KEYWORDS) {
            if (lowerCombined.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        Matcher matcher = IDENTIFIER.matcher(combined);
        while (matcher.find() && keywords.size() < MAX_KEYWORDS) {
            String identifier = matcher.group();
            if (identifier.length() > 2 && (isMethodCall(combined, matcher.end()) || isMixedCase(identifier))) {
                keywords.add(identifier.toLowerCase(Locale.ROOT));
            }
        }

        return List.copyOf(keywords);
    }

    private static boolean isMethodCall(String text, int identifierEnd) {
        return identifierEnd < text.length() && text.charAt(identifierEnd) == '(';
    }

    /**
     * Identifiers like fetchInto, DSLContext or RecordMapper, but not SELECT or plain words.
     */
    private static boolean isMixedCase(String identifier) {
        boolean innerUpperCase = false;
        boolean lowerCase = false;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (Character.isLowerCase(c)) {
                lowerCase = true;
            } else if (i > 0 && Character.isUpperCase(c)) {
                innerUpperCase = true;
            }
        }
        return innerUpperCase && lowerCase;
    }

    private List<DocumentChunk> createChunks(
            String sectionId,
            String title,
            String breadcrumb,
            String content,
            String language,
            int firstChunkIndex) {

        List<DocumentChunk> chunks = new ArrayList<>();

        if (content.length() <= chunkSize) {
            // Single chunk
            chunks.add(new DocumentChunk(
                    DocumentChunk.createId(sectionId, firstChunkIndex),
                    sectionId,
                    title,
                    breadcrumb,
                    content,
                    firstChunkIndex,
                    language,
                    extractKeywords(title, content)
            ));
        } else {
            // Multiple chunks with overlap
            int start = 0;
            int chunkIndex = firstChunkIndex;

            while (start < content.length()) {
                int end = Math.min(start + chunkSize, content.length());

                // Try to break at word boundary
                if (end < content.length()) {
                    int lastSpace = content.lastIndexOf(' ', end);
                    if (lastSpace > start + chunkSize / 2) {
                        end = lastSpace;
                    }
                }

                String chunkContent = content.substring(start, end).trim();

                if (!chunkContent.isEmpty()) {
                    chunks.add(new DocumentChunk(
                            DocumentChunk.createId(sectionId, chunkIndex),
                            sectionId,
                            title,
                            breadcrumb,
                            chunkContent,
                            chunkIndex,
                            language,
                            extractKeywords(title, chunkContent)
                    ));
                    chunkIndex++;
                }

                // Move to next chunk with overlap
                start = end - chunkOverlap;
                if (start >= content.length() - chunkOverlap) {
                    break;
                }
            }
        }

        return chunks;
    }
}
