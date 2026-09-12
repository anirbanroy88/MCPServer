## Purpose

Make the same tools accessible to local and remote MCP clients without persisting upstream credentials.

## ADDED Requirements

### Requirement: Two launch modes
The application SHALL provide mutually exclusive `stdio` and `http` profiles. STDIO SHALL reserve stdout for MCP protocol messages and open no listening HTTP port. HTTP SHALL expose Streamable HTTP at `/mcp` and support HTTPS termination at a reverse proxy.

#### Scenario: Local client starts the process
- **WHEN** a client launches the JAR with the STDIO profile
- **THEN** it can initialize, discover tools, and call tools over stdin/stdout without log contamination

#### Scenario: Remote tool invocation
- **WHEN** an authorized HTTP client initializes and invokes a tool at `/mcp`
- **THEN** the same tool definitions and behavior are available as in STDIO mode

### Requirement: Optional request credentials
STDIO SHALL obtain an optional key from `OPENFIGI_API_KEY`. HTTP SHALL obtain it only from `X-OpenFIGI-API-Key` on each request and SHALL NOT use a process-level OpenFIGI key. Missing or blank keys SHALL select keyless upstream access. Keys SHALL NOT be persisted, included in tool schemas, returned to clients, or logged.

#### Scenario: Credentials cannot leak across requests
- **WHEN** one HTTP call supplies a key and a later or concurrent call omits it
- **THEN** only the first call uses that key and the other call uses keyless access

### Requirement: Request and exception logging
Every STDIO and HTTP request SHALL produce sanitized request-start and request-completion logs containing a correlation identifier, operation or endpoint, outcome, and duration. Every exception SHALL be logged with its type, sanitized context, and stack trace before the exception is returned or propagated. Logs SHALL exclude API keys, bearer tokens, authorization headers, and raw credential-bearing request data.

#### Scenario: HTTP request is completed
- **WHEN** an authorized HTTP client invokes a tool successfully or unsuccessfully
- **THEN** the request start and completion, outcome, status or stable error code, and duration are logged under one correlation identifier

#### Scenario: Request handling throws
- **WHEN** transport, authentication, validation, or tool execution raises an exception
- **THEN** the exception and stack trace are logged once with sanitized request context and the client receives the normal safe error response

### Requirement: Explicit hosted access policy
HTTP SHALL require configured bearer-token or OAuth JWT authentication by default. Anonymous access SHALL require explicit configuration. OAuth mode SHALL validate issuer and audience, support a subject allowlist for personal use, and publish protected-resource discovery metadata. Requests with unapproved Origin headers SHALL be rejected.

#### Scenario: Missing personal credentials
- **WHEN** an HTTP call lacks the configured personal bearer token
- **THEN** the server rejects it before invoking tools

#### Scenario: OAuth token is for another resource
- **WHEN** a JWT has the wrong audience
- **THEN** access is denied

### Requirement: Honest client compatibility
Documentation SHALL distinguish verified protocol tests from live client validation, explain custom-header requirements for keyed HTTP access, and avoid claiming native STDIO or custom-header support for unverified clients.

#### Scenario: Client cannot send custom headers
- **WHEN** a user configures such a client
- **THEN** documentation directs them to keyless access and a compatible hosted authentication method
