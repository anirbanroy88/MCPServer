## 1. Build foundation

- [x] 1.1 Create Java 21 Gradle Kotlin multi-project build, central versions, wrapper and OpenFIGI application; verify wrapper `:openFigi:bootJar` succeeds.

## 2. OpenFIGI behavior

- [x] 2.1 Implement typed mapping/query inputs and four tools with validation and structured results; verify mock-upstream tests cover ordering, pagination, filters and invalid batches.
- [x] 2.2 Implement bounded HTTP calls, safe error mapping and credential-aware rate budgets; verify tests cover 401, 429, transient failures, timeouts, size limits and budget isolation.

## 3. MCP transports and hosted access

- [x] 3.1 Implement exclusive STDIO/HTTP profiles and transient per-request credentials; verify real MCP client tests initialize, list and call tools in both modes and prove sequential/concurrent credential isolation.
- [x] 3.2 Implement personal bearer and OAuth JWT access policies, resource metadata and Origin checks; verify unauthorized access, wrong audience and rejected origins in automated tests.

## 4. Delivery and verification

- [x] 4.1 Add container/HTTPS examples, client configurations and README with sourced compatibility limits and no embedded secrets; verify example syntax and document deployment prerequisites.
- [ ] 4.2 Add Java 21 CI and execute the complete clean build plus strict OpenSpec validation; record actual results and any live-client or deployment limitations.

## 5. Logging and diagnostics

- [ ] 5.1 Add SLF4J and Logback dependencies/configuration, including a configurable log directory defaulting to `C:\Users\anirb\log\mcpServer` and rotation/retention appropriate for local operation.
- [ ] 5.2 Add sanitized correlation-aware request start/completion logging for STDIO and HTTP, ensuring STDIO stdout remains dedicated to MCP protocol messages.
- [ ] 5.3 Add method-level logging for tool, validation, rate-limit, upstream-client, and response-mapping paths, and log every exception with stack traces before safe error handling.
- [ ] 5.4 Add redaction and tests proving API keys, bearer tokens, authorization headers, and sensitive request/upstream values never appear in logs; verify successful, rejected, and exceptional requests produce the required records.
