package ch.martinelli.jooqmcp.config;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces the auto-configured streamable-HTTP transport to turn on idle session eviction.
 * <p>
 * Spring AI's auto-configuration only passes the endpoint, the keep-alive interval and
 * disallow-delete to the transport. It does not expose {@code sessionIdleTimeout}, which
 * defaults to {@code null}: idle eviction is off and a session lives until the client sends
 * DELETE /mcp, which clients rarely do.
 * <p>
 * Keep-alive alone does not clean up: when a ping fails, the SDK's {@code KeepAliveScheduler}
 * logs and swallows the error but never removes the session. Each abandoned session keeps its
 * SSE stream, its async Tomcat request and that request's buffers. In production that
 * accumulated to 6,445 sessions and about 3,000 open requests in 18 days until the heap ran
 * out (2026-11-03).
 * <p>
 * The auto-configured bean is {@code @ConditionalOnMissingBean}, so declaring this one makes
 * it back off, and its router function picks up this transport instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpSessionProperties.class)
public class McpTransportConfiguration {

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties,
            McpSessionProperties session) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .sessionIdleTimeout(session.idleTimeout())
                .maxSessions(session.maxSessions())
                .build();
    }
}
