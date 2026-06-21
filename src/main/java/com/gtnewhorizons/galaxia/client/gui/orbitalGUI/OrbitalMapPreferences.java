package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class OrbitalMapPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String CLICK_MODE_HIERARCHY = "hierarchy";
    private static final String CLICK_MODE_FOLLOW = "follow";
    private static final String UNKNOWN_PLAYER = "unknown-player";

    private final File file;
    private final PreferencesJson preferences;

    OrbitalMapPreferences(File file) {
        this.file = file;
        this.preferences = load();
    }

    static OrbitalMapPreferences current() {
        return new OrbitalMapPreferences(
            new File(Minecraft.getMinecraft().mcDataDir, "config/galaxia/orbital-map.json"));
    }

    static String currentPlayerKey() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.getUniqueID() == null) return UNKNOWN_PLAYER;
        return player.getUniqueID()
            .toString();
    }

    boolean disableHierarchicalView(String playerKey) {
        return CLICK_MODE_FOLLOW.equals(playerPreferences(playerKey).clickMode);
    }

    void setDisableHierarchicalView(String playerKey, boolean value) {
        playerPreferences(playerKey).clickMode = value ? CLICK_MODE_FOLLOW : CLICK_MODE_HIERARCHY;
        save();
    }

    private PreferencesJson load() {
        if (!file.isFile()) return new PreferencesJson();
        try (FileReader reader = new FileReader(file)) {
            PreferencesJson loaded = GSON.fromJson(reader, PreferencesJson.class);
            return loaded == null ? new PreferencesJson() : loaded.normalized();
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

    private PlayerPreferencesJson playerPreferences(String playerKey) {
        String resolvedPlayerKey = playerKey == null || playerKey.isEmpty() ? UNKNOWN_PLAYER : playerKey;
        PlayerPreferencesJson playerPreferences = preferences.players.get(resolvedPlayerKey);
        if (playerPreferences == null) {
            playerPreferences = new PlayerPreferencesJson();
            preferences.players.put(resolvedPlayerKey, playerPreferences);
        }
        return playerPreferences.normalized();
    }

    private static final class PreferencesJson {

        private Map<String, PlayerPreferencesJson> players = new HashMap<>();

        private PreferencesJson normalized() {
            if (players == null) players = new HashMap<>();
            players.replaceAll(
                (playerKey, preferences) -> preferences == null ? new PlayerPreferencesJson()
                    : preferences.normalized());
            return this;
        }
    }

    private static final class PlayerPreferencesJson {

        private String clickMode = CLICK_MODE_HIERARCHY;

        private PlayerPreferencesJson normalized() {
            if (!CLICK_MODE_FOLLOW.equals(clickMode)) clickMode = CLICK_MODE_HIERARCHY;
            return this;
        }
    }
}
