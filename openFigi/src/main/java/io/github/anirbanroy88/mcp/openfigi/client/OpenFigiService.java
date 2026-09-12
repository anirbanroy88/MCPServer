package io.github.anirbanroy88.mcp.openfigi.client;

import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import io.github.anirbanroy88.mcp.openfigi.config.OpenFigiProperties;
import io.github.anirbanroy88.mcp.openfigi.model.Filters;
import io.github.anirbanroy88.mcp.openfigi.model.MappingRequest;
import io.github.anirbanroy88.mcp.openfigi.model.QueryRequest;

@Service
public final class OpenFigiService {
    private static final Set<String> VALUE_KEYS = Set.of("idType", "exchCode", "micCode", "currency",
            "marketSecDes", "securityType", "securityType2", "stateCode");
    private final OpenFigiClient client;
    private final ObjectMapper mapper;
    private final OpenFigiProperties properties;

    public OpenFigiService(OpenFigiClient client, ObjectMapper mapper, OpenFigiProperties properties) {
        this.client = client; this.mapper = mapper; this.properties = properties;
    }
    public JsonNode mapping(MappingRequest request, UpstreamCredential credential) {
        int maximum = credential.keyed() ? properties.keyedBatchSize() : properties.keylessBatchSize();
        if (request == null || request.jobs() == null || request.jobs().isEmpty() || request.jobs().size() > maximum)
            throw OpenFigiException.invalid("jobs must contain between 1 and " + maximum + " mapping jobs");
        var payload = mapper.createArrayNode();
        for (var job : request.jobs()) {
            if (job == null) throw OpenFigiException.invalid("Mapping jobs cannot be null");
            requireText(job.idType(), 128, "idType");
            requireText(job.idValue(), 512, "idValue");
            ObjectNode data = filters(job.filters());
            if (Set.of("BASE_TICKER", "ID_EXCH_SYMBOL").contains(job.idType())
                    && (job.filters() == null || job.filters().securityType2() == null))
                throw OpenFigiException.invalid("securityType2 is required for BASE_TICKER or ID_EXCH_SYMBOL");
            data.put("idType", job.idType()).put("idValue", job.idValue());
            payload.add(data);
        }
        JsonNode response = client.exchange("/v3/mapping", payload, credential, TrafficLimiter.Category.MAPPING);
        if (!response.isArray() || response.size() != request.jobs().size())
            throw new OpenFigiException("UPSTREAM_RESPONSE", "OpenFIGI mapping response does not match the submitted jobs");
        return mapper.createObjectNode().set("results", response);
    }

    public JsonNode query(QueryRequest request, UpstreamCredential credential, boolean filter) {
        if (request == null) throw OpenFigiException.invalid("Query input is required");
        ObjectNode payload = filters(request.filters());
        if (request.query() != null) {
            requireText(request.query(), 1000, "query");
            payload.put("query", request.query());
        }
        if (request.start() != null) {
            requireText(request.start(), 8192, "start");
            payload.put("start", request.start());
        }
        JsonNode response = client.exchange(filter ? "/v3/filter" : "/v3/search", payload, credential, TrafficLimiter.Category.SEARCH);
        if (!response.isObject()) throw new OpenFigiException("UPSTREAM_RESPONSE", "OpenFIGI query response must be an object");
        return response;
    }

    public JsonNode values(String key, UpstreamCredential credential) {
        if (key == null || !VALUE_KEYS.contains(key)) throw OpenFigiException.invalid("Unsupported mapping value key");
        JsonNode response = client.exchange("/v3/mapping/values/" + key, null, credential, TrafficLimiter.Category.MAPPING);
        if (!response.isObject()) throw new OpenFigiException("UPSTREAM_RESPONSE", "OpenFIGI values response must be an object");
        return response;
    }
    private ObjectNode filters(Filters filters) {
        if (filters == null) return mapper.createObjectNode();
        filters.validate();
        return mapper.valueToTree(filters);
    }
    private static void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max)
            throw OpenFigiException.invalid(field + " must be nonblank and at most " + max + " characters");
    }
}
