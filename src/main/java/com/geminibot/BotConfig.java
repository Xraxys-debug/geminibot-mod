package com.geminibot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class BotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("geminibot.json");

    public String apiKey = "";
    public String model = "gemini-3.5-flash-lite";
    public boolean localMode = true;   // true: localhost, false: IP/domain
    public String host = "";
    public int port = 25565;
    public String botName = "Bot1";
    public String bridgeUrl = "ws://127.0.0.1:8765";

    public static final BotConfig INSTANCE = load();

    private static BotConfig load() {
        try {
            if (Files.exists(FILE)) {
                BotConfig c = GSON.fromJson(Files.readString(FILE), BotConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {
        }
        return new BotConfig();
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception ignored) {
        }
    }

    /** Botun bağlanacağı host. */
    public String serverHost() {
        return localMode || host == null || host.isBlank() ? "localhost" : host;
    }
}
