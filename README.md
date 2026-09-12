# MCPServer

Java 21 MCP servers built with Spring Boot, Spring AI, and Gradle Kotlin DSL. Each server is an independently runnable Gradle subproject. The first is `openFigi`.

Repository: https://github.com/anirbanroy88/MCPServer

## Build and test

Install a JDK 21 and run:

```powershell
.\gradlew.bat clean build
```

On Linux/macOS use `./gradlew clean build` (run `chmod +x gradlew` if your checkout did not preserve its executable bit).

The artifact is `openFigi/build/libs/openfigi-mcp.jar`. Tests use a local mock API and real MCP transports, including launching the packaged JAR. No OpenFIGI or model-provider key is needed to build or test.

Versions are centralized in `gradle/libs.versions.toml`: Spring Boot 3.5.16 and Spring AI 1.1.8. The wrapper pins Gradle 8.14.3; root conventions compile every Java module for Java 21.

## Run locally over STDIO

```powershell
java -jar openFigi/build/libs/openfigi-mcp.jar --spring.profiles.active=stdio
```

STDIO is also the default profile. An MCP client normally launches this command and communicates over stdin/stdout. Application logs go to stderr, and there is no HTTP listener.

Optionally configure `OPENFIGI_API_KEY` in the MCP client's process environment. Missing or blank means keyless access. Do not put keys in command-line arguments, conversations, or tool inputs.

## Run over HTTP / HTTPS

For personal clients capable of sending a bearer token:

```powershell
$env:MCP_ACCESS_TOKEN = '<your-random-token-of-at-least-32-characters>'
java -jar openFigi/build/libs/openfigi-mcp.jar --spring.profiles.active=http
```

The endpoint is `http://127.0.0.1:8080/mcp`. The HTTP profile defaults to token authentication and refuses startup without a suitable token. Clients send:

```text
Authorization: Bearer <your MCP access token>
X-OpenFIGI-API-Key: <optional user's OpenFIGI key>
```

The OpenFIGI header must accompany **every HTTP request**, not just initialization. Omitting it selects keyless upstream access, even if a previous request in the same session had a key. An `OPENFIGI_API_KEY` environment variable is deliberately ignored in HTTP mode.

For cloud clients, use public HTTPS and the OAuth mode described in [deployment instructions](docs/deployment.md). [Client examples and compatibility](docs/clients.md) explain which products document custom-header support.

## Tools

| Tool | Arguments | Result |
|---|---|---|
| `openfigi_mapping` | `jobs`: identifier jobs with optional `filters` | `results` in job order, preserving all matches and per-job warnings/errors |
| `openfigi_search` | Optional `query`, `filters`, `start` | One upstream page |
| `openfigi_filter` | Optional `query`, `filters`, `start` | One upstream page, including upstream total when present |
| `openfigi_values` | `key`: a supported mapping property | Available values |

Example mapping arguments:

```json
{
  "jobs": [
    {
      "idType": "TICKER",
      "idValue": "IBM",
      "filters": { "exchCode": "US" }
    }
  ]
}
```

A ticker can identify several instruments. The tools retain every match. Use the returned `next` token as `start` to fetch another search/filter page.

Mapping defaults to at most 5 jobs without a key and 100 with a key. OpenFIGI's documentation conflicts on the keyless batch size (5 in the endpoint section, 10 in the general table), so this server uses 5. Mapping budgets are 25 requests/minute keyless or 25/6 seconds per key; search/filter share 5/minute keyless or 20/minute per key. Values conservatively share the mapping budget. [OpenFIGI API documentation](https://www.openfigi.com/api/documentation#v3-post-mapping)

Successful results include structured MCP content and equivalent JSON text. Tool failures set `isError=true` with an `error` object containing `code`, `message`, and optional `retryAfterSeconds`. Codes include `INVALID_INPUT`, `INVALID_API_KEY`, `RATE_LIMITED`, `BUSY`, `UPSTREAM_TIMEOUT`, `RESPONSE_TOO_LARGE`, and `UPSTREAM_UNAVAILABLE`. Invalid keys never silently fall back to free traffic.

## Configuration and boundaries

- HTTP credentials live only in the current request context; no account store or database is used.
- Rate counters retain only credential hashes and timestamps in memory. Quotas coordinate one server process; run one replica initially.
- Default upstream limits: 16 concurrent calls, 3-second connect timeout, 10-second read timeout, 2 MiB response cap, and one retry after 250 ms for HTTP 5xx. HTTP redirects are not followed.
- `openfigi.*` properties configure the client. Batch and request budgets can be lowered, not raised above the documented conservative caps. Tests can point `openfigi.base-url` at a loopback HTTP mock; other upstream origins require HTTPS.
- HTTP rejects incoming Origin values unless listed in `MCP_ALLOWED_ORIGINS`. Native/cloud server clients usually omit Origin. This is not a browser CORS implementation.
- TLS, identity-provider registration and account permissions are deployment configuration. No external deployment or live client certification is implied by a passing local build.
- Keep application and reverse-proxy request-body/header debug logging disabled when handling credentials.

## Add another server

Create a new directory with its own `build.gradle.kts`, application class, resources and tests, then add `include("moduleName")` to `settings.gradle.kts`. Root conventions supply Java 21 and reproducible archive settings. Apply the Spring Boot plugin and import the shared BOMs as in `openFigi/build.gradle.kts`. Keep provider-specific code inside its server module.

Planning artifacts and the implementation checklist are in [bootstrap-openfigi-mcp](openspec/changes/bootstrap-openfigi-mcp/proposal.md).
