package io.github.anirbanroy88.mcp.openfigi.model;

import java.util.List;

public record MappingRequest(List<Job> jobs) {
    public record Job(String idType, String idValue, Filters filters) {}
}
