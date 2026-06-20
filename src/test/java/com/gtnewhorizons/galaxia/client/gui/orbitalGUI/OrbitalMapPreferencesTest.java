package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OrbitalMapPreferencesTest {

    @TempDir
    private Path tempDir;

    @Test
    void disableHierarchicalViewPersistsPerPlayer() {
        Path preferenceFile = tempDir.resolve("orbital-map.properties");
        OrbitalMapPreferences preferences = new OrbitalMapPreferences(preferenceFile.toFile());

        preferences.setDisableHierarchicalView("alice", true);

        OrbitalMapPreferences reloaded = new OrbitalMapPreferences(preferenceFile.toFile());
        assertTrue(reloaded.disableHierarchicalView("alice"));
        assertFalse(reloaded.disableHierarchicalView("bob"));
    }
}
