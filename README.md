# jOOQ MCP Server

A Model Context Protocol (MCP) server that provides AI models with access to jOOQ documentation. This Spring Boot application uses Spring AI to expose jOOQ documentation as MCP tools, allowing AI systems to query and retrieve information about jOOQ features, SQL examples, and best practices.

## Features

The MCP server provides the following tools:

- **searchDocumentation**: Search jOOQ documentation for specific topics
- **getSqlExamples**: Get SQL query building examples for specific operations
- **getCodeGenerationGuide**: Retrieve jOOQ code generation documentation
- **getDatabaseSupport**: Get database-specific support information
- **getQueryDslReference**: Get Query DSL reference for specific statement types
- **getAdvancedFeatures**: Access documentation for advanced jOOQ features

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6+

### Running the Application

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd jooq-mcp
   ```

2. **Build the project:**
   ```bash
   ./mvnw clean install
   ```

3. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```

The MCP server will start and be available for connections from MCP clients.

### Using with MCP Clients

This server can be used with any MCP-compatible AI client. The server exposes tools that allow AI models to:

- Search jOOQ documentation by keyword
- Retrieve specific SQL examples and code snippets
- Access database-specific configuration information
- Get guidance on code generation setup
- Find information about advanced jOOQ features

### Configuration

The application can be configured via `application.properties`:

```properties
# MCP Server Configuration
spring.ai.mcp.server.name=jooq-documentation-mcp
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.capabilities.tool=true

# Cache Configuration
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=1h
```

### Example Usage

When connected to an MCP client, you can ask questions like:

- "How do I create a SELECT statement in jOOQ?"
- "Show me examples of jOOQ INSERT operations"
- "What databases does jOOQ support?"
- "How do I configure jOOQ code generation?"
- "How do I use transactions in jOOQ?"

The server will fetch the relevant documentation and provide detailed answers with code examples.

## Architecture

The application consists of:

- **JooqDocumentationService**: Main service class with @Tool annotated methods
- **JooqDocumentationFetcher**: Handles fetching and parsing jOOQ documentation
- **McpConfiguration**: Spring configuration for MCP tool registration
- **Caching**: Caffeine-based caching to improve performance

## Testing

Run the test suite:

```bash
./mvnw test
```

## Development

The application uses:
- Spring Boot 3.5.4
- Spring AI 1.0.0 with MCP Server support
- JSoup for HTML parsing
- Caffeine for caching

To add new tools, create methods annotated with `@Tool` in the `JooqDocumentationService` class.