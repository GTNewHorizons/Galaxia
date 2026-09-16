package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

final class SettingsGroupTest {

    @Test
    void idIsPositiveAndStable() {
        assertThrows(IllegalArgumentException.class, () -> new SettingsGroup.ID(0));
        assertThrows(IllegalArgumentException.class, () -> new SettingsGroup.ID(-1));
        assertEquals(12, new SettingsGroup.ID(12).value());
    }

    @Test
    void groupIsAnImmutableValidatedValue() {
        ModuleSettings settings = new DummySettings();
        SettingsGroup original = new SettingsGroup(
            new SettingsGroup.ID(7),
            FacilityModuleKind.MINER,
            "  Shared miners  ",
            settings);

        assertEquals("Shared miners", original.displayName());
        assertSame(settings, original.settings());

        SettingsGroup renamed = original.withDisplayName("Priority miners");
        assertNotSame(original, renamed);
        assertEquals("Shared miners", original.displayName());
        assertEquals("Priority miners", renamed.displayName());
        assertThrows(IllegalArgumentException.class, () -> original.withDisplayName(" "));
    }

    private static final class DummySettings implements ModuleSettings {
    }
}
