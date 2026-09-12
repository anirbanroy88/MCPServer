# HTTPS deployment

## Container

Build the JAR first and then the runtime image from the repository root:

```sh
./gradlew :openFigi:bootJar
docker build -f openFigi/Dockerfile -t openfigi-mcp:local .
```

The container runs Java 21 as a non-root user with the HTTP profile. Supply the access configuration below. Container startup does not need an OpenFIGI key.

The [Compose example](../deploy/compose.yml) places Caddy in front of one server instance. Copy `deploy/.env.example` to `deploy/.env`, set a real hostname and authentication configuration, then:

```sh
docker compose --env-file deploy/.env -f deploy/compose.yml up --build -d
```

The hostname must resolve to your host, ports 80/443 must be reachable, and Caddy must be able to obtain a certificate. The application port is available only on the internal Compose network. Certificate data uses Caddy volumes; OpenFIGI keys are not stored.

## Personal token mode

Set `MCP_SECURITY_MODE=token` and a random `MCP_ACCESS_TOKEN` of at least 32 characters. Clients must send that bearer token on every request. This mode has one personal identity and is not an OAuth authorization server.

## Google OAuth 2.0 Mode

To use Google as your OAuth Identity Provider:

1. **Create OAuth Client ID in Google Cloud Console**:
   - Navigate to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
   - **Application Type**: `Web application`
   - **Name**: `OpenFIGI MCP Server`
   - **Authorized JavaScript origins**:
     - `https://openfigi-mcp-941250209856.asia-east1.run.app` (or your custom domain)
     - `http://localhost:8080` (for local development)
   - **Authorized redirect URIs**:
     - `https://openfigi-mcp-941250209856.asia-east1.run.app/login/oauth2/code/google`
     - `http://localhost:8080/login/oauth2/code/google`
     - Add external client callback URLs (e.g. ChatGPT / Claude Web MCP redirect URL) if connecting external web clients.

2. **Configure Environment Variables**:
   ```text
   MCP_SECURITY_MODE=oauth
   MCP_OAUTH_ISSUER=https://accounts.google.com
   MCP_PUBLIC_URL=https://openfigi-mcp-941250209856.asia-east1.run.app/mcp
   MCP_OAUTH_CLIENT_ID=<your-google-client-id>.apps.googleusercontent.com
   MCP_OAUTH_CLIENT_SECRET=<your-google-client-secret>
   MCP_OAUTH_REDIRECT_URI=https://openfigi-mcp-941250209856.asia-east1.run.app/login/oauth2/code/google
   MCP_OAUTH_AUDIENCE=<your-google-client-id>.apps.googleusercontent.com
   MCP_ALLOWED_SUBJECTS=your-email@gmail.com,other-email@gmail.com
   ```
   *(Leave `MCP_ALLOWED_SUBJECTS` empty to allow all valid Google accounts to access the server).*

## Generic OAuth mode for private cloud-client connections

Set:

```text
MCP_SECURITY_MODE=oauth
MCP_PUBLIC_URL=https://figi.example.com/mcp
MCP_OAUTH_AUDIENCE=https://figi.example.com/mcp
MCP_OAUTH_ISSUER=https://your-identity-provider.example
MCP_ALLOWED_SUBJECTS=your-provider-subject
```

Use the issuer's canonical URL. The resource server validates JWT signatures against the issuer's published keys, issuer, expiry and audience. Access requires the `mcp:tools` scope. The optional subject allowlist restricts initial personal access; an empty list admits any valid authorized subject.

The server publishes discovery at `/.well-known/oauth-protected-resource/mcp` (and the root alias), and includes its URL in unauthorized Bearer challenges. It owns no login page, authorization endpoint, token endpoint or user database.

Configure your external identity provider to support authorization-code + PKCE S256, the client's registration method (pre-registration, DCR or CIMD as supported), the client's exact callback URL, resource/audience binding and the `mcp:tools` scope. Complete that provider configuration before connecting ChatGPT or Claude. Do not point OAuth clients at personal token mode.

The optional OpenFIGI header remains separate from OAuth. A client unable to send it uses free upstream access after authenticating. No OpenFIGI key is stored in the identity provider or encoded in an OAuth token by this project.

## Opening access later

Keep OAuth and remove/expand `MCP_ALLOWED_SUBJECTS` to admit more users. Alternatively, explicitly select `MCP_SECURITY_MODE=none` for anonymous access; this is never the default. The absence of an OpenFIGI key does not bypass whichever MCP authentication policy you chose.

All keyless callers share the process's free upstream budget. Keyed callers sharing a key share its counters. Start with one replica: multiple replicas or other programs using the same OpenFIGI credentials need coordinated quotas. The server obeys upstream 429 and remaining/reset signals but cannot account for other deployments ahead of time.

Configure `MCP_ALLOWED_ORIGINS` as a comma-separated exact allowlist only for clients that send Origin. Keep the application port private behind the proxy and do not enable access logs that capture credential headers. Caddy streams MCP responses without buffering.

## Validation

Run the Gradle tests and verify your actual HTTPS endpoint with an MCP client. OAuth end-to-end login requires the chosen provider and your client account; TLS certificate issuance requires the real hostname. These external steps are not performed by the local automated tests.
