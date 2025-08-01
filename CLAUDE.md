# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application designed to implement a Model Context Protocol (MCP) server using Spring AI. The project uses Java 21 and Maven as the build tool.

## Key Technologies

- Spring Boot 3.5.4
- Spring AI 1.0.0 with MCP Server WebMVC starter
- Java 21
- Maven

## Build and Run Commands

```bash
# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Package as JAR
./mvnw package

# Docker deployment
docker build -t jooq-mcp .
docker run -p 8080:8080 jooq-mcp

# Fly.io deployment
fly deploy
```

## Architecture

The application provides jOOQ documentation access through MCP tools with local indexing and search:

- `src/main/java/ch/martinelli/jooqmcp/` - Main Java source code
  - `JooqMcpApplication.java` - Spring Boot application entry point
  - `service/JooqDocumentationService.java` - Main MCP tools with @Tool methods
  - `service/LocalJooqDocumentationService.java` - Local documentation indexing and search with TF-IDF scoring
  - `service/JooqDocumentationCrawler.java` - Crawls and fetches jOOQ documentation
  - `service/JooqDocumentationFetcher.java` - Documentation parsing and content extraction
  - `search/InvertedIndex.java` - Full-text search implementation with relevance scoring
  - `util/TextProcessor.java` - Text processing utilities for search
  - `config/McpConfiguration.java` - MCP tool registration configuration
  - `exception/GlobalExceptionHandler.java` - Error handling
- `src/main/resources/` - Application resources
  - `application.properties` - Spring Boot and MCP server configuration
  - `docs/manual-single-page.html` - Locally stored jOOQ documentation
  - `static/` - Static web assets
- `src/test/java/` - Test code

## MCP Tools Available

The server provides these tools for AI models:

1. `searchDocumentation(String query)` - Search jOOQ docs by keyword
2. `getSqlExamples(String topic)` - Get SQL examples for specific operations
3. `getCodeGenerationGuide()` - Retrieve code generation documentation
4. `getDatabaseSupport(String database)` - Get database-specific support info
5. `getQueryDslReference(String queryType)` - Get DSL reference for query types
6. `getAdvancedFeatures(String feature)` - Access advanced feature documentation

## Development Notes

- All MCP tools are defined in `JooqDocumentationService` with `@Tool` annotations
- Documentation is stored locally in `resources/docs/` for faster access
- Full-text search uses TF-IDF scoring via `InvertedIndex` for better relevance
- `LocalJooqDocumentationService` handles document indexing and search operations
- JSoup is used for HTML parsing and content extraction
- Caffeine provides caching to improve performance
- Error handling includes timeouts, network failures, and buffer overflow prevention
- Server is configured with increased buffer sizes for SSE (Server-Sent Events)

## Deployment

- **Docker**: Dockerfile uses Azul Zulu OpenJDK Alpine 21 for minimal image size
- **Fly.io**: Configured with health checks, auto-scaling, and 2GB memory allocation
- **Health endpoint**: Available at `/actuator/health` for monitoring

## Configuration Properties

- `spring.ai.mcp.server.*` - MCP server configuration
- `spring.cache.*` - Caffeine cache settings
- `jooq.documentation.crawler.*` - Documentation crawler settings (max-depth, timeout, cache duration)
- `server.tomcat.*` - Server buffer configuration for handling large responses

Reference: [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)