## Context

See proposal.md for motivation. The workspace has no application source, build files, or Git checkout. Java 21 is installed. The user explicitly authorized proposal, tasks, and implementation together. Each server is one independently runnable Gradle subproject.

## Goals / Non-Goals

Goals: share tools between STDIO and HTTP; keep OpenFIGI keys transient; make upstream behavior bounded and testable; prepare HTTPS hosting with an explicit access policy.

Non-goals: operating a public deployment, publishing to GitHub, implementing an identity provider, storing user API keys, adding a database or LLM dependency, or claiming live certification with desktop/cloud clients.

## Decisions

- Use Spring Boot 3.5.16 and Spring AI 1.1.8 with Java 21 and Gradle 8.14.3. These are published stable versions on the Boot 3 / AI 1.1 line. Centralize versions in a catalog and the AI BOM. Avoid snapshots and a premature shared runtime module.
- Use one executable JAR with mutually exclusive `stdio` (default) and `http` profiles. Spring AI's synchronous server registers shared tools. HTTP uses the Spring MVC Streamable HTTP transport. Carry credentials through the SDK transport context, not servlet thread locals, because tool execution can cross threads.
- Use typed Java records for inputs, preserving OpenFIGI response JSON in structured MCP results. Mapping returns all jobs and matches, and search/filter return one page per call. Expose four tools including filter, a low-cost addition using the same query model.
- Use an optional `OPENFIGI_API_KEY` only in STDIO. HTTP extracts `X-OpenFIGI-API-Key` on every request. Blank means keyless. Never fall back to the process key in HTTP. No key in arguments, URLs, persistence, or exception messages. Hash credentials only for short-lived rate counters.
- Use an HTTP client with no redirects, explicit connect/read timeouts, response-size limits, bounded concurrency, and at most one retry for transient 5xx failures. Return stable MCP errors for invalid input, upstream authentication, quota, timeout, and bad responses. Never retry 401 or automatically wait out 429.
- Use per-process rate budgets: 25/minute mapping and 5/minute search/filter without a key; 25/6 seconds mapping and 20/minute search/filter per key. Values share the mapping budget conservatively. Honor upstream rate-limit reset guidance. Mapping defaults to 5 keyless jobs due to conflicting official documentation (general table says 10, endpoint section says 5). Configured limits cannot exceed these chosen safe caps.
- Use SLF4J APIs with Logback as the implementation. Log application method entry/exit where useful, log every request start and completion with a correlation ID, operation, outcome, status, and duration, and log every exception with its stack trace and sanitized context. Write file logs under `C:\Users\anirb\log\mcpServer`, with configuration allowing the directory to be overridden for other environments. STDIO logging must use the file and/or stderr without writing non-protocol content to stdout. Redact API keys, bearer tokens, authorization headers, and sensitive request values from all log messages; never log raw upstream error bodies when they may contain secrets.
- HTTP defaults to bearer-token access for personal clients; require a nonempty configured token. Provide OAuth JWT resource-server mode with issuer, audience, subject allowlist, and protected-resource metadata for browser/cloud clients. A compatible external authorization server owns login, PKCE, client registration, and token issuance. Explicit `none` mode enables public keyless use; omission never silently enables it. Bind to loopback by default, reject unapproved Origin values, and use a Caddy HTTPS deployment example.
- Document client compatibility by product. VS Code and Kimi Code document headers, while Kimi web is a separate unverified target. ChatGPT and Claude remote connections require compatible hosted authentication and may not offer arbitrary custom headers; keyed use is conditional on client support, otherwise use keyless. Do not introduce an account key store to hide that limitation.

## Risks / Trade-offs

- Per-process quota accounting does not coordinate replicas: deploy one replica initially and add shared counters before scaling horizontally.
- A raw key is necessarily in memory while forwarding a request: avoid logging it, retaining it in sessions, or using it as a map key.
- External OAuth setup requires a domain and provider configuration: include exact environment settings and discovery behavior; do not invent production credentials.
- Stateless tools do not need session data, but Streamable HTTP clients can create transport sessions: credentials must always come from the current request context, verified with sequential and concurrent calls.
- Client features and plan availability change: cite official documentation and distinguish protocol integration tests from actual app sign-in testing.

## Migration Plan

Build and test locally, then use the STDIO JAR directly or build the container. Configure an HTTPS hostname and an access mode before running the deployment example. No existing data migration is needed. Rollback consists of stopping the new process/container. No deployment or Git push is included in this change.
