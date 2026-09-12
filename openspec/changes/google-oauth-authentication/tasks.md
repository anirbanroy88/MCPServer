## 1. Security Configuration & Properties

- [x] 1.1 Add Google OAuth properties (`clientId`, `clientSecret`, `redirectUri`) to `AccessProperties` and ensure client secrets are redacted in string representations; verify configuration parsing with unit tests.
- [x] 1.2 Update `application-http.yml` and `deploy/.env.example` with `MCP_OAUTH_CLIENT_ID`, `MCP_OAUTH_CLIENT_SECRET`, and `MCP_OAUTH_REDIRECT_URI`; verify Spring environment property resolution.

## 2. Google Token Validation & Allowlist

- [x] 2.1 Implement Google OIDC token validator supporting `https://accounts.google.com` and `accounts.google.com` issuers and matching client ID in `aud` or `azp` claims; verify unit tests with simulated Google JWTs.
- [x] 2.2 Update authorization logic in `AccessConfiguration` to check both subject ID and verified `email` claim against `MCP_ALLOWED_SUBJECTS`; verify allowlist tests for authorized and unauthorized Google users.

## 3. Metadata & Documentation

- [x] 3.1 Update OAuth protected resource metadata discovery in `AccessConfiguration` to publish resource URLs and scopes; verify metadata endpoint tests at `/.well-known/oauth-protected-resource/mcp`.
- [x] 3.2 Update `docs/deployment.md` with step-by-step Google Cloud Console OAuth Client ID setup, including Authorized JavaScript Origins and Authorized Redirect URIs; verify documentation consistency.
