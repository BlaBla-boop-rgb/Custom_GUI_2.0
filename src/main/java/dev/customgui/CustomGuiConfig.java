package dev.customgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Speichert Shop-Link und Inseln in config/customgui.json (kann auch von Hand bearbeitet werden). */
public final class CustomGuiConfig {

    public static class Island {
        public String name = "";
        public String command = "";

        public Island() {
        }

        public Island(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    /** Dieser Link wird beim Klick auf "Shop" im Standard-Browser geöffnet. */
    public String shopUrl = "https://www.cytooxien.de/de";
    public List<Island> islands = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CustomGuiConfig instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("customgui.json");
    }

    public static CustomGuiConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        Path p = path();
        CustomGuiConfig loaded = null;
        try {
            if (Files.exists(p)) {
                loaded = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), CustomGuiConfig.class);
            }
        } catch (Exception e) {
            System.err.println("[CustomGui] Konfiguration konnte nicht gelesen werden: " + e);
        }
        if (loaded == null) {
            loaded = new CustomGuiConfig();
        }
        if (loaded.islands == null) {
            loaded.islands = new ArrayList<>();
        }
        if (loaded.shopUrl == null || loaded.shopUrl.isBlank()) {
            loaded.shopUrl = "https://www.cytooxien.de/de";
        }
        instance = loaded;
        save();
    }

    public static void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[CustomGui] Konfiguration konnte nicht gespeichert werden: " + e);
        }
    }
}
