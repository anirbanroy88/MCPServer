## Context

See `proposal.md` for motivation. The OpenFIGI MCP server is implemented with Spring Boot and Spring Security OAuth2 Resource Server. Currently, `AccessConfiguration` validates JWT tokens with a single issuer location and strict audience check. Integrating Google OAuth 2.0 requires handling Google OIDC token semantics (JWKS discovery at `https://www.googleapis.com/oauth2/v3/certs`, issuers `https://accounts.google.com`, `aud`/`azp` client ID claims, and email claims).

## Goals / Non-Goals

**Goals:**
- Provide configuration properties to store Google OAuth Client ID, Client Secret, and Redirect URIs securely.
- Support Google OIDC JWT validation (signature, issuer, client ID in `aud` or `azp`).
- Support user allowlisting by Google email and subject ID in `MCP_ALLOWED_SUBJECTS`.
- Provide concrete Google Cloud Console OAuth Client ID settings (Authorized JavaScript Origins and Authorized Redirect URIs).

**Non-Goals:**
- Implementing a full OAuth authorization server or custom user database (Google remains the external IdP).
- Storing OpenFIGI API keys inside Google OAuth credentials or tokens.

## Decisions

### Decision 1: Google OAuth Configuration in `AccessProperties`
- **Choice**: Extend `AccessProperties` to bind `clientId`, `clientSecret`, and `redirectUri` under `mcp.security.oauth.*` with fallback environment variables `MCP_OAUTH_CLIENT_ID`, `MCP_OAUTH_CLIENT_SECRET`, and `MCP_OAUTH_REDIRECT_URI`.
- **Rationale**: Keeps secrets centralized in Spring Boot's type-safe configuration record. Ensures `toString()` and logging always redact the client secret.
- **Alternatives considered**: Storing in separate property namespaces; rejected to keep security settings unified.

### Decision 2: Google OIDC Token Validation Strategy
- **Choice**: When Google OAuth is selected (`mcp.security.issuer=https://accounts.google.com` or Google client ID is configured), configure a Nimbus JWT decoder with Google's JWKS endpoint (`https://www.googleapis.com/oauth2/v3/certs`) and custom validator checking:
  1. Issuer is `https://accounts.google.com` or `accounts.google.com`.
  2. Audience (`aud`) contains the Client ID OR Authorized Party (`azp`) equals the Client ID.
  3. Token is not expired.
- **Rationale**: Google issued ID tokens can contain the Client ID in either `aud` or `azp` when cross-client auth is involved.

### Decision 3: Subject & Email Allowlist Matching
- **Choice**: Check both `jwt.getSubject()` (Google numerical user ID) and `jwt.getClaimAsString("email")` against `allowedSubjects`.
- **Rationale**: Users configure recognizable email addresses (e.g., `user@gmail.com`) rather than obscure Google numeric subject IDs.

### Decision 4: Google Cloud Console OAuth Credentials Mapping
For the Google Cloud Console "Create OAuth Client ID" page:
- **Application Type**: `Web application`
- **Authorized JavaScript origins**:
  - `https://openfigi-mcp-941250209856.asia-east1.run.app`
  - `http://localhost:8080` (for local development)
- **Authorized redirect URIs**:
  - `https://openfigi-mcp-941250209856.asia-east1.run.app/login/oauth2/code/google`
  - External MCP client callback URIs (e.g. ChatGPT OAuth callback URLs when registering the MCP tool in ChatGPT).

## Risks / Trade-offs

- **[Risk]** Google ID tokens vs Access tokens: Google access tokens for Google APIs are opaque strings, whereas Google OIDC ID tokens are signed JWTs.
  → **Mitigation**: MCP clients and external tools requesting Google OAuth must request the standard OIDC scopes (`openid`, `email`, `profile`) to receive a verifiable JWT ID token.
- **[Risk]** Token expiration in long-lived MCP sessions.
  → **Mitigation**: Streamable HTTP / MCP client refreshes token per request using standard OAuth refresh token flow at the client side.
