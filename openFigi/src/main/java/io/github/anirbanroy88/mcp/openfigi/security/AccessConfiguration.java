package io.github.anirbanroy88.mcp.openfigi.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@Profile("http")
public class AccessConfiguration {
    @Bean
    SecurityFilterChain accessFilterChain(HttpSecurity http, AccessProperties properties, ObjectProvider<JwtDecoder> decoders) throws Exception {
        validate(properties);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new RequestGuard(properties), BearerTokenAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> challenge(response, properties)))
                .authorizeHttpRequests(auth -> {
                    if (properties.mode() == AccessProperties.Mode.OAUTH) {
                        auth.requestMatchers("/.well-known/oauth-protected-resource",
                                "/.well-known/oauth-protected-resource/mcp").permitAll();
                    }
                    if (properties.mode() == AccessProperties.Mode.NONE) auth.requestMatchers("/mcp").permitAll();
                    else auth.requestMatchers("/mcp").access((authentication, context) -> {
                        var principal = authentication.get();
                        if (principal == null || !principal.isAuthenticated()) {
                            return new AuthorizationDecision(false);
                        }
                        boolean isGoogle = isGoogleIssuer(properties.issuer());
                        boolean scope = isGoogle || principal.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("SCOPE_mcp:tools"));

                        boolean subject;
                        if (properties.allowedSubjects().isEmpty()) {
                            subject = true;
                        } else {
                            String name = principal.getName();
                            String email = null;
                            if (principal.getPrincipal() instanceof Jwt jwt) {
                                email = jwt.getClaimAsString("email");
                            }
                            subject = properties.allowedSubjects().contains(name)
                                    || (email != null && properties.allowedSubjects().contains(email));
                        }
                        return new AuthorizationDecision(scope && subject);
                    });
                    auth.anyRequest().denyAll();
                });
        if (properties.mode() == AccessProperties.Mode.OAUTH) {
            http.oauth2ResourceServer(oauth -> oauth
                    .authenticationEntryPoint((request, response, error) -> challenge(response, properties))
                    .jwt(jwt -> jwt.decoder(decoders.getObject())));
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "mcp.security.mode", havingValue = "oauth")
    JwtDecoder jwtDecoder(AccessProperties properties) {
        validate(properties);
        NimbusJwtDecoder decoder;
        if (isGoogleIssuer(properties.issuer())) {
            decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        } else {
            decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuer()).build();
        }
        decoder.setJwtValidator(jwtValidator(properties.issuer(), properties.audience(), properties.clientId()));
        return decoder;
    }

    public static boolean isGoogleIssuer(String issuer) {
        return "https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer);
    }

    public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience) {
        return jwtValidator(issuer, audience, "");
    }

    public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience, String clientId) {
        OAuth2TokenValidator<Jwt> customValidator = token -> {
            if (token.getExpiresAt() == null || token.getSubject() == null || token.getSubject().isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token is missing subject or expiry", null));
            }

            String tokenIssuer = "";
            if (token.getIssuer() != null) {
                tokenIssuer = token.getIssuer().toString();
            } else if (token.getClaim("iss") != null) {
                tokenIssuer = token.getClaim("iss").toString();
            }

            boolean validIssuer;
            if (isGoogleIssuer(issuer)) {
                validIssuer = isGoogleIssuer(tokenIssuer);
            } else {
                validIssuer = issuer.equals(tokenIssuer);
            }
            if (!validIssuer) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token issuer is invalid", null));
            }

            String targetAud = !audience.isBlank() ? audience : clientId;
            boolean audMatches = token.getAudience() != null && token.getAudience().contains(targetAud);
            boolean azpMatches = !clientId.isBlank() && clientId.equals(token.getClaimAsString("azp"));
            boolean directClientIdMatches = !clientId.isBlank() && token.getAudience() != null && token.getAudience().contains(clientId);

            if (!audMatches && !azpMatches && !directClientIdMatches) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token audience is invalid", null));
            }

            return OAuth2TokenValidatorResult.success();
        };

        if (isGoogleIssuer(issuer)) {
            return customValidator;
        }
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), customValidator);
    }

    static void validate(AccessProperties properties) {
        if (properties.mode() == AccessProperties.Mode.TOKEN
                && (properties.token() == null || properties.token().length() < 32 || properties.token().isBlank()))
            throw new IllegalStateException("Token mode requires MCP_ACCESS_TOKEN with at least 32 characters");
        if (properties.mode() == AccessProperties.Mode.OAUTH) {
            requireHttps(properties.issuer(), "MCP_OAUTH_ISSUER");
            requireHttps(properties.publicUrl(), "MCP_PUBLIC_URL");
            if (!URI.create(properties.publicUrl()).getPath().equals("/mcp"))
                throw new IllegalStateException("MCP_PUBLIC_URL must end with /mcp");
            if (!isGoogleIssuer(properties.issuer()) && !properties.publicUrl().equals(properties.audience()))
                throw new IllegalStateException("MCP_OAUTH_AUDIENCE must equal MCP_PUBLIC_URL");
            if (isGoogleIssuer(properties.issuer()) && properties.audience().isBlank() && properties.clientId().isBlank())
                throw new IllegalStateException("Google OAuth mode requires MCP_OAUTH_AUDIENCE or MCP_OAUTH_CLIENT_ID");
        }
    }
    private static void requireHttps(String value, String name) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (Exception invalid) { throw new IllegalStateException(name + " must be an HTTPS URL without credentials or query"); }
    }
    static String metadataUrl(AccessProperties properties) {
        return URI.create(properties.publicUrl()).resolve("/.well-known/oauth-protected-resource/mcp").toString();
    }
    static void challenge(HttpServletResponse response, AccessProperties properties) {
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", properties.mode() == AccessProperties.Mode.OAUTH
                ? "Bearer resource_metadata=\"" + metadataUrl(properties) + "\", scope=\"mcp:tools\""
                : "Bearer realm=\"openfigi-mcp\"");
    }

    private static final class RequestGuard extends OncePerRequestFilter {
        private final AccessProperties properties;
        RequestGuard(AccessProperties properties) { this.properties = properties; }

        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            var origins = Collections.list(request.getHeaders("Origin"));
            if (origins.size() > 1 || (!origins.isEmpty() && !properties.allowedOrigins().contains(origins.getFirst()))) {
                response.setStatus(403);
                return;
            }
            if ("/mcp".equals(request.getServletPath())) {
                var keys = Collections.list(request.getHeaders("X-OpenFIGI-API-Key"));
                if (keys.size() > 1 || (!keys.isEmpty() && (keys.getFirst().length() > 4096
                        || keys.getFirst().chars().anyMatch(c -> c < 32 || c > 126)))) {
                    response.setStatus(400);
                    return;
                }
                if (properties.mode() == AccessProperties.Mode.TOKEN) {
                    var authorization = Collections.list(request.getHeaders("Authorization"));
                    byte[] expected = ("Bearer " + properties.token()).getBytes(StandardCharsets.UTF_8);
                    if (authorization.size() != 1 || !MessageDigest.isEqual(expected,
                            authorization.getFirst().getBytes(StandardCharsets.UTF_8))) {
                        challenge(response, properties);
                        return;
                    }
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken("personal", null,
                            List.of(new SimpleGrantedAuthority("SCOPE_mcp:tools"))));
                    SecurityContextHolder.setContext(context);
                }
            }
            chain.doFilter(request, response);
        }
    }

    @RestController
    @Profile("http")
    @ConditionalOnProperty(name = "mcp.security.mode", havingValue = "oauth")
    public static class ResourceMetadata {
        private final AccessProperties properties;
        public ResourceMetadata(AccessProperties properties) { this.properties = properties; }
        @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
        public Map<String, Object> metadata() {
            return Map.of("resource", properties.publicUrl(), "authorization_servers", List.of(properties.issuer()),
                    "scopes_supported", List.of("mcp:tools"), "bearer_methods_supported", List.of("header"));
        }
    }
}
