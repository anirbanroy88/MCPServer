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
    @Test void validatesGoogleOidcTokenWithAudienceOrAzp() {
        var validator = AccessConfiguration.jwtValidator("https://accounts.google.com", "", "google-client-id-123");

        var validAudToken = Jwt.withTokenValue("test").header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of("google-client-id-123"))
                .subject("1234567890")
                .claim("email", "user@gmail.com")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(600)).build();
        assertThat(validator.validate(validAudToken).hasErrors()).isFalse();

        var validAzpToken = Jwt.withTokenValue("test").header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .claim("azp", "google-client-id-123")
                .audience(List.of("other-client-id"))
                .subject("1234567890")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(600)).build();
        assertThat(validator.validate(validAzpToken).hasErrors()).isFalse();

        var wrongClientToken = Jwt.withTokenValue("test").header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of("wrong-client-id"))
                .subject("1234567890")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(600)).build();
        assertThat(validator.validate(wrongClientToken).hasErrors()).isTrue();
    }
    @Test void missingPersonalTokenFailsClosed() {
        assertThatThrownBy(() -> AccessConfiguration.validate(new AccessProperties(
                AccessProperties.Mode.TOKEN, "", "", "", "", "", "", "", Set.of(), Set.of())))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void clientSecretIsRedactedInToString() {
        var properties = new AccessProperties(AccessProperties.Mode.OAUTH, "super-secret-token", "https://accounts.google.com",
                "google-client-id", "https://mcp.example/mcp", "google-client-id", "top-secret-client-secret",
                "https://mcp.example/login/oauth2/code/google", Set.of("me@gmail.com"), Set.of());
        assertThat(properties.toString())
                .doesNotContain("super-secret-token", "top-secret-client-secret")
                .contains("credentials=redacted");
        assertThat(properties.clientId()).isEqualTo("google-client-id");
        assertThat(properties.clientSecret()).isEqualTo("top-secret-client-secret");
        assertThat(properties.redirectUri()).isEqualTo("https://mcp.example/login/oauth2/code/google");
    }
    @Test void metadataMatchesTheConfiguredResourceAndIssuer() {
        var properties = new AccessProperties(AccessProperties.Mode.OAUTH, "", "https://issuer.example",
                "https://mcp.example/mcp", "https://mcp.example/mcp", "", "", "", Set.of("me"), Set.of());
        AccessConfiguration.validate(properties);
        var metadata = new AccessConfiguration.ResourceMetadata(properties).metadata();
        assertThat(metadata).containsEntry("resource", "https://mcp.example/mcp")
                .containsEntry("authorization_servers", List.of("https://issuer.example"));
        assertThat(AccessConfiguration.metadataUrl(properties))
                .isEqualTo("https://mcp.example/.well-known/oauth-protected-resource/mcp");
    }
}
