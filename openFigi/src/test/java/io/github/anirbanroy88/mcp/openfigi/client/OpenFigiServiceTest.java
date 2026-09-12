package io.github.anirbanroy88.mcp.openfigi.client;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.anirbanroy88.mcp.openfigi.MockOpenFigi;
import io.github.anirbanroy88.mcp.openfigi.TestSettings;
import io.github.anirbanroy88.mcp.openfigi.model.MappingRequest;
import io.github.anirbanroy88.mcp.openfigi.model.QueryRequest;
import static org.assertj.core.api.Assertions.*;

class OpenFigiServiceTest {
    private final MockOpenFigi upstream = new MockOpenFigi();
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenFigiService service = service();
    private OpenFigiService service() {
        var properties = TestSettings.properties(upstream.uri());
        return new OpenFigiService(new OpenFigiClient(mapper, properties, new TrafficLimiter(properties)), mapper, properties);
    }
    @AfterEach void close() { upstream.close(); }

    @Test void preservesAllMatchesAndJobWarnings() throws Exception {
        upstream.handler = request -> MockOpenFigi.Reply.json(200,
                "[{\"data\":[{\"figi\":\"A\"},{\"figi\":\"B\"}]},{\"warning\":\"No identifier found.\"}]");
        var request = mapper.readValue("""
                {"jobs":[{"idType":"TICKER","idValue":"IBM","filters":{"exchCode":"US"}},
                {"idType":"ID_ISIN","idValue":"unknown"}]}
                """, MappingRequest.class);
        var result = service.mapping(request, UpstreamCredential.of("test-key"));
        assertThat(result.at("/results/0/data")).hasSize(2);
        assertThat(result.at("/results/1/warning").asText()).isEqualTo("No identifier found.");
        var sent = upstream.requests.element();
        assertThat(sent.key()).isEqualTo("test-key");
        assertThat(sent.path()).isEqualTo("/v3/mapping");
        assertThat(mapper.readTree(sent.body()).get(0).has("filters")).isFalse();
        assertThat(mapper.readTree(sent.body()).at("/0/exchCode").asText()).isEqualTo("US");
    }

    @Test void returnsOnePageAndForwardsContinuation() {
        upstream.handler = request -> MockOpenFigi.Reply.json(200, "{\"data\":[],\"next\":\"opaque-next\",\"total\":42}");
        var result = service.query(new QueryRequest("IBM", "opaque-start", null), UpstreamCredential.of(null), true);
        assertThat(result.get("next").asText()).isEqualTo("opaque-next");
        assertThat(upstream.requests).hasSize(1);
        assertThat(upstream.requests.element().path()).isEqualTo("/v3/filter");
        assertThat(upstream.requests.element().body()).contains("opaque-start");
    }

    @Test void rejectsInvalidInputsBeforeNetwork() throws Exception {
        for (String json : List.of(
                "{\"jobs\":[]}",
                "{\"jobs\":[{\"idType\":\"TICKER\",\"idValue\":\"IBM\",\"filters\":{\"exchCode\":\"US\",\"micCode\":\"XNYS\"}}]}",
                "{\"jobs\":[{\"idType\":\"TICKER\",\"idValue\":\"IBM\",\"filters\":{\"strike\":[9,1]}}]}",
                "{\"jobs\":[{\"idType\":\"TICKER\",\"idValue\":\"IBM\",\"filters\":{\"expiration\":[\"2025-01-01\",\"2027-01-01\"]}}]}",
                "{\"jobs\":[{\"idType\":\"BASE_TICKER\",\"idValue\":\"IBM\"}]}")) {
            var input = mapper.readValue(json, MappingRequest.class);
            assertThatThrownBy(() -> service.mapping(input, UpstreamCredential.of(null))).isInstanceOf(OpenFigiException.class);
        }
        assertThatThrownBy(() -> service.values("../../secret", UpstreamCredential.of(null))).isInstanceOf(OpenFigiException.class);
        assertThat(upstream.requests).isEmpty();
    }

    @Test void keylessBatchLimitIsStricterThanKeyed() {
        var jobs = java.util.Collections.nCopies(6, new MappingRequest.Job("TICKER", "IBM", null));
        assertThatThrownBy(() -> service.mapping(new MappingRequest(jobs), UpstreamCredential.of(null)))
                .hasMessageContaining("between 1 and 5");
        upstream.handler = request -> MockOpenFigi.Reply.json(200, "[{},{},{},{},{},{}]");
        assertThat(service.mapping(new MappingRequest(jobs), UpstreamCredential.of("key")).get("results")).hasSize(6);
    }

    @Test void blankKeyUsesFreeAccess() {
        service.values("idType", UpstreamCredential.of("  "));
        assertThat(upstream.requests.element().key()).isNull();
    }
}
