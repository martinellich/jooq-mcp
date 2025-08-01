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

### Docker Deployment

1. **Build the JAR file:**
   ```bash
   ./mvnw clean package
   ```

2. **Build the Docker image:**
   ```bash
   docker build -t jooq-mcp .
   ```

3. **Run the Docker container:**
   ```bash
   docker run -p 8080:8080 jooq-mcp
   ```

### Fly.io Deployment

This application is configured for deployment on Fly.io:

1. **Install Fly CLI and authenticate:**
   ```bash
   brew install flyctl  # or your preferred installation method
   fly auth login
   ```

2. **Deploy the application:**
   ```bash
   ./mvnw clean package
   fly deploy
   ```

The application includes health checks at `/actuator/health` and is configured with auto-scaling.

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

# jOOQ Documentation Crawler Configuration
jooq.documentation.crawler.max-depth=4
jooq.documentation.crawler.max-urls-per-section=100
jooq.documentation.crawler.timeout-ms=10000
jooq.documentation.crawler.cache-duration-hours=24

# Server Configuration - SSE Buffer Settings
server.tomcat.max-http-response-header-size=64KB
server.tomcat.max-swallow-size=10MB
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

- **JooqDocumentationService**: Main service class with @Tool annotated methods for MCP integration
- **LocalJooqDocumentationService**: Provides local documentation indexing and full-text search with TF-IDF scoring
- **InvertedIndex**: Implements advanced full-text search capabilities with relevance scoring
- **JooqDocumentationCrawler**: Crawls and fetches jOOQ documentation for local storage
- **JooqDocumentationFetcher**: Handles parsing and extraction of documentation content
- **McpConfiguration**: Spring configuration for MCP tool registration
- **Caching**: Caffeine-based caching to improve performance

### Key Features

- **Local Documentation Storage**: Documentation is stored locally in `src/main/resources/docs/` for faster access
- **Full-Text Search**: Advanced search using TF-IDF scoring for better relevance
- **Efficient Indexing**: In-memory inverted index for fast document retrieval
- **Code Example Extraction**: Automatic extraction and categorization of code examples

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