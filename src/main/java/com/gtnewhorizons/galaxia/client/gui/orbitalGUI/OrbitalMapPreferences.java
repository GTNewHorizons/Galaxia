package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

final class OrbitalMapPreferences {

    private static final String DISABLE_HIERARCHICAL_VIEW = "disableHierarchicalView";
    private static final String UNKNOWN_PLAYER = "unknown-player";

    private final File file;
    private final Properties properties = new Properties();

    OrbitalMapPreferences(File file) {
        this.file = file;
        load();
    }

    static OrbitalMapPreferences current() {
        return new OrbitalMapPreferences(
            new File(Minecraft.getMinecraft().mcDataDir, "config/galaxia/orbital-map.properties"));
    }

    static String currentPlayerKey() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.getUniqueID() == null) return UNKNOWN_PLAYER;
        return player.getUniqueID()
            .toString();
    }

    boolean disableHierarchicalView(String playerKey) {
        return Boolean.parseBoolean(properties.getProperty(key(playerKey, DISABLE_HIERARCHICAL_VIEW), "false"));
    }

    void setDisableHierarchicalView(String playerKey, boolean value) {
        properties.setProperty(key(playerKey, DISABLE_HIERARCHICAL_VIEW), Boolean.toString(value));
        save();
    }

    private void load() {
        if (!file.isFile()) return;
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load orbital map preferences: " + file, e);
        }
    }

    private void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create orbital map preference directory: " + parent);
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "Galaxia orbital map preferences");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save orbital map preferences: " + file, e);
        }
    }

    private static String key(String playerKey, String setting) {
        String resolvedPlayerKey = playerKey == null || playerKey.isEmpty() ? UNKNOWN_PLAYER : playerKey;
        return resolvedPlayerKey + "." + setting;
    }
}
