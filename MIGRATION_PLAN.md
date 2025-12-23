# jOOQ MCP Server: Migration to Pinecone + OpenAI Vector Search

## Problem

Current in-memory TF-IDF search causes daily restarts due to memory pressure on 2GB Fly.io. Search quality is
keyword-based only.

## Solution

Migrate to Pinecone vector database with OpenAI embeddings (similar
to [Vaadin MCP](https://github.com/vaadin/vaadin-mcp)), keeping Java/Spring Boot stack.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      MCP Server (Stateless)                      │
│  JooqDocumentationService (@Tool methods)                        │
│           ↓                                                      │
│  HybridSearchService (semantic + keyword fusion)                 │
│           ↓                                                      │
│  PineconeSearchService ←→ OpenAiEmbeddingService                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    External Services                             │
│  Pinecone (vectors)            OpenAI (embeddings)               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Files to Create

| File                                  | Purpose                                                |
|---------------------------------------|--------------------------------------------------------|
| `config/PineconeConfig.java`          | Pinecone client bean configuration                     |
| `config/OpenAiConfig.java`            | OpenAI embedding model configuration                   |
| `service/OpenAiEmbeddingService.java` | Generate embeddings for queries                        |
| `service/PineconeSearchService.java`  | Vector search operations                               |
| `service/HybridSearchService.java`    | Combine semantic + keyword with Reciprocal Rank Fusion |
| `model/DocumentChunk.java`            | Chunk data model                                       |
| `model/SearchResult.java`             | Unified search result                                  |
| `cli/EmbeddingGeneratorCommand.java`  | One-time Pinecone seeding CLI                          |

## Files to Modify

| File                            | Changes                                                |
|---------------------------------|--------------------------------------------------------|
| `pom.xml`                       | Add spring-ai-openai, pinecone-client; remove caffeine |
| `application.properties`        | Add Pinecone/OpenAI config; remove cache config        |
| `JooqDocumentationService.java` | Rewire @Tool methods to HybridSearchService            |
| `McpConfiguration.java`         | Remove @EnableCaching                                  |

## Files to Delete

| File                                 | Reason                      |
|--------------------------------------|-----------------------------|
| `LocalJooqDocumentationService.java` | Replaced by Pinecone search |
| `InvertedIndex.java`                 | No longer needed            |
| `search/` package                    | Entire package obsolete     |
| `TextProcessor.java`                 | No longer needed            |

---

## Implementation Steps

### Phase 1: Setup Infrastructure

1. **Create Pinecone index**
    - Go to [Pinecone Console](https://app.pinecone.io/)
    - Create index named `jooq-docs`
    - Dimensions: 1536 (for text-embedding-3-small)
    - Metric: cosine
    - Type: Serverless, region eu-west-1 (near Fly.io)

2. **Add Fly.io secrets**
   ```bash
   fly secrets set OPENAI_API_KEY=sk-...
   fly secrets set PINECONE_API_KEY=pcsk_...
   ```

3. **Add Maven dependencies** (see below)

### Phase 2: Core Services

4. Create model classes:
    - `model/DocumentChunk.java`
    - `model/SearchResult.java`

5. Create config classes:
    - `config/PineconeConfig.java`
    - `config/OpenAiConfig.java`

6. Implement `service/OpenAiEmbeddingService.java`
    - Single embedding generation for queries
    - Batch embedding generation for seeding

7. Implement `service/PineconeSearchService.java`
    - Semantic search via vector similarity
    - Metadata-based filtering (for language, section type)

8. Implement `service/HybridSearchService.java`
    - Combine semantic + keyword results
    - Reciprocal Rank Fusion algorithm for merging

### Phase 3: One-Time Seeding

9. Create `cli/EmbeddingGeneratorCommand.java`
    - Reuse HTML parsing logic from LocalJooqDocumentationService
    - Header-based chunking: 1000 chars max, 200 char overlap
    - Extract keywords for hybrid search
    - Batch embed with OpenAI (100 chunks/batch)
    - Upsert to Pinecone with metadata

10. Run seeding locally:
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.arguments="--embedding.generate=true"
    ```

### Phase 4: Migration

11. Modify `JooqDocumentationService.java`:
    - Replace LocalJooqDocumentationService dependency with HybridSearchService
    - Keep @Tool method signatures identical

12. Simplify `McpConfiguration.java`:
    - Remove @EnableCaching annotation
    - Remove Caffeine bean configuration

13. Delete obsolete classes:
    - `LocalJooqDocumentationService.java`
    - `InvertedIndex.java`
    - `search/` package
    - `TextProcessor.java`
    - `MemoryHealthIndicator.java`

### Phase 5: Deploy

14. Update `fly.toml` - reduce memory:
    ```toml
    [[vm]]
      memory = '512mb'  # Down from 2gb
      cpu_kind = 'shared'
      cpus = 1
    ```

15. Deploy:
    ```bash
    ./mvnw clean package
    fly deploy
    ```

---

## Dependencies

### Add to pom.xml

```xml
<!-- Spring AI OpenAI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

        <!-- Pinecone Java SDK -->
<dependency>
<groupId>io.pinecone</groupId>
<artifactId>pinecone-client</artifactId>
<version>2.0.0</version>
</dependency>

        <!-- gRPC for Pinecone -->
<dependency>
<groupId>io.grpc</groupId>
<artifactId>grpc-netty-shaded</artifactId>
<version>1.59.0</version>
</dependency>
```

### Remove from pom.xml

```xml
<!-- No longer needed -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## Configuration (application.properties)

```properties
# === MCP Server (unchanged) ===
spring.application.name=jooq-mcp
spring.ai.mcp.server.name=jooq-documentation-mcp
spring.ai.mcp.server.version=2.0.0
# === OpenAI ===
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.embedding.options.model=text-embedding-3-small
# === Pinecone ===
pinecone.api-key=${PINECONE_API_KEY}
pinecone.index-name=jooq-docs
# === Search Tuning ===
search.semantic-weight=0.7
search.keyword-weight=0.3
search.default-limit=5
# === Embedding CLI (one-time) ===
embedding.generate=false
embedding.chunk-size=1000
embedding.chunk-overlap=200
```

---

## Key Code Snippets

### HybridSearchService - Reciprocal Rank Fusion

```java

@Service
public class HybridSearchService {
    private final PineconeSearchService pineconeSearch;

    @Value("${search.semantic-weight:0.7}")
    private double semanticWeight;

    @Value("${search.keyword-weight:0.3}")
    private double keywordWeight;

    public List<SearchResult> hybridSearch(String query, int limit) {
        // Get more results initially for better fusion
        var semanticResults = pineconeSearch.semanticSearch(query, limit * 3);
        var keywordResults = pineconeSearch.keywordSearch(query, limit * 3);

        return mergeWithRRF(semanticResults, keywordResults, limit);
    }

    private List<SearchResult> mergeWithRRF(
            List<SearchResult> semantic,
            List<SearchResult> keyword,
            int limit) {
        Map<String, Double> scores = new HashMap<>();
        int k = 60; // RRF constant

        for (int i = 0; i < semantic.size(); i++) {
            String id = semantic.get(i).id();
            scores.merge(id, semanticWeight / (k + i), Double::sum);
        }

        for (int i = 0; i < keyword.size(); i++) {
            String id = keyword.get(i).id();
            scores.merge(id, keywordWeight / (k + i), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> findResultById(e.getKey(), semantic, keyword))
                .toList();
    }
}
```

### DocumentChunk Model

```java
public record DocumentChunk(
        String id,              // Unique chunk identifier
        String sectionId,       // Original section anchor
        String title,           // Section title
        String breadcrumb,      // Navigation path: "Overview > Getting Started"
        String content,         // Chunk text content
        int chunkIndex,         // Index within section (for overlap)
        String language,        // "java", "sql", "xml", "text"
        List<String> keywords   // Extracted keywords for hybrid search
) {
}
```

### Pinecone Metadata Structure

```json
{
  "id": "getting-started_chunk_0",
  "values": [
    0.023,
    -0.041,
    ...
  ],
  "metadata": {
    "sectionId": "getting-started",
    "title": "Getting started with jOOQ",
    "breadcrumb": "Overview > Getting started with jOOQ",
    "content": "This section will get you started with jOOQ quickly...",
    "chunkIndex": 0,
    "language": "text",
    "keywords": [
      "jooq",
      "getting",
      "started",
      "setup",
      "tutorial"
    ]
  }
}
```

---

## Cost Estimates

| Service                              | Cost                                              |
|--------------------------------------|---------------------------------------------------|
| Pinecone                             | Free tier (100K vectors, plenty for ~1000 chunks) |
| OpenAI embeddings (one-time seeding) | ~$0.03                                            |
| OpenAI embeddings (per search query) | ~$0.0001                                          |
| **Monthly estimate**                 | ~$5-10 for typical usage                          |

---

## Benefits After Migration

| Before                        | After                         |
|-------------------------------|-------------------------------|
| Daily restarts needed         | Stateless, no memory pressure |
| 2GB memory required           | 512MB sufficient              |
| Keyword-only search           | Semantic understanding        |
| In-memory index               | Persistent in Pinecone        |
| Cold start: slow (parse HTML) | Cold start: fast (no loading) |

---

## Rollback Plan

If issues arise:

```bash
# Tag before migration
git tag v1.0.2-pre-pinecone

# Rollback if needed
git checkout v1.0.2-pre-pinecone
fly deploy
```

---

## Reference

- [Vaadin MCP Server](https://github.com/vaadin/vaadin-mcp) - Inspiration for this architecture
- [Pinecone Java SDK](https://docs.pinecone.io/docs/java-client)
- [Spring AI OpenAI](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)
- [Reciprocal Rank Fusion](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf) - Algorithm for combining search
  results
