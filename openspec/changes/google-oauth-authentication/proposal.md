## Why

Enable Google as the OAuth 2.0 Identity Provider (IdP) for the OpenFIGI MCP server so external OAuth clients (including ChatGPT Web, Claude, and web applications) and public users can authenticate via Google OAuth 2.0 to access MCP tools securely.

## What Changes

- Add configuration properties and environment variables (`MCP_OAUTH_CLIENT_ID`, `MCP_OAUTH_CLIENT_SECRET`, `MCP_OAUTH_REDIRECT_URI`) for Google OAuth 2.0 Client credentials.
- Update OAuth JWT validation to support Google's OpenID Connect (OIDC) token specifications:
  - Validate Google token issuers (`https://accounts.google.com` and `accounts.google.com`).
  - Validate audience (`aud`) and authorized party (`azp`) against the configured Google Client ID.
  - Support Google user subject/email allowlisting when `MCP_ALLOWED_SUBJECTS` is configured.
- Define and document the authorized redirect URIs and authorized JavaScript origins for Google Cloud Console OAuth Client ID configuration.
- Support standard OAuth 2.0 redirection endpoints and resource metadata discovery for Google OAuth client integrations.

## Capabilities

### New Capabilities
- `google-oauth-auth`: Google OAuth 2.0 Identity Provider integration, client credentials handling, token validation rules, and redirect URI management for the MCP server.

### Modified Capabilities
<!-- None -->

## Impact

- **Configuration**: New configuration keys in `application-http.yml` under `mcp.security.oauth` or `mcp.security`.
- **Code**: `AccessProperties.java` and `AccessConfiguration.java` in `openFigi` module.
- **Documentation**: Updated `docs/deployment.md` and `deploy/.env.example` with Google Cloud OAuth Client ID configuration steps, Authorized JavaScript Origins, and Authorized Redirect URIs.
