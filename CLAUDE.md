# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application designed to implement a Model Context Protocol (MCP) server using Spring AI. The project uses Java 21 and Maven as the build tool.

## Key Technologies

- Spring Boot 4.1.2
- Spring AI 2.0.2 with MCP Server WebMVC starter (streamable HTTP transport at `/mcp`)
- OpenAI embeddings (`text-embedding-3-small`) and Pinecone vector database
- Java 21
- Maven (releases via gitflow-maven-plugin: work on `develop`, release to `main`)

## Build and Run Commands

```bash
# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run

# Run unit tests
./mvnw test

# Run integration tests locally (requires OPENAI_API_KEY and PINECONE_API_KEY)
./mvnw verify -DskipITs=false

# Seed the Pinecone index from the Markdown manual (replaces the whole index)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--embedding.generate=true"

# Package as JAR
./mvnw package

# Docker deployment
docker build -t jooq-mcp .
docker run -p 8080:8080 jooq-mcp

# Fly.io deployment
fly deploy
```

## Architecture

The application answers MCP tool calls with hybrid search over the jOOQ manual stored in Pinecone:

- `src/main/java/ch/martinelli/jooqmcp/` - Main Java source code
  - `JooqMcpApplication.java` - Spring Boot application entry point
  - `service/JooqDocumentationService.java` - MCP tools with `@Tool` methods
  - `service/HybridSearchService.java` - Merges semantic and keyword results with Reciprocal Rank Fusion (weights `search.semantic-weight` / `search.keyword-weight`)
  - `service/PineconeSearchService.java` - Semantic (vector) and keyword search against Pinecone, drops unrelated semantic matches
  - `service/OpenAiEmbeddingService.java` - Embeddings for queries and, in batches, for seeding
  - `cli/EmbeddingGeneratorCommand.java` - One-time seeding of the Pinecone index, runs with `--embedding.generate=true`
  - `cli/ManualMarkdownParser.java` - Splits the single-file Markdown manual into one section per anchored heading
  - `config/McpConfiguration.java` - MCP tool registration
  - `config/McpTransportConfiguration.java` / `McpSessionProperties.java` - Replaces the auto-configured streamable-HTTP transport to evict idle sessions (`jooq-mcp.session.*`)
  - `config/PineconeConfig.java`, `config/OpenAiConfig.java` - Client configuration
  - `health/OpenAiHealthIndicator.java` - Health goes DOWN when the OpenAI quota is exhausted
  - `exception/GlobalExceptionHandler.java` - Error handling
- `src/main/resources/` - Application resources
  - `application.properties` - Spring Boot, MCP, OpenAI, Pinecone and search configuration
  - `static/index.html` - Public website; Maven resource filtering fills in `@project.version@`, `@project.parent.version@` and `@spring-ai.version@`
- `src/test/java/` - Unit tests (`*Test`) and integration tests (`*IT`, skipped unless `-DskipITs=false`)

## MCP Tools Available

The server provides these tools for AI models:

1. `searchDocumentation(String query)` - Search jOOQ docs by topic, feature or API name
2. `getSqlExamples(String topic)` - Get SQL examples for specific operations
3. `getCodeGenerationGuide()` - Retrieve code generation documentation
4. `getDatabaseSupport(String database)` - Get database-specific support info
5. `getQueryDslReference(String queryType)` - Get DSL reference for query types
6. `getAdvancedFeatures(String feature)` - Access advanced feature documentation

## Development Notes

- All MCP tools are defined in `JooqDocumentationService` with `@Tool` annotations
- The Pinecone index is seeded from the Markdown manual at `embedding.manual-url` with `--embedding.generate=true`, which replaces the whole index
- Search is hybrid: semantic search finds concepts, keyword search finds exact API identifiers like `fetchInto`
- Abandoned MCP sessions used to accumulate until the heap ran out; idle eviction in `McpTransportConfiguration` prevents that
- When changing tools or search, keep `static/index.html` in sync

## Deployment

- **Docker**: Dockerfile uses Azul Zulu OpenJDK Alpine 21; JVM flags cap heap, direct memory (Netty/gRPC of the Pinecone client) and metaspace to fit the 1 GB machine
- **Fly.io**: Region `fra`, one shared-CPU 1 GB machine always running (auto stop/start for extra machines), a volume mounted at `/tmp` so heap dumps and GC logs survive restarts
- **Health endpoint**: `/actuator/health` is the only exposed actuator endpoint
- **Secrets**: `OPENAI_API_KEY` and `PINECONE_API_KEY`

## Configuration Properties

- `spring.ai.mcp.server.*` - MCP server configuration (streamable protocol, keep-alive interval)
- `jooq-mcp.session.*` - Idle timeout and maximum number of MCP sessions
- `spring.ai.openai.*` - OpenAI API key and embedding model
- `pinecone.*` - Pinecone API key and index name
- `search.*` - Hybrid search weights and default result limit
- `embedding.*` - Seeding: manual URL, chunk size and overlap
- `server.tomcat.*` - Server buffer configuration for handling large responses

Reference: [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
