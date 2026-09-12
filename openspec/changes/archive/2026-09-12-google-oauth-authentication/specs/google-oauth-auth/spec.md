## Purpose

Provides Google OAuth 2.0 Identity Provider authentication, token validation, and redirect URI management for the OpenFIGI MCP server.

## ADDED Requirements

### Requirement: Google OAuth 2.0 Client Configuration
The server SHALL support configuring Google OAuth 2.0 client credentials (`client-id`, `client-secret`, `redirect-uri`) through application properties and environment variables (`MCP_OAUTH_CLIENT_ID`, `MCP_OAUTH_CLIENT_SECRET`, `MCP_OAUTH_REDIRECT_URI`). Client secrets SHALL remain redacted in all logs and string representations.

#### Scenario: Server starts with Google OAuth credentials
- **WHEN** the server starts in OAuth mode with Google OAuth properties supplied
- **THEN** the configuration is loaded securely without exposing the client secret in startup logs

### Requirement: Google OIDC Token Validation
When OAuth mode is enabled with Google as the identity provider, the server SHALL validate JWT tokens issued by Google (`https://accounts.google.com` and `accounts.google.com`). The validator SHALL verify token signature via Google's published JWKS keys, verify expiration, and ensure that either the audience (`aud`) or authorized party (`azp`) claim matches the configured Google Client ID.

#### Scenario: Valid Google JWT is presented
- **WHEN** an HTTP client sends an unexpired Google ID/access token with a valid signature and matching client ID
- **THEN** the server authenticates the request and allows access to the MCP tools

#### Scenario: Token issued for different client application
- **WHEN** a client presents a valid Google JWT whose audience and azp do not match the configured client ID
- **THEN** the request is rejected with a 401 Unauthorized status

### Requirement: Google User Subject and Email Allowlisting
The server SHALL support restricting access to authorized Google accounts using the `MCP_ALLOWED_SUBJECTS` configuration. The check SHALL accept matching Google subject IDs (`sub`) or verified Google email addresses. If `MCP_ALLOWED_SUBJECTS` is empty, all authenticated Google accounts SHALL be authorized.

#### Scenario: Authorized Google email matches allowlist
- **WHEN** an authenticated user whose email appears in `MCP_ALLOWED_SUBJECTS` invokes an MCP tool
- **THEN** the tool invocation proceeds

#### Scenario: Unlisted Google user attempts access
- **WHEN** an authenticated user whose subject and email are not in `MCP_ALLOWED_SUBJECTS` invokes an MCP tool
- **THEN** access is denied with a 403 Forbidden or 401 Unauthorized status

### Requirement: Redirection URI and Origin Metadata
The server SHALL publish and document its public base URL as the Authorized JavaScript Origin and specify valid Authorized Redirect URIs for Google Cloud Console OAuth Client configuration.

#### Scenario: Client checks OAuth protected resource metadata
- **WHEN** a client queries `/.well-known/oauth-protected-resource/mcp`
- **THEN** the server returns the authorization server metadata and resource identifier
