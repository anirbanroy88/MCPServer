## Purpose

Provide a reproducible foundation for independently built Java MCP servers in one repository.

## ADDED Requirements

### Requirement: Independent Java 21 server builds
The repository SHALL use Java 21 and Gradle Kotlin DSL, include a Gradle wrapper, and package each MCP server as an independent subproject. The first subproject SHALL be `openFigi`.

#### Scenario: Build the first server
- **WHEN** a developer runs the root wrapper build using Java 21
- **THEN** tests execute and an executable OpenFIGI server JAR is produced

### Requirement: Reproducible checks and instructions
The repository SHALL include CI checks and documented local, container, and HTTPS deployment instructions without requiring real upstream credentials for automated tests.

#### Scenario: Test without OpenFIGI credentials
- **WHEN** CI executes the test suite without API keys
- **THEN** upstream integration tests use local mock responses and do not consume OpenFIGI quota

### Requirement: Application logging
The repository SHALL include SLF4J and Logback logging support for application methods, requests, and exceptions. Logs SHALL be written to the configured directory, defaulting to `C:\Users\anirb\log\mcpServer`, and SHALL be configurable for other environments. Logging SHALL never write non-protocol content to STDIO stdout.

#### Scenario: Logging dependencies are available
- **WHEN** the application is built
- **THEN** SLF4J APIs and Logback implementation/configuration are included in the executable server artifact

#### Scenario: Log output is separated from protocol output
- **WHEN** the server runs in STDIO mode
- **THEN** application logs are written to the configured file destination and/or stderr, while stdout remains reserved for MCP messages
