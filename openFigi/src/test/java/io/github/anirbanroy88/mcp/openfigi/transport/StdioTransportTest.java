package io.github.anirbanroy88.mcp.openfigi.transport;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.github.anirbanroy88.mcp.openfigi.MockOpenFigi;
import static org.assertj.core.api.Assertions.*;

class StdioTransportTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "stdio-test-key"})
    void launchesPackagedJarWithoutStdoutContamination(String key) {
        try (var upstream = new MockOpenFigi()) {
            String java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            var parameters = ServerParameters.builder(java)
                    .args("-jar", System.getProperty("openfigi.test.jar"), "--spring.profiles.active=stdio", "--debug=false",
                            "--openfigi.base-url=" + upstream.uri())
                    .env(Map.of("OPENFIGI_API_KEY", key)).build();
            var transport = new StdioClientTransport(parameters, new JacksonMcpJsonMapper(new ObjectMapper()));
            var errors = new CopyOnWriteArrayList<String>();
            transport.setStdErrorHandler(errors::add);
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).initializationTimeout(Duration.ofSeconds(60)).build()) {
                assertThat(client.initialize().serverInfo().name()).isEqualTo("openfigi-mcp");
                assertThat(client.listTools().tools()).hasSize(4);
                var result = client.callTool(new McpSchema.CallToolRequest("openfigi_values", Map.of("key", "idType")));
                assertThat(result.isError()).isFalse();
                assertThat(result.structuredContent()).isNotNull();
            }
            assertThat(upstream.requests.element().key()).isEqualTo(key.isEmpty() ? null : key);
            assertThat(String.join("\n", errors)).doesNotContain("Tomcat initialized", "Tomcat started");
            if (!key.isEmpty()) assertThat(String.join("\n", errors)).doesNotContain(key);
        }
    }
}
