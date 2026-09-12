package io.github.anirbanroy88.mcp.openfigi.model;

/** One upstream page; pass the returned next value as start for the following page. */
public record QueryRequest(String query, String start, Filters filters) {}
