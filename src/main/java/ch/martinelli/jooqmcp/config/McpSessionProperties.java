package ch.martinelli.jooqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Session limits for the streamable-HTTP transport, applied by {@link McpTransportConfiguration}.
 *
 * @param idleTimeout a session that receives no request for this long is closed and removed
 * @param maxSessions upper bound on concurrent sessions; further initialize requests are rejected
 */
@ConfigurationProperties("jooq-mcp.session")
public record McpSessionProperties(Duration idleTimeout, long maxSessions) {
}
