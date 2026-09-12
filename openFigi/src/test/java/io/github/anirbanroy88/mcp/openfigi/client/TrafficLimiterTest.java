package io.github.anirbanroy88.mcp.openfigi.client;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import io.github.anirbanroy88.mcp.openfigi.TestSettings;
import static org.assertj.core.api.Assertions.*;

class TrafficLimiterTest {
    @Test void sharesKeylessBudgetAndSeparatesCredentialsAndOperations() {
        var clock = new MutableClock();
        var limiter = new TrafficLimiter(TestSettings.properties(URI.create("http://localhost")), clock);
        for (int i = 0; i < 5; i++) limiter.acquire(UpstreamCredential.of(null), TrafficLimiter.Category.SEARCH);
        assertThatThrownBy(() -> limiter.acquire(UpstreamCredential.of(" "), TrafficLimiter.Category.SEARCH))
                .isInstanceOf(OpenFigiException.class);
        limiter.acquire(UpstreamCredential.of(null), TrafficLimiter.Category.MAPPING);
        limiter.acquire(UpstreamCredential.of("key-a"), TrafficLimiter.Category.SEARCH);
        limiter.acquire(UpstreamCredential.of("key-b"), TrafficLimiter.Category.SEARCH);
        clock.now = clock.now.plusSeconds(60);
        limiter.acquire(UpstreamCredential.of(null), TrafficLimiter.Category.SEARCH);
    }
    @Test void keyedMappingWindowIsSixSeconds() {
        var clock = new MutableClock();
        var limiter = new TrafficLimiter(TestSettings.properties(URI.create("http://localhost")), clock);
        var credential = UpstreamCredential.of("key");
        for (int i = 0; i < 25; i++) limiter.acquire(credential, TrafficLimiter.Category.MAPPING);
        assertThatThrownBy(() -> limiter.acquire(credential, TrafficLimiter.Category.MAPPING)).isInstanceOf(OpenFigiException.class);
        clock.now = clock.now.plusSeconds(6);
        limiter.acquire(credential, TrafficLimiter.Category.MAPPING);
    }
    @Test void credentialRepresentationsDoNotRevealSecrets() {
        var key = UpstreamCredential.of("a-secret");
        assertThat(key.toString()).doesNotContain("a-secret");
        assertThat(key.budgetId()).doesNotContain("a-secret");
        assertThatThrownBy(() -> UpstreamCredential.of("key\r\ninjected")).isInstanceOf(OpenFigiException.class);
    }
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
