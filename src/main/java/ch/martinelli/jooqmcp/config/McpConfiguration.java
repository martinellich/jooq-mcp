package ch.martinelli.jooqmcp.config;

import ch.martinelli.jooqmcp.service.JooqDocumentationService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpConfiguration {

    @Bean
    public List<ToolCallback> jooqDocumentationTools(JooqDocumentationService jooqDocumentationService) {
        return List.of(ToolCallbacks.from(jooqDocumentationService));
    }
}