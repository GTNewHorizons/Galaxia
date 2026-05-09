package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

class SettingsGroupTest {

    @Test
    void constructorDerivesDefaultDisplayNameFromKindAndId() {
        ModuleSettings settings = new DummySettings();

        SettingsGroup group = new SettingsGroup((short) 12, FacilityModuleKind.MINER, settings);

        assertEquals("MINER #12", group.displayName());
        assertEquals(FacilityModuleKind.MINER, group.kind());
        assertSame(settings, group.settings());
    }

    @Test
    void constructorRejectsInvalidIdentityAndPayload() {
        ModuleSettings settings = new DummySettings();
        IllegalArgumentException idException = assertThrows(
            IllegalArgumentException.class,
            () -> new SettingsGroup((short) 0, FacilityModuleKind.MINER, settings));
        assertTrue(
            idException.getMessage()
                .contains("id must be > 0"));

        IllegalArgumentException kindException = assertThrows(
            IllegalArgumentException.class,
            () -> new SettingsGroup((short) 1, null, settings));
        assertTrue(
            kindException.getMessage()
                .contains("kind must not be null"));

        IllegalArgumentException settingsException = assertThrows(
            IllegalArgumentException.class,
            () -> new SettingsGroup((short) 1, FacilityModuleKind.MINER, (ModuleSettings) null));
        assertTrue(
            settingsException.getMessage()
                .contains("settings must not be null"));
    }

    @Test
    void displayNameRequiresNonBlankText() {
        ModuleSettings settings = new DummySettings();
        SettingsGroup group = new SettingsGroup((short) 2, FacilityModuleKind.POWER, settings);

        IllegalArgumentException createException = assertThrows(
            IllegalArgumentException.class,
            () -> new SettingsGroup((short) 2, FacilityModuleKind.POWER, "   ", settings));
        assertTrue(
            createException.getMessage()
                .contains("displayName must not be blank"));

        group.setDisplayName("  Priority Power  ");
        assertEquals("Priority Power", group.displayName());

        IllegalArgumentException renameException = assertThrows(
            IllegalArgumentException.class,
            () -> group.setDisplayName(" "));
        assertTrue(
            renameException.getMessage()
                .contains("displayName must not be blank"));
    }

    @Test
    void memberOperationsThrowWithContextAndReportEmptyState() {
        SettingsGroup group = new SettingsGroup((short) 4, FacilityModuleKind.STORAGE, new DummySettings());
        StationTileCoord coord = StationTileCoord.of(1, 2);

        group.addMember(coord);
        assertTrue(
            group.members()
                .contains(coord));

        IllegalStateException duplicateException = assertThrows(
            IllegalStateException.class,
            () -> group.addMember(coord));
        assertTrue(
            duplicateException.getMessage()
                .contains("groupId=4"));
        assertTrue(
            duplicateException.getMessage()
                .contains("kind=STORAGE"));

        boolean emptied = group.removeMember(coord);
        assertTrue(emptied);

        IllegalStateException missingException = assertThrows(
            IllegalStateException.class,
            () -> group.removeMember(coord));
        assertTrue(
            missingException.getMessage()
                .contains("groupId=4"));
        assertTrue(
            missingException.getMessage()
                .contains("member="));
    }

    private static final class DummySettings implements ModuleSettings {

        @Override
        public void applyTo(ModuleInstance instance) {}

        @Override
        public ModuleSettings from(ModuleInstance instance) {
            return this;
        }
    }
}
