package io.github.anirbanroy88.mcp.openfigi.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;

class AccessConfigurationTest {
    private static Jwt token(String issuer, String audience, Instant expires) {
        return Jwt.withTokenValue("test").header("alg", "RS256").issuer(issuer)
                .audience(List.of(audience)).subject("personal-user")
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(expires).build();
    }
    @Test void validatesAudienceIssuerAndExpiry() {
        var validator = AccessConfiguration.jwtValidator("https://issuer.example", "https://mcp.example/mcp");
        assertThat(validator.validate(token("https://issuer.example", "https://mcp.example/mcp",
                Instant.now().plusSeconds(600))).hasErrors()).isFalse();
        assertThat(validator.validate(token("https://issuer.example", "other-resource",
                Instant.now().plusSeconds(600))).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://wrong.example", "https://mcp.example/mcp",
                Instant.now().plusSeconds(600))).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://issuer.example", "https://mcp.example/mcp",
                Instant.now().minusSeconds(600))).hasErrors()).isTrue();
    }
    @Test void missingPersonalTokenFailsClosed() {
        assertThatThrownBy(() -> AccessConfiguration.validate(new AccessProperties(
                AccessProperties.Mode.TOKEN, "", "", "", "", Set.of(), Set.of())))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void metadataMatchesTheConfiguredResourceAndIssuer() {
        var properties = new AccessProperties(AccessProperties.Mode.OAUTH, "", "https://issuer.example",
                "https://mcp.example/mcp", "https://mcp.example/mcp", Set.of("me"), Set.of());
        AccessConfiguration.validate(properties);
        var metadata = new AccessConfiguration.ResourceMetadata(properties).metadata();
        assertThat(metadata).containsEntry("resource", "https://mcp.example/mcp")
                .containsEntry("authorization_servers", List.of("https://issuer.example"));
        assertThat(AccessConfiguration.metadataUrl(properties))
                .isEqualTo("https://mcp.example/.well-known/oauth-protected-resource/mcp");
    }
}
