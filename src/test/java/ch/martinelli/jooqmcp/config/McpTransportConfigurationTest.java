package ch.martinelli.jooqmcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards idle session eviction on the streamable-HTTP transport.
 * <p>
 * Checking that a property binds is not enough: the keep-alive fix passed such a test while
 * production kept leaking sessions, because a failed keep-alive ping never removes a session.
 * This test checks the transport that actually serves /mcp: it must be ours (the
 * auto-configured one backs off) and it must carry an idle timeout, otherwise abandoned
 * sessions are never evicted.
 */
class McpTransportConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpServerStreamableHttpWebMvcAutoConfiguration.class))
            .withUserConfiguration(Infrastructure.class, McpTransportConfiguration.class)
            .withPropertyValues(
                    "spring.ai.mcp.server.protocol=streamable",
                    "jooq-mcp.session.idle-timeout=1h",
                    "jooq-mcp.session.max-sessions=1000");

    @Test
    void transportServingMcpHasIdleSessionEviction() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(WebMvcStreamableServerTransportProvider.class);
            WebMvcStreamableServerTransportProvider transport = context
                    .getBean(WebMvcStreamableServerTransportProvider.class);

            assertThat(ReflectionTestUtils.getField(transport, "sessionIdleTimeout"))
                    .as("idle timeout on the transport; null disables eviction")
                    .isEqualTo(Duration.ofHours(1));
            assertThat(ReflectionTestUtils.getField(transport, "maxSessions")).isEqualTo(1000L);
        });
    }

    @Test
    void transportKeepsTheConfiguredKeepAliveAndEndpoint() {
        runner.withPropertyValues("spring.ai.mcp.server.streamable-http.keep-alive-interval=30s")
                .run(context -> {
                    WebMvcStreamableServerTransportProvider transport = context
                            .getBean(WebMvcStreamableServerTransportProvider.class);

                    assertThat(ReflectionTestUtils.getField(transport, "keepAliveScheduler"))
                            .as("keep-alive scheduler, only created when an interval is set")
                            .isNotNull();
                    assertThat(ReflectionTestUtils.getField(transport, "mcpEndpoint")).isEqualTo("/mcp");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
    static class Infrastructure {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
