package io.github.anirbanroy88.mcp.openfigi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import com.sun.net.httpserver.HttpServer;

public final class MockOpenFigi implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    public final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    public volatile Function<Request, Reply> handler = request -> Reply.json(200, "{\"values\":[\"ID_ISIN\"]}");

    public MockOpenFigi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                var request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-OPENFIGI-APIKEY"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                requests.add(request);
                Reply reply = handler.apply(request);
                try {
                    if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
                    reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(reply.status(), body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (IOException disconnected) {
                    // Timeout tests deliberately close the connection before the reply.
                } finally { exchange.close(); }
            });
            server.start();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    public URI uri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
    public record Request(String method, String path, String key, String body) {}
    public record Reply(int status, String body, Map<String, String> headers, long delayMillis) {
        public static Reply json(int status, String body) { return new Reply(status, body, Map.of(), 0); }
    }
}
