package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import handler.AuthFilter;
import handler.HandlerFactory;
import handler.GsonTool;
import handler.StatusCodes;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import service.RoutineScheduler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Server {

    // Paths that do NOT require an authenticated session.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/health",
            "/login", "/auth/login",
            "/createUser", "/auth/createUser", "/auth/register",
            "/logout", "/auth/logout",
            "/auth/whoami", "/auth/me",
            "/plaid/webhook");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "1299"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("HTTP server started on port " + port);

        server.createContext("/", new GenericHandler());
        server.setExecutor(null); // default executor
        server.start();

        // Apply recurring monthly "set aside" routines in the background.
        new RoutineScheduler().start();
        System.out.println("Routine scheduler started");
    }

    static class GenericHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Allow the deployed frontend origin to be configured; default to CRA dev.
            String origin = System.getenv().getOrDefault("APP_ORIGIN", "http://localhost:3000");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
                exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Cookie, Authorization");
                exchange.getResponseHeaders().add("Vary", "Origin");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // Single-service deploy: anything that isn't an API path is the bundled SPA.
            if (!isApiPath(path)) {
                serveStatic(exchange, path);
                return;
            }

            Map<String, List<String>> headers = exchange.getRequestHeaders();

            int contentLength = 0;
            if (headers.containsKey("Content-Length")) {
                try {
                    contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
                } catch (Exception ignore) { }
            }
            String body;
            if (contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = exchange.getRequestBody().read(buf, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                body = new String(buf, 0, read);
            } else {
                body = new String(exchange.getRequestBody().readAllBytes());
            }

            ParsedRequest req = new ParsedRequest();
            req.setPath(path);
            req.setMethod(method);
            headers.forEach((key, values) -> req.setHeaderValue(key, String.join(",", values)));
            req.setBody(body);

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().add("Vary", "Origin");

            HttpResponseBuilder respBuilder;
            try {
                respBuilder = route(path, req);
            } catch (Throwable t) {
                // Never drop the connection on an unexpected error — return a 500 JSON.
                t.printStackTrace();
                respBuilder = new HttpResponseBuilder().setStatus(StatusCodes.SERVER_ERROR)
                        .setBody(new RestApiAppResponse<>(false, null, "Server error: " + t.getMessage()));
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            String json = GsonTool.GSON.toJson(respBuilder.getBody());
            byte[] respBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(respBytes.length));
            exchange.getResponseHeaders().set("Connection", "close");
            respBuilder.getHeaders().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            int statusCode = 200;
            try {
                statusCode = Integer.parseInt(respBuilder.getStatus().split(" ")[0]);
            } catch (Exception ignore) { }
            exchange.sendResponseHeaders(statusCode, respBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(respBytes);
            os.flush();
            os.close();
        }

        /** Central routing + auth gate. Non-public paths require a valid session. */
        private HttpResponseBuilder route(String path, ParsedRequest req) {
            if ("/health".equals(path)) {
                return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                        .setBody(new RestApiAppResponse<>(true, null, "ok"));
            }
            if (!PUBLIC_PATHS.contains(path)) {
                AuthFilter.AuthResult auth = AuthFilter.doFilter(req);
                if (!auth.isLoggedIn) {
                    return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED)
                            .setBody(new RestApiAppResponse<>(false, null, "Authentication required"));
                }
            }
            return HandlerFactory.getHandler(req).handleRequest(req);
        }

        // Exact API endpoints — a bare path that *is* the API.
        private static final Set<String> API_EXACT = Set.of(
                "/health", "/login", "/logout", "/createUser",
                "/getTransactions", "/transactions", "/deposit", "/withdraw",
                "/createDeposit", "/createWithdraw", "/transfer", "/transferGoals",
                "/savings", "/getSavings", "/balance", "/financing", "/repay");

        // Prefix groups — API only when followed by a sub-path. This is what lets
        // bare "/accounts" and "/goals" (which are ALSO client-side SPA routes)
        // fall through to index.html instead of 404-ing on the API. Without it, a
        // page refresh or nav to /accounts or /goals hits a non-existent handler.
        private static final String[] API_PREFIX_GROUPS = {
                "/auth", "/goals", "/accounts", "/routines", "/plaid", "/transactions", "/spend"
        };

        private static boolean isApiPath(String path) {
            if (API_EXACT.contains(path)) return true;
            for (String p : API_PREFIX_GROUPS) {
                if (path.startsWith(p + "/")) return true;
            }
            return false;
        }

        /** Serve the bundled React build (with SPA fallback to index.html). */
        private void serveStatic(HttpExchange exchange, String path) throws IOException {
            String staticDir = System.getenv().getOrDefault("STATIC_DIR", "");
            if (staticDir.isEmpty()) {
                byte[] b = "{\"status\":false,\"message\":\"Not found\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, b.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
                return;
            }
            java.nio.file.Path base = java.nio.file.Paths.get(staticDir).toAbsolutePath().normalize();
            String rel = (path == null || path.equals("/") || path.isBlank()) ? "index.html" : path.substring(1);
            java.nio.file.Path file = base.resolve(rel).normalize();
            // Path-traversal guard + SPA fallback for unknown routes.
            if (!file.startsWith(base) || !java.nio.file.Files.exists(file)
                    || java.nio.file.Files.isDirectory(file)) {
                file = base.resolve("index.html");
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file.toString()));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        private static String contentType(String name) {
            String n = name.toLowerCase();
            if (n.endsWith(".html")) return "text/html; charset=utf-8";
            if (n.endsWith(".js")) return "application/javascript";
            if (n.endsWith(".css")) return "text/css";
            if (n.endsWith(".json") || n.endsWith(".map")) return "application/json";
            if (n.endsWith(".svg")) return "image/svg+xml";
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".ico")) return "image/x-icon";
            if (n.endsWith(".woff2")) return "font/woff2";
            if (n.endsWith(".woff")) return "font/woff";
            if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
            return "application/octet-stream";
        }
    }
}
