package ch.martinelli.jooqmcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the keep-alive setting for the streamable-HTTP transport.
 * <p>
 * Without a keep-alive interval the server never writes to an idle SSE stream, so it
 * never learns that a client is gone: the async request stays open for the lifetime of
 * the process. Clients are not required to send DELETE /mcp, and in practice they don't,
 * so a failing keep-alive ping is what ends a dead stream. Leaving this unset leaked
 * roughly 16 HTTP connections per hour in production until the heap ran out.
 * <p>
 * A misspelled property would bind to nothing and silently disable keep-alive again,
 * which is exactly what this test exists to catch.
 */
class McpKeepAliveConfigurationTest {

    private static final String KEEP_ALIVE_PROPERTY = "spring.ai.mcp.server.streamable-http.keep-alive-interval";

    @Test
    void applicationPropertiesDeclaresKeepAliveInterval() throws IOException {
        Properties properties = loadApplicationProperties();

        String value = properties.getProperty(KEEP_ALIVE_PROPERTY);

        assertNotNull(value, KEEP_ALIVE_PROPERTY + " must be set, otherwise SSE sessions leak");
    }

    @Test
    void keepAliveIntervalBindsToTheStreamableTransportProperties() throws IOException {
        String value = loadApplicationProperties().getProperty(KEEP_ALIVE_PROPERTY);

        new ApplicationContextRunner()
                .withUserConfiguration(StreamableHttpPropertiesConfiguration.class)
                .withPropertyValues(KEEP_ALIVE_PROPERTY + "=" + value)
                .run(context -> {
                    McpServerStreamableHttpProperties bound = context
                            .getBean(McpServerStreamableHttpProperties.class);

                    assertThat(bound.getKeepAliveInterval())
                            .as("keep-alive interval reaching the transport provider")
                            .isNotNull()
                            .isPositive()
                            // Long intervals defeat the purpose: a dead stream is only
                            // detected on the next ping attempt.
                            .isLessThanOrEqualTo(Duration.ofMinutes(1));
                });
    }

    private static Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
    static class StreamableHttpPropertiesConfiguration {
    }
}
