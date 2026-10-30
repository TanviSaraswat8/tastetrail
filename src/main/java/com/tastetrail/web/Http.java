package com.tastetrail.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tastetrail.model.User;
import com.tastetrail.security.TokenService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A very small router on top of the JDK's built-in HTTP server. */
public final class Http {
    private Http() {}

    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /** One request: path parameters, query string, JSON body and the signed-in user. */
    public static final class Req {
        public final String method;
        public final Map<String, String> params = new HashMap<>();
        public final Map<String, String> query = new HashMap<>();
        public Map<String, Object> body = Map.of();
        public User user;
        int status = 200;

        Req(String method) { this.method = method; }

        public void status(int s) { status = s; }
        public String q(String key, String def) { return query.getOrDefault(key, def); }
        public int param(String key) {
            try { return Integer.parseInt(params.get(key)); } catch (NumberFormatException e) { throw new ApiException.NotFound("Not found"); }
        }
        public String str(String key) { Object v = body.get(key); return v == null ? null : String.valueOf(v).trim(); }
        public Double num(String key) {
            Object v = body.get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof String s && !s.isBlank()) try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
            return null;
        }
        public Boolean bool(String key) { Object v = body.get(key); return v instanceof Boolean b ? b : null; }

        public User requireUser() {
            if (user == null) throw new ApiException.Unauthorized("Please sign in");
            return user;
        }
        /** Uses the polymorphic canManageRestaurants(): true for Admin, false for Customer. */
        public User requireAdmin() {
            if (!requireUser().canManageRestaurants()) throw new ApiException.Forbidden("Admin access only");
            return user;
        }
    }

    @FunctionalInterface public interface Handler { Object handle(Req r) throws Exception; }
    private record Route(String method, Pattern pattern, List<String> names, Handler handler) {}

    public static final class Router implements HttpHandler {
        private final List<Route> routes = new ArrayList<>();
        private final TokenService tokens;

        public Router(TokenService tokens) { this.tokens = tokens; }

        /** Paths may contain parameters such as /api/orders/:id */
        public Router add(String method, String path, Handler h) {
            List<String> names = new ArrayList<>();
            Matcher m = Pattern.compile(":(\\w+)").matcher(path);
            while (m.find()) names.add(m.group(1));
            routes.add(new Route(method, Pattern.compile("^" + path.replaceAll(":\\w+", "([^/]+)") + "$"), names, h));
            return this;
        }
        public Router get(String p, Handler h) { return add("GET", p, h); }
        public Router post(String p, Handler h) { return add("POST", p, h); }
        public Router patch(String p, Handler h) { return add("PATCH", p, h); }

        @Override public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            Req req = new Req(ex.getRequestMethod());
            Object result;
            try {
                if ("OPTIONS".equals(req.method)) { send(ex, 204, null); return; }
                parseQuery(ex.getRequestURI().getRawQuery(), req.query);
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) req.user = tokens.verify(auth.substring(7));
                Route route = null; Matcher match = null;
                for (Route r : routes) {
                    Matcher m = r.pattern.matcher(path);
                    if (r.method.equals(req.method) && m.matches()) { route = r; match = m; break; }
                }
                if (route == null) throw new ApiException.NotFound("Not found");
                for (int i = 0; i < route.names.size(); i++) req.params.put(route.names.get(i), match.group(i + 1));
                if (!"GET".equals(req.method)) req.body = readBody(ex);
                result = route.handler.handle(req);
                send(ex, req.status, result);
            } catch (ApiException e) {
                send(ex, e.getStatus(), Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                send(ex, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                send(ex, 500, Map.of("error", "Something went wrong on our side"));
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> readBody(HttpExchange ex) throws IOException {
            try (InputStream in = ex.getRequestBody()) {
                byte[] data = in.readNBytes(100_001);
                if (data.length > 100_000) throw new ApiException(413, "Request is too large");
                String s = new String(data, StandardCharsets.UTF_8).trim();
                if (s.isEmpty()) return Map.of();
                try { Map<String, Object> m = GSON.fromJson(s, Map.class); return m == null ? Map.of() : m; }
                catch (JsonSyntaxException | ClassCastException e) { throw new ApiException.BadRequest("Request body must be JSON"); }
            }
        }
    }

    static void parseQuery(String raw, Map<String, String> out) {
        if (raw == null) return;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
    }

    static void send(HttpExchange ex, int status, Object body) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
        if (body == null) { ex.sendResponseHeaders(status, -1); ex.close(); return; }
        byte[] out = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(out); }
    }

    /** Serves the HTML/CSS/JS front end from the web/ folder. */
    public static final class StaticFiles implements HttpHandler {
        private final Path root;
        private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon", "json", "application/json", "webmanifest", "application/manifest+json");

        public StaticFiles(Path root) { this.root = root.toAbsolutePath().normalize(); }

        @Override public void handle(HttpExchange ex) throws IOException {
            String p = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (p.equals("/") || p.isEmpty()) p = "/index.html";
            Path file = root.resolve(p.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) { // also blocks ../ path traversal
                byte[] nf = "<h1>404 – Page not found</h1>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(404, nf.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(nf); }
                return;
            }
            String name = file.getFileName().toString();
            String ext = name.substring(name.lastIndexOf('.') + 1);
            byte[] data = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        }
    }
}
