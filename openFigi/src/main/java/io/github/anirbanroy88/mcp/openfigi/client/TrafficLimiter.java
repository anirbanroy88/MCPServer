package io.github.anirbanroy88.mcp.openfigi.client;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import io.github.anirbanroy88.mcp.openfigi.config.OpenFigiProperties;

@Component
public final class TrafficLimiter {
    public enum Category { MAPPING, SEARCH }
    private static final int MAX_BUCKETS = 10_000;
    private final OpenFigiProperties properties;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public TrafficLimiter(OpenFigiProperties properties) { this(properties, Clock.systemUTC()); }
    public TrafficLimiter(OpenFigiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void acquire(UpstreamCredential credential, Category category) {
        long now = clock.millis();
        buckets.values().removeIf(b -> b.lastUsed + 60_000 <= now && b.blockedUntil <= now);
        String id = credential.budgetId() + ":" + category;
        if (!buckets.containsKey(id) && buckets.size() >= MAX_BUCKETS) {
            throw new OpenFigiException("BUSY", "Too many active traffic budgets; retry later", 60L);
        }
        Bucket bucket = buckets.computeIfAbsent(id, ignored -> new Bucket());
        bucket.lastUsed = now;
        if (bucket.blockedUntil > now) rateLimited(bucket.blockedUntil - now);
        long window = credential.keyed() && category == Category.MAPPING ? 6_000 : 60_000;
        int limit = category == Category.MAPPING
                ? (credential.keyed() ? properties.keyedMappingRequests() : properties.keylessMappingRequests())
                : (credential.keyed() ? properties.keyedSearchRequests() : properties.keylessSearchRequests());
        while (!bucket.calls.isEmpty() && bucket.calls.peekFirst() <= now - window) bucket.calls.removeFirst();
        if (bucket.calls.size() >= limit) rateLimited(bucket.calls.peekFirst() + window - now);
        bucket.calls.addLast(now);
    }

    public synchronized void block(UpstreamCredential credential, Category category, long seconds) {
        String id = credential.budgetId() + ":" + category;
        Bucket bucket = buckets.get(id);
        if (bucket != null) {
            bucket.blockedUntil = Math.max(bucket.blockedUntil, clock.millis() + Math.min(3600, Math.max(1, seconds)) * 1000);
        }
    }
    private static void rateLimited(long millis) {
        throw new OpenFigiException("RATE_LIMITED", "OpenFIGI traffic budget exhausted; retry later",
                Math.max(1, (millis + 999) / 1000));
    }
    private static final class Bucket {
        final ArrayDeque<Long> calls = new ArrayDeque<>();
        long lastUsed;
        long blockedUntil;
    }
}
