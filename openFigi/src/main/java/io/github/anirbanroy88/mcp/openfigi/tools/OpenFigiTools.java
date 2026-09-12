package io.github.anirbanroy88.mcp.openfigi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.github.anirbanroy88.mcp.openfigi.client.OpenFigiException;
import io.github.anirbanroy88.mcp.openfigi.client.OpenFigiService;
import io.github.anirbanroy88.mcp.openfigi.client.UpstreamCredential;
import io.github.anirbanroy88.mcp.openfigi.config.CredentialResolver;
import io.github.anirbanroy88.mcp.openfigi.config.LoggingSupport;
import io.github.anirbanroy88.mcp.openfigi.model.MappingRequest;
import io.github.anirbanroy88.mcp.openfigi.model.QueryRequest;
import io.github.anirbanroy88.mcp.openfigi.model.ValuesRequest;

@Configuration(proxyBeanMethods = false)
public class OpenFigiTools {
        private static final Logger log = LoggerFactory.getLogger(OpenFigiTools.class);

    @Bean
    List<McpServerFeatures.SyncToolSpecification> openFigiToolSpecifications(
            ObjectMapper mapper, OpenFigiService service, CredentialResolver credentials) throws IOException {
        ObjectMapper inputs = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return List.of(
                tool(inputs, credentials, "openfigi_mapping",
                        "Map identifiers such as ISIN, CUSIP or ticker to FIGIs. All matches are returned in job order. "
                                + "Use filters to disambiguate. Maximum 5 jobs without a key, 100 with a key.",
                        MappingRequest.class, service::mapping),
                tool(inputs, credentials, "openfigi_search",
                        "Search instruments by keywords and optional filters. Returns one page; pass next as start to continue.",
                        QueryRequest.class, (request, key) -> service.query(request, key, false)),
                tool(inputs, credentials, "openfigi_filter",
                        "Discover instruments using filters, with optional keywords. Returns one page in FIGI order and a continuation token.",
                        QueryRequest.class, (request, key) -> service.query(request, key, true)),
                tool(inputs, credentials, "openfigi_values",
                        "List valid values for idType, exchCode, micCode, currency, marketSecDes, securityType, securityType2 or stateCode.",
                        ValuesRequest.class, (request, key) -> service.values(request.key(), key)));
    }

    private <T> McpServerFeatures.SyncToolSpecification tool(ObjectMapper mapper, CredentialResolver credentials,
            String name, String description, Class<T> inputType,
            BiFunction<T, UpstreamCredential, JsonNode> operation) throws IOException {
        String schema = new ClassPathResource("schemas/" + name + ".json").getContentAsString(StandardCharsets.UTF_8);
        var definition = McpSchema.Tool.builder().name(name).description(description)
                .inputSchema(new JacksonMcpJsonMapper(mapper), schema)
                .annotations(new McpSchema.ToolAnnotations(null, true, false, true, true, false))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder().tool(definition).callHandler((exchange, request) -> {
                        String requestId = LoggingSupport.beginRequest();
                        long started = System.nanoTime();
                        String requestParameters = serialize(mapper, request.arguments());
                        log.info("mcp.request tool={} requestParameters={} requestId={}", name, requestParameters, requestId);
            try {
                T input;
                try { input = mapper.convertValue(request.arguments(), inputType); }
                catch (IllegalArgumentException malformed) { throw OpenFigiException.invalid("Arguments do not match the tool input schema"); }
                JsonNode data = operation.apply(input, credentials.resolve(exchange.transportContext()));
                                var result = McpSchema.CallToolResult.builder().structuredContent(mapper.convertValue(data, Object.class))
                        .addTextContent(data.toString()).isError(false).build();
                                log.info("mcp.response tool={} responseParameters={} outcome=success durationMs={} requestId={}", name,
                                                serialize(mapper, data), elapsedMillis(started), requestId);
                                return result;
            } catch (OpenFigiException safe) {
                var error = mapper.createObjectNode().put("code", safe.code()).put("message", safe.getMessage());
                if (safe.retryAfterSeconds() != null) error.put("retryAfterSeconds", safe.retryAfterSeconds());
                var body = mapper.createObjectNode().set("error", error);
                                log.warn("mcp.response tool={} responseParameters={} outcome=error durationMs={} requestId={}", name,
                                                serialize(mapper, body), elapsedMillis(started), requestId);
                                log.error("mcp.exception tool={} type={} code={} requestId={} stackTrace={}", name,
                                                safe.getClass().getSimpleName(), safe.code(), requestId, LoggingSupport.sanitizedStackTrace(safe));
                return McpSchema.CallToolResult.builder().addTextContent(body.toString()).isError(true).build();
            } catch (Exception unexpected) {
                                var body = mapper.createObjectNode().putObject("error")
                                                .put("code", "INTERNAL_ERROR").put("message", "Tool execution failed");
                                log.error("mcp.response tool={} responseParameters={} outcome=error durationMs={} requestId={}", name,
                                                serialize(mapper, body), elapsedMillis(started), requestId);
                                log.error("mcp.exception tool={} type={} code=INTERNAL_ERROR requestId={} stackTrace={}", name,
                                                unexpected.getClass().getSimpleName(), requestId, LoggingSupport.sanitizedStackTrace(unexpected));
                                return McpSchema.CallToolResult.builder().addTextContent(body.toString()).isError(true).build();
                        } finally {
                                LoggingSupport.clearRequest();
            }
        }).build();
    }

        private static String serialize(ObjectMapper mapper, Object value) {
                try {
                        return LoggingSupport.sanitize(mapper.writeValueAsString(value));
                } catch (Exception serializationFailure) {
                        return "[UNSERIALIZABLE_PARAMETERS]";
                }
        }

        private static long elapsedMillis(long started) {
                return (System.nanoTime() - started) / 1_000_000;
        }
}
