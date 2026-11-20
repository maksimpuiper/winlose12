package ru.winlose;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

public class Config {
    public boolean enabled = true;
    public String anchor = "TOP_CENTER"; // TOP_LEFT TOP_CENTER TOP_RIGHT MIDDLE_LEFT CENTER MIDDLE_RIGHT BOTTOM_LEFT BOTTOM_CENTER BOTTOM_RIGHT
    public int offsetX = 0;
    public int offsetY = 40;
    public float scale = 1.5f;
    public boolean showNames = true;
    public int timeoutSeconds = 10;   // сколько секунд после последнего удара/урона держать цель
    public double maxDistance = 16;   // дальше этой дистанции цель сбрасывается

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("winlose.json");
    public static Config INSTANCE = load();

    static Config load() {
        try { if (Files.exists(FILE)) return GSON.fromJson(Files.readString(FILE), Config.class); } catch (Exception ignored) {}
        Config c = new Config(); c.save(); return c;
    }
    public void save() { try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {} }
}
