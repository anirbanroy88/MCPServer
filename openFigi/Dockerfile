FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 mcp && useradd --uid 10001 --gid mcp --create-home mcp \
    && mkdir -p /app/logs && chown -R mcp:mcp /app
COPY --chown=mcp:mcp openFigi/build/libs/openfigi-mcp.jar /app/openfigi-mcp.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=http SERVER_ADDRESS=0.0.0.0 MCP_LOG_DIR=/app/logs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/openfigi-mcp.jar"]
