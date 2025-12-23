package ch.martinelli.jooqmcp.config;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Pinecone vector database client.
 */
@Configuration
public class PineconeConfig {

    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.index-name}")
    private String indexName;

    @Bean
    public Pinecone pineconeClient() {
        return new Pinecone.Builder(apiKey).build();
    }

    @Bean
    public Index pineconeIndex(Pinecone pineconeClient) {
        return pineconeClient.getIndexConnection(indexName);
    }
}
