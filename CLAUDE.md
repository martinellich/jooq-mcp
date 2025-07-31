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

# Create Docker image (if needed)
./mvnw spring-boot:build-image
```

## Architecture

The application provides jOOQ documentation access through MCP tools:

- `src/main/java/ch/martinelli/jooqmcp/` - Main Java source code
  - `JooqMcpApplication.java` - Spring Boot application entry point
  - `service/JooqDocumentationService.java` - Main MCP tools with @Tool methods
  - `service/JooqDocumentationFetcher.java` - Documentation fetching and parsing
  - `config/McpConfiguration.java` - MCP tool registration configuration
  - `exception/GlobalExceptionHandler.java` - Error handling
- `src/main/resources/` - Application resources
  - `application.properties` - Spring Boot and MCP server configuration
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
- Documentation is fetched from https://www.jooq.org/doc/3.21/manual/ and cached
- JSoup is used for HTML parsing and content extraction
- Caffeine provides caching to improve performance
- Error handling includes timeouts and network failures

Reference: [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)