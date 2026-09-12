package io.github.anirbanroy88.mcp.openfigi.config;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.github.anirbanroy88.mcp.openfigi.client.UpstreamCredential;

@Configuration(proxyBeanMethods = false)
@Profile("http")
public class TransportConfiguration {
    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(ObjectMapper mapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .mcpEndpoint("/mcp")
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        CredentialResolver.CONTEXT_KEY,
                        UpstreamCredential.of(request.headers().firstHeader("X-OpenFIGI-API-Key")))))
                .build();
    }
}
