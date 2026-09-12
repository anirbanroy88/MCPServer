package io.github.anirbanroy88.mcp.openfigi.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import io.github.anirbanroy88.mcp.openfigi.config.OpenFigiProperties;

@Component
public final class OpenFigiClient {
    private final RestClient http;
    private final ObjectMapper mapper;
    private final OpenFigiProperties properties;
    private final TrafficLimiter limiter;
    private final Semaphore concurrency;

    public OpenFigiClient(ObjectMapper mapper, OpenFigiProperties properties, TrafficLimiter limiter) {
        this.mapper = mapper;
        this.properties = properties;
        this.limiter = limiter;
        this.concurrency = new Semaphore(properties.maxConcurrentRequests());
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.http = RestClient.builder().baseUrl(properties.baseUrl().toString())
                .requestFactory(factory).build();
    }

    public JsonNode exchange(String path, JsonNode payload, UpstreamCredential credential, TrafficLimiter.Category category) {
        if (!concurrency.tryAcquire()) throw new OpenFigiException("BUSY", "Server is busy; retry later", 1L);
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                limiter.acquire(credential, category);
                Reply reply = send(path, payload, credential);
                if (reply.status() >= 200 && reply.status() < 300) {
                    if ("0".equals(reply.remaining())) limiter.block(credential, category, retrySeconds(reply.reset(), 60));
                    try {
                        JsonNode result = mapper.readTree(reply.body());
                        if (result == null || (!result.isObject() && !result.isArray()))
                            throw new OpenFigiException("UPSTREAM_RESPONSE", "OpenFIGI returned an invalid response");
                        return result;
                    } catch (IOException malformed) {
                        throw new OpenFigiException("UPSTREAM_RESPONSE", "OpenFIGI returned invalid JSON");
                    }
                }
                if (reply.status() == 401 || reply.status() == 403)
                    throw new OpenFigiException("INVALID_API_KEY", "OpenFIGI rejected the supplied API key");
                if (reply.status() == 429) {
                    long seconds = Math.max(retrySeconds(reply.retryAfter(), 1), retrySeconds(reply.reset(), 60));
                    limiter.block(credential, category, seconds);
                    throw new OpenFigiException("RATE_LIMITED", "OpenFIGI rate limit reached; retry later", seconds);
                }
                if (reply.status() >= 500 && attempt == 0) {
                    try { Thread.sleep(250); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new OpenFigiException("CANCELLED", "OpenFIGI call interrupted");
                    }
                    continue;
                }
                if (reply.status() >= 400 && reply.status() < 500)
                    throw new OpenFigiException("UPSTREAM_REJECTED", "OpenFIGI rejected the request; check identifiers and filters");
                throw new OpenFigiException("UPSTREAM_UNAVAILABLE", "OpenFIGI is unavailable; retry later");
            }
            throw new OpenFigiException("UPSTREAM_UNAVAILABLE", "OpenFIGI is unavailable; retry later");
        } finally { concurrency.release(); }
    }

    private Reply send(String path, JsonNode payload, UpstreamCredential credential) {
        try {
            var request = http.method(payload == null ? HttpMethod.GET : HttpMethod.POST)
                    .uri(path).accept(MediaType.APPLICATION_JSON);
            if (credential.keyed()) request.header("X-OPENFIGI-APIKEY", credential.headerValue());
            if (payload != null) request.contentType(MediaType.APPLICATION_JSON).body(payload.toString());
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                HttpHeaders headers = response.getHeaders();
                // Never read or propagate upstream error bodies, which can contain reflected secrets.
                byte[] body = status >= 200 && status < 300
                        ? response.getBody().readNBytes(properties.maxResponseBytes() + 1) : new byte[0];
                if (body.length > properties.maxResponseBytes())
                    throw new OpenFigiException("RESPONSE_TOO_LARGE", "OpenFIGI response exceeds the configured size limit");
                return new Reply(status, body, headers.getFirst("ratelimit-remaining"),
                        headers.getFirst("ratelimit-reset"), headers.getFirst("Retry-After"));
            });
        } catch (ResourceAccessException failure) {
            Throwable cause = failure;
            while (cause != null) {
                if (cause instanceof SocketTimeoutException)
                    throw new OpenFigiException("UPSTREAM_TIMEOUT", "OpenFIGI request timed out");
                cause = cause.getCause();
            }
            throw new OpenFigiException("UPSTREAM_UNAVAILABLE", "Could not reach OpenFIGI");
        } catch (OpenFigiException safe) {
            throw safe;
        } catch (Exception failure) {
            throw new OpenFigiException("UPSTREAM_UNAVAILABLE", "OpenFIGI request failed");
        }
    }
    private static long retrySeconds(String raw, long fallback) {
        try { return Math.min(3600, Math.max(1, Long.parseLong(raw))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private record Reply(int status, byte[] body, String remaining, String reset, String retryAfter) {}
}
