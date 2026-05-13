package dev.khaodoes.remoteListsAddon.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.khaodoes.remoteListsAddon.RemoteListsAddon;
import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

// todo: i don't really like how it looks code-wise, looks pretty messy. gotta have to look into a better syntax.
public class ApiServer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String LISTS = "/api/lists";
    private static final String PLAYERS = "/api/players";

    private final String host;
    private final int port;
    private HttpServer server;
    private boolean running;

    public ApiServer(String host, int port) { this.host = host; this.port = port; }

    public synchronized void start() throws IOException {
        if (running) return;
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api", this::handle);
        server.start();
        running = true;
        RemoteListsAddon.LOG.info("API server started on http://{}:{}", host, port);
    }

    public synchronized void stop() {
        if (!running) return;
        server.stop(1);
        running = false;
        RemoteListsAddon.LOG.info("API server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    private void handle(HttpExchange x) throws IOException {
        try {
            String path = x.getRequestURI().getPath();
            String method = x.getRequestMethod().toUpperCase();

            if (method.equals("OPTIONS")) {
                cors(x);
                return;
            }

            if (path.startsWith(LISTS)) {
                lists(x, path, method);
                return;
            }

            if (path.startsWith(PLAYERS)) {
                players(x, path, method);
                return;
            }

            if (path.equals("/api/health")) {
                json(x, 200, Map.of("status", "ok", "listsCount", RemoteListsAddon.LIST_SERVICE.getAllLists().size()));
                return;
            }

            json(x, 404, Map.of("error", "not found"));
        } catch (Exception error) {
            RemoteListsAddon.LOG.error("API error", error);
            json(x, 500, Map.of("error", "internal server error"));
        }
    }

    private void lists(HttpExchange x, String path, String method) throws IOException {
        // GET /api/lists
        if (path.equals(LISTS) || path.equals(LISTS + "/")) {
            if (!method.equals("GET")) {
                json(x, 405, Map.of("error", "method not allowed"));
                return;
            }

            json(x, 200, Map.of("lists", RemoteListsAddon.LIST_SERVICE.getAllLists().stream().map(ApiServer::listMap).toList()));
            return;
        }

        String rest = path.substring(LISTS.length() + 1); // after "/api/lists/"

        if (rest.contains("/players")) {
            String[] parts = rest.split("/players/?");
            String listId = parts[0];
            String username = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;

            if (username == null) {
                // POST /api/lists/{id}/players
                if (!method.equals("POST")) {
                    json(x, 405, Map.of("error", "method not allowed"));
                    return;
                }

                String name = readBody(x);
                if (name == null || name.isBlank()) {
                    json(x, 400, Map.of("error", "username is required"));
                    return;
                }

                boolean ok = RemoteListsAddon.LIST_SERVICE.addPlayer(listId, name);
                json(x, ok
                    ? 200
                    : 409, ok
                    ? Map.of("success", true, "username", name)
                    : Map.of("error", "player already in list or list not found")
                );

                return;
            }

            // DELETE /api/lists/{id}/players/{username}
            if (!method.equals("DELETE")) {
                json(x, 405, Map.of("error", "method not allowed"));
                return;
            }

            boolean ok = RemoteListsAddon.LIST_SERVICE.removePlayer(listId, username);
            json(x, ok
                ? 200
                : 404, ok
                ? Map.of("success", true, "username", username)
                : Map.of("error", "player not found or list not found")
            );
            return;
        }

        // GET /api/lists/{id}
        String listId = rest.endsWith("/") ? rest.substring(0, rest.length() - 1) : rest;
        if (!method.equals("GET")) {
            json(x, 405, Map.of("error", "method not allowed"));
            return;
        }

        PlayerList list = RemoteListsAddon.LIST_SERVICE.getList(listId);
        if (list == null) {
            json(x, 404, Map.of("error", "list not found"));
            return;
        }

        json(x, 200, listMap(list));
    }

    private void players(HttpExchange x, String path, String m) throws IOException {
        if (!m.equals("GET")) {
            json(x, 405, Map.of("error", "method not allowed"));
            return;
        }

        // GET /api/players/{username}
        if (path.length() > PLAYERS.length() + 1) {
            String username = path.substring(PLAYERS.length() + 1);
            List<String> inLists = new ArrayList<>();
            for (PlayerList l : RemoteListsAddon.LIST_SERVICE.getAllLists())
                for (PlayerEntry e : l.entries())
                    if (e.username().equalsIgnoreCase(username)) {
                        inLists.add(l.id());
                        break;
                    }

            json(x, 200, Map.of("username", username, "present", !inLists.isEmpty(), "lists", inLists));
            return;
        }

        // GET /api/players
        json(x, 200, Map.of("players", RemoteListsAddon.LIST_SERVICE.getAllPlayers().stream().map(ApiServer::entryMap).toList()));
    }

    private static Map<String, Object> listMap(PlayerList l) {
        return Map.of("id", l.id(), "displayName", l.displayName(), "entries", l.entries().stream().map(ApiServer::entryMap).toList());
    }

    private static Map<String, Object> entryMap(PlayerEntry e) {
        return Map.of("uuid", e.uuid() != null ? e.uuid().toString() : null, "username", e.username());
    }

    private String readBody(HttpExchange x) throws IOException {
        byte[] b = x.getRequestBody().readAllBytes();
        if (b.length == 0) return null;
        Map<?, ?> m = GSON.fromJson(new String(b, StandardCharsets.UTF_8), Map.class);
        Object v = m != null ? m.get("username") : null;
        return v instanceof String s ? s : null;
    }

    private void json(HttpExchange x, int status, Object body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        x.sendResponseHeaders(status, data.length);
        try (OutputStream os = x.getResponseBody()) {
            os.write(data);
        }
    }

    private void cors(HttpExchange x) throws IOException {
        x.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        x.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        x.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        x.sendResponseHeaders(204, -1);
    }
}
