# Client connections

These examples distinguish the MCP protocol from individual app configuration. The automated suite verifies Java SDK STDIO and Streamable HTTP interoperability; it does not log into your client accounts.

## VS Code

Merge [vscode-stdio.json](../examples/clients/vscode-stdio.json) or [vscode-http.json](../examples/clients/vscode-http.json) into your workspace's `.vscode/mcp.json`. Replace the absolute JAR path or HTTPS endpoint. The examples use password prompts for secrets. Leave the OpenFIGI key blank for free access.

VS Code documents local commands, HTTP headers and input variables in MCP configuration. [Official instructions](https://code.visualstudio.com/docs/agent-customization/mcp-servers)

## Claude Desktop

The [Claude Desktop STDIO example](../examples/clients/claude-desktop.json) shows the local `mcpServers` shape. Replace the JAR path and configure an optional `OPENFIGI_API_KEY` in the process environment. Restart the client after configuration changes. Local configuration containing a key is managed by the client and must not be committed.

Claude's remote connectors connect from Anthropic's cloud, including when configured in the desktop app. Use the public HTTPS endpoint with a compatible OAuth provider for private access. The cited setup does not establish support for arbitrary OpenFIGI headers, so keyed remote access is conditional on that capability; otherwise requests use free traffic. [Claude remote connectors](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)

## ChatGPT web / desktop

Use a publicly reachable HTTPS `/mcp` endpoint with Streamable HTTP, or a supported OpenAI tunnel. Add the connection using the current developer/plugin settings available to your account. This repository does not assume the desktop app can launch arbitrary local Java STDIO processes.

Private access uses the external OAuth integration in [deployment.md](deployment.md). The setup documentation does not establish an arbitrary custom-header field for the OpenFIGI key. When your client cannot supply that header, use keyless access. A server cannot manufacture a missing client credential.

[Official connection guidance](https://developers.openai.com/plugins/deploy/connect-chatgpt) and [OAuth requirements](https://developers.openai.com/plugins/build/auth) describe the remote endpoint and authentication flow. Availability depends on account/workspace configuration.

## Kimi

[Kimi Code MCP documentation](https://www.kimi.com/code/docs/en/kimi-code-cli/customization/mcp.html) explicitly documents STDIO and HTTP configuration, including headers. Adapt [kimi-code-http.json](../examples/clients/kimi-code-http.json) with your HTTPS URL and credentials.

Kimi Code is a separate product from the Kimi web app. Native custom-server configuration, authentication and custom-header support in Kimi web remain unverified; do not treat the Code example as proof of web compatibility.

## Credential rules for every client

- The MCP access token/OAuth token goes in `Authorization`.
- The optional OpenFIGI key goes in `X-OpenFIGI-API-Key` on every HTTP request, or `OPENFIGI_API_KEY` for a local process.
- OpenFIGI keys never go in tool arguments, URL parameters or MCP session IDs.
- A missing key means free traffic; an invalid supplied key returns an error.
- Token authentication is useful only when the client can supply the configured bearer token. For browser/cloud OAuth clients use OAuth mode with a suitable identity provider.
