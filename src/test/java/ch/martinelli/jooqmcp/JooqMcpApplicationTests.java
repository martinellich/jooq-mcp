package ch.martinelli.jooqmcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that require OPENAI_API_KEY and PINECONE_API_KEY environment variables.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
class JooqMcpApplicationTests {

	@Test
	void contextLoads() {
	}

}
