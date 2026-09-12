package io.github.anirbanroy88.mcp.openfigi.security;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("mcp.security")
public record AccessProperties(
        @DefaultValue("token") Mode mode, @DefaultValue("") String token,
        @DefaultValue("") String issuer, @DefaultValue("") String audience,
        @DefaultValue("") String publicUrl, Set<String> allowedSubjects, Set<String> allowedOrigins) {
    public enum Mode { TOKEN, OAUTH, NONE }
    public AccessProperties {
        allowedSubjects = clean(allowedSubjects);
        allowedOrigins = clean(allowedOrigins);
    }
    private static Set<String> clean(Set<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override public String toString() { return "AccessProperties[mode=" + mode + ", credentials=redacted]"; }
}
