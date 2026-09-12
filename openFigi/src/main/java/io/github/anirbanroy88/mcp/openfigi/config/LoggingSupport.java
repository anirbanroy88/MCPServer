package io.github.anirbanroy88.mcp.openfigi.config;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class LoggingSupport {
    private static final String REQUEST_ID = "requestId";
    private static final Pattern SECRET = Pattern.compile(
            "(?i)([\"']?(?:api[-_ ]?key|authorization|bearer|token|password)[\"']?)(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,\\s}]+(?:\\s+[^,\\s}]+)*)",
            Pattern.MULTILINE);

    private LoggingSupport() {}

    public static String beginRequest() {
        String requestId = UUID.randomUUID().toString();
        MDC.put(REQUEST_ID, requestId);
        return requestId;
    }

    public static void clearRequest() {
        MDC.remove(REQUEST_ID);
    }

    public static String sanitize(String value) {
        return value == null ? "" : SECRET.matcher(value).replaceAll("$1$2[REDACTED]");
    }

    public static String sanitizedStackTrace(Throwable failure) {
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return sanitize(output.toString());
    }
}