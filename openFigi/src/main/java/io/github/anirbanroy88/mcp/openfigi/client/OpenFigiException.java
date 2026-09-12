package io.github.anirbanroy88.mcp.openfigi.client;

/** Contains only safe, application-owned messages; never attach an upstream exception. */
public final class OpenFigiException extends RuntimeException {
    private final String code;
    private final Long retryAfterSeconds;

    public OpenFigiException(String code, String message) { this(code, message, null); }
    public OpenFigiException(String code, String message, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
    public static OpenFigiException invalid(String message) {
        return new OpenFigiException("INVALID_INPUT", message);
    }
}
