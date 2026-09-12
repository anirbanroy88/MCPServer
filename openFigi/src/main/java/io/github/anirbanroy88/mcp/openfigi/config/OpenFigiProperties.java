package io.github.anirbanroy88.mcp.openfigi.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("openfigi")
public record OpenFigiProperties(
        @DefaultValue("https://api.openfigi.com") URI baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue("2097152") @Min(1024) @Max(16777216) int maxResponseBytes,
        @DefaultValue("16") @Min(1) @Max(128) int maxConcurrentRequests,
        @DefaultValue("5") @Min(1) @Max(5) int keylessBatchSize,
        @DefaultValue("100") @Min(1) @Max(100) int keyedBatchSize,
        @DefaultValue("25") @Min(1) @Max(25) int keylessMappingRequests,
        @DefaultValue("5") @Min(1) @Max(5) int keylessSearchRequests,
        @DefaultValue("25") @Min(1) @Max(25) int keyedMappingRequests,
        @DefaultValue("20") @Min(1) @Max(20) int keyedSearchRequests) {
    public OpenFigiProperties {
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !(baseUrl.getPath().isEmpty() || baseUrl.getPath().equals("/"))) {
            throw new IllegalArgumentException("openfigi.base-url must be an origin without credentials, path or query");
        }
        boolean local = "localhost".equals(baseUrl.getHost()) || "127.0.0.1".equals(baseUrl.getHost())
                || "[::1]".equals(baseUrl.getHost());
        if (!"https".equals(baseUrl.getScheme()) && !("http".equals(baseUrl.getScheme()) && local)) {
            throw new IllegalArgumentException("OpenFIGI requires HTTPS except for loopback tests");
        }
        if (connectTimeout == null || readTimeout == null || connectTimeout.isNegative()
                || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
                || connectTimeout.compareTo(Duration.ofSeconds(10)) > 0
                || readTimeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("OpenFIGI timeouts must be positive and at most 10 seconds");
        }
    }
}
