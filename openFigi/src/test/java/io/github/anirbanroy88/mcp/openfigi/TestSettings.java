package io.github.anirbanroy88.mcp.openfigi;

import java.net.URI;
import java.time.Duration;
import io.github.anirbanroy88.mcp.openfigi.config.OpenFigiProperties;

public final class TestSettings {
    private TestSettings() {}
    public static OpenFigiProperties properties(URI uri) { return properties(uri, Duration.ofSeconds(2), 2097152); }
    public static OpenFigiProperties properties(URI uri, Duration timeout, int bytes) {
        return new OpenFigiProperties(uri, Duration.ofSeconds(1), timeout, bytes, 16, 5, 100, 25, 5, 25, 20);
    }
}
