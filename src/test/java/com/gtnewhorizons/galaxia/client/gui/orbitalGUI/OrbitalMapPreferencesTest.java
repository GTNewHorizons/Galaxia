package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OrbitalMapPreferencesTest {

    @TempDir
    private Path tempDir;

    @Test
    void clickModePersistsPerWorldAndPlayer() throws IOException {
        Path preferenceFile = tempDir.resolve("orbital-map.json");
        OrbitalMapPreferences preferences = new OrbitalMapPreferences(preferenceFile.toFile());

        preferences.setClickMode("world-a", "alice", OrbitalMapClickMode.FOLLOW);

        OrbitalMapPreferences reloaded = new OrbitalMapPreferences(preferenceFile.toFile());
        assertEquals(OrbitalMapClickMode.FOLLOW, reloaded.clickMode("world-a", "alice"));
        assertEquals(OrbitalMapClickMode.HIERARCHY, reloaded.clickMode("world-a", "bob"));
        assertEquals(OrbitalMapClickMode.HIERARCHY, reloaded.clickMode("world-b", "alice"));

        String saved = Files.readString(preferenceFile);
        assertTrue(saved.contains("\"worlds\""));
        assertTrue(saved.contains("\"world-a\""));
        assertTrue(saved.contains("\"players\""));
        assertTrue(saved.contains("\"alice\""));
        assertTrue(saved.contains("\"clickMode\""));
        assertTrue(saved.contains("\"FOLLOW\""));
    }
}
