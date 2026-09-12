## Purpose

Expose OpenFIGI identifier mapping and discovery through bounded, predictable MCP tools.

## ADDED Requirements

### Requirement: OpenFIGI operations
The server SHALL expose `openfigi_mapping`, `openfigi_search`, `openfigi_filter`, and `openfigi_values`. Mapping SHALL preserve job order and all matching instruments, warnings, and per-job errors. Search and filter SHALL return one page and preserve the upstream continuation token.

#### Scenario: Mixed mapping results
- **WHEN** OpenFIGI returns matches for one job and a warning for another
- **THEN** both results are returned in their original order without choosing a single match

#### Scenario: Search has another page
- **WHEN** a search response contains `next`
- **THEN** the result includes that token and the server does not automatically fetch the next page

### Requirement: Validate bounded inputs
The server SHALL reject empty mapping batches, missing identifiers, mutually exclusive exchange filters, invalid intervals, unsupported value keys, and oversized batches before upstream calls. Keyless batches SHALL default to at most 5 jobs and keyed batches to 100 jobs, configurable within those upstream-safe defaults.

#### Scenario: Invalid exchange selection
- **WHEN** an input includes both `exchCode` and `micCode`
- **THEN** the tool returns a validation error without contacting OpenFIGI

### Requirement: Bounded traffic and safe errors
The server SHALL enforce separate mapping and search/filter budgets for each upstream credential and a shared keyless budget per server process. It SHALL bound concurrency, response size, timeouts, and transient retries. Errors SHALL have stable codes and SHALL exclude credentials and raw upstream error bodies. Upstream 401 SHALL not trigger keyless fallback; 429 SHALL return retry guidance.

#### Scenario: Upstream rejects a key
- **WHEN** OpenFIGI returns HTTP 401
- **THEN** the client receives an invalid-key error and no keyless retry occurs

#### Scenario: Shared free budget exhausted
- **WHEN** keyless traffic exhausts the deployment's configured budget
- **THEN** further keyless calls are rejected with retry guidance without contacting OpenFIGI

### Requirement: Method and failure logging
OpenFIGI tool, validation, rate-limiting, upstream-client, and response-mapping methods SHALL use SLF4J logging at meaningful entry, completion, and failure points. Every upstream failure, rejected request, and unexpected exception SHALL be logged with correlation ID, operation, stable error code or upstream status, and duration where available. Logs SHALL redact credentials and exclude raw sensitive request values and unsafe upstream bodies.

#### Scenario: Tool call fails
- **WHEN** validation, rate limiting, timeout, upstream authentication, quota, or response parsing rejects a tool call
- **THEN** the failure is logged with sanitized diagnostic context and the caller receives the stable safe error contract
