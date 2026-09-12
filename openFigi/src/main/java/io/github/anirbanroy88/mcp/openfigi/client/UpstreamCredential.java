package io.github.anirbanroy88.mcp.openfigi.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Request/process scoped secret. Deliberately not a record: toString never exposes the value. */
public final class UpstreamCredential {
    private final String value;
    private UpstreamCredential(String value) { this.value = value; }

    public static UpstreamCredential of(String raw) {
        if (raw == null || raw.isBlank()) return new UpstreamCredential(null);
        String value = raw.trim();
        if (value.length() > 4096 || value.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw OpenFigiException.invalid("OpenFIGI key must contain printable non-whitespace ASCII characters");
        }
        return new UpstreamCredential(value);
    }
    public boolean keyed() { return value != null; }
    public String headerValue() { return value; }
    public String budgetId() {
        if (!keyed()) return "keyless";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
    @Override public String toString() { return keyed() ? "UpstreamCredential[redacted]" : "UpstreamCredential[keyless]"; }
}
