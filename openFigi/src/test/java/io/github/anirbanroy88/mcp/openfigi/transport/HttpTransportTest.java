package io.github.anirbanroy88.mcp.openfigi.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.github.anirbanroy88.mcp.openfigi.MockOpenFigi;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mcp.security.mode=token", "mcp.security.token=personal-test-token-01234567890123456789",
                "OPENFIGI_API_KEY=must-never-be-used-by-http"})
@ActiveProfiles("http")
class HttpTransportTest {
    static final String TOKEN = "personal-test-token-01234567890123456789";
    static final MockOpenFigi UPSTREAM = new MockOpenFigi();
    @LocalServerPort int port;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("openfigi.base-url", () -> UPSTREAM.uri().toString());
    }
    @AfterAll static void closeUpstream() { UPSTREAM.close(); }
    @BeforeEach void reset() {
        UPSTREAM.requests.clear();
        UPSTREAM.handler = request -> MockOpenFigi.Reply.json(200, "{\"values\":[\"ID_ISIN\"]}");
    }
    private McpSyncClient connect(Supplier<String> key) {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) -> {
                    builder.header("Authorization", "Bearer " + TOKEN);
                    String value = key.get();
                    if (value != null) builder.header("X-OpenFIGI-API-Key", value);
                }).build();
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
        return client;
    }
    private McpSchema.CallToolResult values(McpSyncClient client) {
        return client.callTool(new McpSchema.CallToolRequest("openfigi_values", Map.of("key", "idType")));
    }

    @Test void discoversToolsAndDoesNotRetainKeyWithinSession() {
        var key = new AtomicReference<>("first-request-key");
        try (var client = connect(key::get)) {
            var tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                    "openfigi_mapping", "openfigi_search", "openfigi_filter", "openfigi_values");
            assertThat(tools.toString()).doesNotContain("API_KEY", "first-request-key");
            assertThat(tools).allMatch(t -> Boolean.TRUE.equals(t.annotations().readOnlyHint()));
            assertThat(values(client).isError()).isFalse();
            key.set(null);
            assertThat(values(client).isError()).isFalse();
            key.set("replacement-key");
            assertThat(values(client).isError()).isFalse();
        }
        assertThat(UPSTREAM.requests).extracting(MockOpenFigi.Request::key)
                .containsExactly("first-request-key", null, "replacement-key");
    }

    @Test void concurrentClientsKeepTheirCredentialsIsolated() throws Exception {
        try (var keyed = connect(() -> "parallel-key"); var free = connect(() -> null);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> values(keyed));
            var two = executor.submit(() -> values(free));
            assertThat(one.get().isError()).isFalse();
            assertThat(two.get().isError()).isFalse();
        }
        assertThat(UPSTREAM.requests).extracting(MockOpenFigi.Request::key)
                .containsExactlyInAnyOrder("parallel-key", null);
    }

    @Test void invalidKeyIsAnMcpErrorAndDoesNotFallBack() {
        UPSTREAM.handler = request -> MockOpenFigi.Reply.json(401, "do-not-reflect-secret");
        try (var client = connect(() -> "do-not-reflect-secret")) {
            var result = values(client);
            assertThat(result.isError()).isTrue();
            assertThat(result.content().toString()).contains("INVALID_API_KEY").doesNotContain("do-not-reflect-secret");
        }
        assertThat(UPSTREAM.requests).hasSize(1);
    }

    @Test void validationErrorsAreMcpErrorsWithoutUpstreamTraffic() {
        try (var client = connect(() -> null)) {
            var result = client.callTool(new McpSchema.CallToolRequest("openfigi_mapping", Map.of("jobs", java.util.List.of())));
            assertThat(result.isError()).isTrue();
        }
        assertThat(UPSTREAM.requests).isEmpty();
    }

    @Test void rejectsMissingTokenAndUnapprovedOrigin() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var noToken = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(noToken.statusCode()).isEqualTo(401);
            assertThat(noToken.headers().firstValue("WWW-Authenticate")).isPresent();
            var badOrigin = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                    .header("Authorization", "Bearer " + TOKEN).header("Origin", "https://untrusted.example")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(badOrigin.statusCode()).isEqualTo(403);
        }
        assertThat(UPSTREAM.requests).isEmpty();
    }
}
