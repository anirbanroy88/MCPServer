## Why

Provide a Java 21 monorepo for independently runnable MCP servers, starting with OpenFIGI identifier lookup. The same OpenFIGI tools must serve local process clients and remote HTTPS clients without storing users' upstream API keys.

## What Changes

- Establish a Gradle Kotlin DSL multi-project build, wrapper, centralized versions, Java 21 toolchain, and CI.
- Add the `openFigi` Spring Boot / Spring AI application with STDIO and Streamable HTTP launch profiles.
- Expose mapping, search, filter, and valid mapping-value discovery tools with typed inputs and structured results.
- Resolve optional OpenFIGI credentials from the local process environment or each HTTP request; default to keyless access and never persist credentials.
- Add bounded upstream traffic, validation, safe errors, and tests for transport behavior and credential isolation.
- Add SLF4J and Logback logging across application methods, requests, and exceptions, writing application logs to `C:\Users\anirb\log\mcpServer` without contaminating STDIO protocol output or exposing credentials.
- Provide container/HTTPS deployment examples, configurable hosted authentication, and accurate client setup documentation.

## Capabilities

### New Capabilities

- `mcp-monorepo`: Reproducible Java 21 multi-module build and independent server packaging.
- `openfigi-tools`: OpenFIGI tools, validation, upstream limits, and error handling.
- `mcp-transports`: Local STDIO and remote Streamable HTTP with request-scoped credentials and hosted access control.

### Modified Capabilities

None.

## Impact

Adds root build files, `openFigi` application and tests, CI, deployment examples, and user documentation. External dependencies include Spring Boot, Spring AI, the MCP Java SDK, and the OpenFIGI HTTPS API. No database, model-provider API, or account key store is needed. The workspace currently contains OpenSpec scaffolding but no application source or root Git metadata; this change creates reviewable local files and does not publish or deploy the repository.
