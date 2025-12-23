package ch.martinelli.jooqmcp.cli;

import ch.martinelli.jooqmcp.model.DocumentChunk;
import ch.martinelli.jooqmcp.service.OpenAiEmbeddingService;
import ch.martinelli.jooqmcp.service.PineconeSearchService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time CLI command to generate embeddings and seed Pinecone index.
 * Run with: ./mvnw spring-boot:run -Dspring-boot.run.arguments="--embedding.generate=true"
 */
@Component
public class EmbeddingGeneratorCommand implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingGeneratorCommand.class);

    @Value("${embedding.generate:false}")
    private boolean generateEmbeddings;

    @Value("${embedding.chunk-size:1000}")
    private int chunkSize;

    @Value("${embedding.chunk-overlap:200}")
    private int chunkOverlap;

    @Value("classpath:docs/manual-single-page.html")
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
                    .map(DocumentChunk::content)
                    .toList();

            logger.info("Generating embeddings for {} chunks...", contents.size());
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(contents);

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

    private List<DocumentChunk> parseDocumentation() throws IOException {
        logger.info("Loading documentation from: {}", documentationFile.getFilename());

        Document fullDocument = Jsoup.parse(documentationFile.getInputStream(), "UTF-8", "");
        Elements headers = fullDocument.select("h1, h2, h3, h4, h5, h6");

        List<DocumentChunk> allChunks = new ArrayList<>();
        List<String> breadcrumbStack = new ArrayList<>();

        for (Element header : headers) {
            int level = Integer.parseInt(header.tagName().substring(1));
            String title = header.text().trim();
            String id = header.id();

            if (title.isEmpty()) continue;

            // Update breadcrumb stack
            while (breadcrumbStack.size() >= level) {
                breadcrumbStack.removeLast();
            }
            breadcrumbStack.add(title);

            String breadcrumb = String.join(" > ", breadcrumbStack);
            String sectionContent = extractSectionContent(header);

            if (sectionContent.isEmpty()) continue;

            // Detect language from content
            String language = detectLanguage(sectionContent);

            // Extract keywords
            List<String> keywords = extractKeywords(title, sectionContent);

            // Create chunks with overlap
            List<DocumentChunk> sectionChunks = createChunks(
                    id, title, breadcrumb, sectionContent, language, keywords
            );

            allChunks.addAll(sectionChunks);
        }

        return allChunks;
    }

    private String extractSectionContent(Element header) {
        StringBuilder content = new StringBuilder();
        Element nextElement = header.nextElementSibling();
        int maxLength = 5000; // Max content per section

        while (nextElement != null && !nextElement.tagName().matches("h[1-6]") && content.length() < maxLength) {
            String text = nextElement.text().trim();
            if (!text.isEmpty()) {
                content.append(text).append("\n\n");
            }
            nextElement = nextElement.nextElementSibling();
        }

        return content.toString().trim();
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

    private List<String> extractKeywords(String title, String content) {
        List<String> keywords = new ArrayList<>();
        String combined = (title + " " + content).toLowerCase();

        // Extract jOOQ-specific keywords
        String[] jooqKeywords = {
                "jooq", "dsl", "select", "insert", "update", "delete", "merge",
                "join", "where", "group", "order", "having", "union", "subquery",
                "transaction", "batch", "stored", "procedure", "function",
                "code generation", "codegen", "dialect", "mysql", "postgresql",
                "oracle", "sqlserver", "h2", "mariadb", "sqlite",
                "record", "table", "field", "condition", "result", "query",
                "fetch", "execute", "bind", "parameter", "expression"
        };

        for (String keyword : jooqKeywords) {
            if (combined.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        // Limit to top keywords
        return keywords.stream().distinct().limit(10).toList();
    }

    private List<DocumentChunk> createChunks(
            String sectionId,
            String title,
            String breadcrumb,
            String content,
            String language,
            List<String> keywords) {

        List<DocumentChunk> chunks = new ArrayList<>();

        if (content.length() <= chunkSize) {
            // Single chunk
            chunks.add(new DocumentChunk(
                    DocumentChunk.createId(sectionId, 0),
                    sectionId,
                    title,
                    breadcrumb,
                    content,
                    0,
                    language,
                    keywords
            ));
        } else {
            // Multiple chunks with overlap
            int start = 0;
            int chunkIndex = 0;

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
                            keywords
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
