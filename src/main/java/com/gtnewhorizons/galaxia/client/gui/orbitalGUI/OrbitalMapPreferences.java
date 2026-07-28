package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class OrbitalMapPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String KEY_WORLDS = "worlds";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_CLICK_MODE = "clickMode";
    private static final String UNKNOWN_WORLD = "unknown-world";
    private static final String UNKNOWN_PLAYER = "unknown-player";

    private final File file;
    private final JsonObject preferences;

    OrbitalMapPreferences(File file) {
        this.file = file;
        this.preferences = load();
    }

    static OrbitalMapPreferences current() {
        return new OrbitalMapPreferences(
            new File(Minecraft.getMinecraft().mcDataDir, "config/galaxia/orbital-map.json"));
    }

    static String currentWorldKey() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) return UNKNOWN_WORLD;
        ServerData serverData = minecraft.func_147104_D();
        if (serverData != null && serverData.serverIP != null && !serverData.serverIP.isEmpty()) {
            return "server:" + serverData.serverIP;
        }
        String worldName = minecraft.theWorld.getWorldInfo()
            .getWorldName();
        return worldName == null || worldName.isEmpty() ? UNKNOWN_WORLD : "world:" + worldName;
    }

    static String currentPlayerKey() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.getUniqueID() == null) return UNKNOWN_PLAYER;
        return player.getUniqueID()
            .toString();
    }

    OrbitalMapClickMode clickMode(String worldKey, String playerKey) {
        JsonObject playerPreferences = playerPreferences(worldKey, playerKey, false);
        if (playerPreferences == null) return OrbitalMapClickMode.HIERARCHY;
        return OrbitalMapClickMode.fromJson(playerPreferences.get(KEY_CLICK_MODE));
    }

    void setClickMode(String worldKey, String playerKey, OrbitalMapClickMode clickMode) {
        playerPreferences(worldKey, playerKey, true)
            .addProperty(KEY_CLICK_MODE, (clickMode == null ? OrbitalMapClickMode.HIERARCHY : clickMode).name());
        save();
    }

    private JsonObject load() {
        if (!file.isFile()) return new JsonObject();
        try (FileReader reader = new FileReader(file)) {
            JsonObject loaded = GSON.fromJson(reader, JsonObject.class);
            return loaded == null ? new JsonObject() : loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load orbital map preferences: " + file, e);
        }
    }

    private void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create orbital map preference directory: " + parent);
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(preferences, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save orbital map preferences: " + file, e);
        }
    }

    private JsonObject playerPreferences(String worldKey, String playerKey, boolean create) {
        JsonObject worldPreferences = worldPreferences(worldKey, create);
        if (worldPreferences == null) return null;
        JsonObject players = object(worldPreferences, KEY_PLAYERS, create);
        if (players == null) return null;
        String resolvedPlayerKey = playerKey == null || playerKey.isEmpty() ? UNKNOWN_PLAYER : playerKey;
        return object(players, resolvedPlayerKey, create);
    }

    private JsonObject worldPreferences(String worldKey, boolean create) {
        JsonObject worlds = object(preferences, KEY_WORLDS, create);
        if (worlds == null) return null;
        String resolvedWorldKey = worldKey == null || worldKey.isEmpty() ? UNKNOWN_WORLD : worldKey;
        return object(worlds, resolvedWorldKey, create);
    }

    private static JsonObject object(JsonObject parent, String key, boolean create) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonObject()) return existing.getAsJsonObject();
        if (!create) return null;
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }
}
