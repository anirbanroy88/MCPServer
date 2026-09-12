package io.github.anirbanroy88.mcp.openfigi.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import io.modelcontextprotocol.common.McpTransportContext;
import io.github.anirbanroy88.mcp.openfigi.client.UpstreamCredential;

@Component
public final class CredentialResolver {
    public static final String CONTEXT_KEY = "openfigi.credential";
    private final boolean http;
    private final UpstreamCredential local;

    public CredentialResolver(Environment environment) {
        http = environment.matchesProfiles("http");
        boolean stdio = environment.matchesProfiles("stdio");
        if (http == stdio) throw new IllegalStateException("Select exactly one profile: stdio or http");
        local = http ? UpstreamCredential.of(null) : UpstreamCredential.of(environment.getProperty("OPENFIGI_API_KEY"));
    }
    public UpstreamCredential resolve(McpTransportContext context) {
        if (!http) return local;
        Object value = context == null ? null : context.get(CONTEXT_KEY);
        return value instanceof UpstreamCredential credential ? credential : UpstreamCredential.of(null);
    }
}
