package io.github.anirbanroy88.mcp.openfigi.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.github.anirbanroy88.mcp.openfigi.MockOpenFigi;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mcp.security.mode=oauth", "mcp.security.issuer=https://issuer.example",
        "mcp.security.public-url=https://figi.example/mcp", "mcp.security.audience=https://figi.example/mcp",
        "mcp.security.allowed-subjects=owner", "debug=false"})
@ActiveProfiles("http")
class OAuthAccessTest {
    static final MockOpenFigi UPSTREAM = new MockOpenFigi();
    static final KeyPair KEYS = keys();
    @LocalServerPort int port;
    @MockitoBean JwtDecoder jwtDecoder;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("openfigi.base-url", () -> UPSTREAM.uri().toString());
    }
    @AfterAll static void closeUpstream() { UPSTREAM.close(); }
    @BeforeEach void configureLocalSignedTokenValidation() {
        // Keep signature/claims validation real; substitute only external issuer key discovery.
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(AccessConfiguration.jwtValidator("https://issuer.example", "https://figi.example/mcp"));
        doAnswer(call -> decoder.decode(call.getArgument(0))).when(jwtDecoder).decode(anyString());
        UPSTREAM.requests.clear();
    }
    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    String token(String audience, String subject, String scope, KeyPair keys) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("https://issuer.example").audience(audience)
                .subject(subject).claim("scope", scope).issueTime(Date.from(Instant.now().minusSeconds(30)))
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
    HttpResponse<String> post(String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
            if (token != null) request.header("Authorization", "Bearer " + token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void validSignedOwnerCanCallToolsWithoutAnOpenFigiKey() throws Exception {
        var token = token("https://figi.example/mcp", "owner", "mcp:tools", KEYS);
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp").customizeRequest(builder -> builder.header("Authorization", "Bearer " + token)).build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();
            assertThat(client.callTool(new McpSchema.CallToolRequest("openfigi_values", Map.of("key", "idType"))).isError()).isFalse();
        }
        assertThat(UPSTREAM.requests.element().key()).isNull();
    }
    @Test void rejectsWrongAudienceAndSignature() throws Exception {
        assertThat(post(token("other-resource", "owner", "mcp:tools", KEYS)).statusCode()).isEqualTo(401);
        assertThat(post(token("https://figi.example/mcp", "owner", "mcp:tools", keys())).statusCode()).isEqualTo(401);
        assertThat(UPSTREAM.requests).isEmpty();
    }
    @Test void restrictsSubjectsAndScopes() throws Exception {
        assertThat(post(token("https://figi.example/mcp", "someone-else", "mcp:tools", KEYS)).statusCode()).isEqualTo(403);
        assertThat(post(token("https://figi.example/mcp", "owner", "unrelated", KEYS)).statusCode()).isEqualTo(403);
    }
    @Test void publishesMetadataAndDiscoveryChallenge() throws Exception {
        var unauthorized = post(null);
        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(unauthorized.headers().firstValue("WWW-Authenticate").orElseThrow())
                .contains("https://figi.example/.well-known/oauth-protected-resource/mcp", "mcp:tools");
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + port + "/.well-known/oauth-protected-resource/mcp")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("https://figi.example/mcp", "https://issuer.example", "mcp:tools");
        }
    }
}
