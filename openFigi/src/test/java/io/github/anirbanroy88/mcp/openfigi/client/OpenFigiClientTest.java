package io.github.anirbanroy88.mcp.openfigi.client;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.anirbanroy88.mcp.openfigi.MockOpenFigi;
import io.github.anirbanroy88.mcp.openfigi.TestSettings;
import static org.assertj.core.api.Assertions.*;

class OpenFigiClientTest {
    private final MockOpenFigi upstream = new MockOpenFigi();
    private final ObjectMapper mapper = new ObjectMapper();
    @AfterEach void close() { upstream.close(); }
    private OpenFigiClient client(Duration timeout, int bytes) {
        var properties = TestSettings.properties(upstream.uri(), timeout, bytes);
        return new OpenFigiClient(mapper, properties, new TrafficLimiter(properties));
    }
    private void call(OpenFigiClient client) {
        client.exchange("/v3/mapping/values/idType", null, UpstreamCredential.of("private-test-key"), TrafficLimiter.Category.MAPPING);
    }

    @Test void invalidKeyIsNeverRetriedOrReflected() {
        upstream.handler = request -> MockOpenFigi.Reply.json(401, "private-test-key");
        assertThatThrownBy(() -> call(client(Duration.ofSeconds(1), 1024)))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> {
                    assertThat(e.code()).isEqualTo("INVALID_API_KEY");
                    assertThat(e.getMessage()).doesNotContain("private-test-key");
                    assertThat(e.getCause()).isNull();
                });
        assertThat(upstream.requests).hasSize(1);
    }
    @Test void rateLimitBlocksFurtherCallsAndIncludesRetryGuidance() {
        upstream.handler = request -> new MockOpenFigi.Reply(429, "private-test-key",
                Map.of("ratelimit-reset", "7", "Retry-After", "3"), 0);
        var client = client(Duration.ofSeconds(1), 1024);
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> call(client))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> {
                    assertThat(e.code()).isEqualTo("RATE_LIMITED");
                    assertThat(e.retryAfterSeconds()).isBetween(1L, 7L);
                });
        assertThat(upstream.requests).hasSize(1);
    }
    @Test void retriesTransientFailuresOnce() {
        var attempts = new AtomicInteger();
        upstream.handler = request -> attempts.getAndIncrement() == 0
                ? MockOpenFigi.Reply.json(503, "unavailable") : MockOpenFigi.Reply.json(200, "{\"values\":[]}");
        call(client(Duration.ofSeconds(1), 1024));
        assertThat(upstream.requests).hasSize(2);
    }
    @Test void persistentFailureHasBoundedRetries() {
        upstream.handler = request -> MockOpenFigi.Reply.json(500, "private-test-key");
        assertThatThrownBy(() -> call(client(Duration.ofSeconds(1), 1024)))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> assertThat(e.code()).isEqualTo("UPSTREAM_UNAVAILABLE"));
        assertThat(upstream.requests).hasSize(2);
    }
    @Test void limitsResponseSize() {
        upstream.handler = request -> MockOpenFigi.Reply.json(200, "{\"padding\":\"" + "x".repeat(2000) + "\"}");
        assertThatThrownBy(() -> call(client(Duration.ofSeconds(1), 1024)))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> assertThat(e.code()).isEqualTo("RESPONSE_TOO_LARGE"));
    }
    @Test void boundsReadTimeout() {
        upstream.handler = request -> new MockOpenFigi.Reply(200, "{}", Map.of(), 600);
        assertThatThrownBy(() -> call(client(Duration.ofMillis(100), 1024)))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> assertThat(e.code()).isEqualTo("UPSTREAM_TIMEOUT"));
    }
    @Test void doesNotFollowRedirectsWithCredentials() {
        upstream.handler = request -> new MockOpenFigi.Reply(302, "", Map.of("Location", upstream.uri() + "/elsewhere"), 0);
        assertThatThrownBy(() -> call(client(Duration.ofSeconds(1), 1024))).isInstanceOf(OpenFigiException.class);
        assertThat(upstream.requests).hasSize(1);
    }
    @Test void rejectsMalformedSuccessfulResponse() {
        upstream.handler = request -> MockOpenFigi.Reply.json(200, "not-json");
        assertThatThrownBy(() -> call(client(Duration.ofSeconds(1), 1024)))
                .isInstanceOfSatisfying(OpenFigiException.class, e -> assertThat(e.code()).isEqualTo("UPSTREAM_RESPONSE"));
    }
}
