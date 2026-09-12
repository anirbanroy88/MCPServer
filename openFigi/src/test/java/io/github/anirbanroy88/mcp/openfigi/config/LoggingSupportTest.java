package io.github.anirbanroy88.mcp.openfigi.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoggingSupportTest {
    @Test
    void preservesOrdinaryParametersAndRedactsCredentialValues() {
        String message = "{\"query\":\"IBM\",\"apiKey\":\"secret\",\"authorization\":\"Bearer abc\"}";

        assertThat(LoggingSupport.sanitize(message))
                .contains("IBM", "[REDACTED]")
                .doesNotContain("secret", "Bearer abc");
    }

    @Test
    void sanitizesExceptionStackTraces() {
        var failure = new IllegalStateException("Authorization: Bearer secret-token");

        assertThat(LoggingSupport.sanitizedStackTrace(failure))
                .contains("IllegalStateException")
                .doesNotContain("secret-token");
    }
}
