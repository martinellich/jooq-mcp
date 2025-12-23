package ch.martinelli.jooqmcp.service;

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

    public OpenAiEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generate embedding for a single text (typically a search query).
     */
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }

        logger.debug("Generating embedding for text of length: {}", text.length());

        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] embedding = response.getResult().getOutput();

        return toFloatList(embedding);
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

            EmbeddingResponse response = embeddingModel.embedForResponse(batch);

            for (var embedding : response.getResults()) {
                allEmbeddings.add(toFloatList(embedding.getOutput()));
            }
        }

        logger.info("Generated {} embeddings", allEmbeddings.size());
        return allEmbeddings;
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
