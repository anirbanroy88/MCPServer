package io.github.anirbanroy88.mcp.openfigi.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import io.modelcontextprotocol.common.McpTransportContext;
import io.github.anirbanroy88.mcp.openfigi.client.UpstreamCredential;
import static org.assertj.core.api.Assertions.*;

class CredentialResolverTest {
    @Test void httpIgnoresTheProcessCredential() {
        var environment = new MockEnvironment().withProperty("OPENFIGI_API_KEY", "process-secret");
        environment.setActiveProfiles("http");
        var resolver = new CredentialResolver(environment);
        assertThat(resolver.resolve(McpTransportContext.EMPTY).keyed()).isFalse();
        var context = McpTransportContext.create(Map.of(CredentialResolver.CONTEXT_KEY, UpstreamCredential.of("request-secret")));
        assertThat(resolver.resolve(context).headerValue()).isEqualTo("request-secret");
        assertThat(resolver.resolve(McpTransportContext.EMPTY).keyed()).isFalse();
    }
    @Test void profilesAreMutuallyExclusive() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("http", "stdio");
        assertThatThrownBy(() -> new CredentialResolver(environment)).isInstanceOf(IllegalStateException.class);
    }
}
