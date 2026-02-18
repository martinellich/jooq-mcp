package ch.martinelli.jooqmcp.service;

import ch.martinelli.jooqmcp.health.OpenAiHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating embeddings using OpenAI's text-embedding-3-small model.
 */
@Service
public class OpenAiEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingService.class);
    private static final int BATCH_SIZE = 100;

    private final EmbeddingModel embeddingModel;
    private final OpenAiHealthIndicator healthIndicator;

    public OpenAiEmbeddingService(EmbeddingModel embeddingModel, OpenAiHealthIndicator healthIndicator) {
        this.embeddingModel = embeddingModel;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Generate embedding for a single text (typically a search query).
     */
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }

        logger.debug("Generating embedding for text of length: {}", text.length());

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            float[] embedding = response.getResult().getOutput();
            healthIndicator.reportHealthy();
            return toFloatList(embedding);
        } catch (Exception e) {
            if (isQuotaExceeded(e)) {
                logger.error("OpenAI quota exceeded: {}", e.getMessage());
                healthIndicator.reportQuotaExceeded(e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Generate embeddings for multiple texts in batches (for seeding).
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        logger.info("Generating embeddings for {} texts in batches of {}", texts.size(), BATCH_SIZE);

        List<List<Float>> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);

            logger.debug("Processing batch {}-{} of {}", i, end, texts.size());

            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(batch);

                for (var embedding : response.getResults()) {
                    allEmbeddings.add(toFloatList(embedding.getOutput()));
                }
                healthIndicator.reportHealthy();
            } catch (Exception e) {
                if (isQuotaExceeded(e)) {
                    logger.error("OpenAI quota exceeded: {}", e.getMessage());
                    healthIndicator.reportQuotaExceeded(e.getMessage());
                }
                throw e;
            }
        }

        logger.info("Generated {} embeddings", allEmbeddings.size());
        return allEmbeddings;
    }

    private boolean isQuotaExceeded(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("exceeded your current quota")) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.contains("exceeded your current quota")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
