package com.geminibot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Termux'taki Node.js bot servisiyle konuşan WebSocket istemcisi. */
public final class Bridge {
    public record Line(String bot, String kind, String text) {}

    public static final List<Line> lines = new CopyOnWriteArrayList<>();
    public static volatile int version = 0;
    public static volatile List<String> bots = List.of();
    public static volatile boolean connected = false;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "geminibot-bridge");
        t.setDaemon(true);
        return t;
    });
    private static volatile WebSocket ws;
    private static volatile boolean connecting = false;
    private static CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    private Bridge() {}

    public static void start() {
        EXEC.scheduleWithFixedDelay(Bridge::tryConnect, 0, 3, TimeUnit.SECONDS);
    }

    private static void tryConnect() {
        if (connected || connecting) return;
        connecting = true;
        try {
            HTTP.newWebSocketBuilder()
                    .buildAsync(URI.create(BotConfig.INSTANCE.bridgeUrl), new Listener())
                    .whenComplete((w, err) -> {
                        connecting = false;
                        if (err == null) {
                            ws = w;
                            connected = true;
                            sendConfig();
                            send(obj("list"));
                        }
                    });
        } catch (Exception e) {
            connecting = false;
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket w) {
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String s = buf.toString();
                buf.setLength(0);
                try {
                    handle(s);
                } catch (Exception ignored) {
                }
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            disconnected();
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            disconnected();
        }
    }

    private static void disconnected() {
        if (connected) addLine(null, "log", "Servis bağlantısı koptu.");
        connected = false;
        ws = null;
        bots = List.of();
    }

    private static void handle(String raw) {
        JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
        String type = o.get("type").getAsString();
        String bot = o.has("bot") && !o.get("bot").isJsonNull() ? o.get("bot").getAsString() : null;
        switch (type) {
            case "log" -> addLine(bot, "log", o.get("text").getAsString());
            case "ai" -> addLine(bot, "ai", o.get("text").getAsString());
            case "bots" -> {
                List<String> l = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("bots")) l.add(e.getAsString());
                bots = List.copyOf(l);
            }
            default -> {}
        }
    }

    public static synchronized void addLine(String bot, String kind, String text) {
        lines.add(new Line(bot, kind, text));
        while (lines.size() > 300) lines.remove(0);
        version++;
    }

    private static JsonObject obj(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    public static synchronized boolean send(JsonObject o) {
        WebSocket w = ws;
        if (!connected || w == null) return false;
        String s = o.toString();
        tail = tail.handle((v, e) -> null).thenCompose(v -> w.sendText(s, true));
        return true;
    }

    // ---- komutlar ----

    public static void sendConfig() {
        JsonObject o = obj("config");
        o.addProperty("apiKey", BotConfig.INSTANCE.apiKey);
        o.addProperty("model", BotConfig.INSTANCE.model);
        send(o);
    }

    public static void spawn(String name, String host, int port) {
        JsonObject o = obj("spawn");
        o.addProperty("name", name);
        o.addProperty("host", host);
        o.addProperty("port", port);
        o.addProperty("version", "1.21.4");
        if (!send(o)) addLine(null, "log", "Servis bağlı değil. Termux'ta `node index.js` çalıştır.");
    }

    public static void remove(String name) {
        JsonObject o = obj("remove");
        o.addProperty("name", name);
        send(o);
    }

    public static void chat(String text, String target) {
        addLine("all".equals(target) ? null : target, "user", text);
        JsonObject o = obj("chat");
        o.addProperty("text", text);
        o.addProperty("bot", target);
        if (!send(o)) addLine(null, "log", "Servis bağlı değil. Termux'ta `node index.js` çalıştır.");
    }
}
