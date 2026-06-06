package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

class SettingsGroupRegistryTest {

    @Test
    void createSupportsDefaultAndExplicitDisplayName() {
        SettingsGroupRegistry registry = new SettingsGroupRegistry();
        ModuleSettings defaultSettings = new DummySettings("default");
        ModuleSettings explicitSettings = new DummySettings("explicit");

        SettingsGroup defaultNamed = registry.create(FacilityModuleKind.MINER, defaultSettings);
        SettingsGroup explicitNamed = registry.create(FacilityModuleKind.MINER, "Ore Priority", explicitSettings);

        assertEquals((short) 1, defaultNamed.id());
        assertEquals("MINER #1", defaultNamed.displayName());
        assertEquals((short) 2, explicitNamed.id());
        assertEquals("Ore Priority", explicitNamed.displayName());
    }

    @Test
    void createRejectsNullPayloadsAndInvalidNextGroupId() {
        SettingsGroupRegistry registry = new SettingsGroupRegistry();
        ModuleSettings settings = new DummySettings("cfg");

        IllegalArgumentException nullKind = assertThrows(
            IllegalArgumentException.class,
            () -> registry.create(null, settings));
        assertTrue(
            nullKind.getMessage()
                .contains("kind must not be null"));

        IllegalArgumentException nullSettings = assertThrows(
            IllegalArgumentException.class,
            () -> registry.create(FacilityModuleKind.MINER, (ModuleSettings) null));
        assertTrue(
            nullSettings.getMessage()
                .contains("settings must not be null"));

        IllegalArgumentException invalidNextGroupId = assertThrows(
            IllegalArgumentException.class,
            () -> registry.setNextGroupId((short) 0));
        assertTrue(
            invalidNextGroupId.getMessage()
                .contains("nextGroupId must be > 0"));
    }

    @Test
    void requireSupportsMissingAndWrongKindFailures() {
        SettingsGroupRegistry registry = new SettingsGroupRegistry();
        SettingsGroup group = registry.create(FacilityModuleKind.MINER, new DummySettings("a"));

        assertSame(group, registry.get(group.id()));
        assertNull(registry.get((short) 77));

        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> registry.require((short) 77));
        assertTrue(
            missing.getMessage()
                .contains("groupId=77"));

        IllegalStateException wrongKind = assertThrows(
            IllegalStateException.class,
            () -> registry.require(group.id(), FacilityModuleKind.STORAGE));
        assertTrue(
            wrongKind.getMessage()
                .contains("expectedKind=STORAGE"));
        assertTrue(
            wrongKind.getMessage()
                .contains("actualKind=MINER"));
    }

    @Test
    void removeMemberDeletesGroupWhenLastMemberLeaves() {
        SettingsGroupRegistry registry = new SettingsGroupRegistry();
        SettingsGroup group = registry.create(FacilityModuleKind.STORAGE, "Buffer", new DummySettings("b"));
        short groupId = group.id();
        StationTileCoord one = StationTileCoord.of(1, 1);
        StationTileCoord two = StationTileCoord.of(2, 2);

        registry.addMember(groupId, one);
        registry.addMember(groupId, two);

        boolean deletedAfterFirst = registry.removeMember(groupId, one);
        assertFalse(deletedAfterFirst);
        assertSame(group, registry.require(groupId));

        boolean deletedAfterSecond = registry.removeMember(groupId, two);
        assertTrue(deletedAfterSecond);
        assertNull(registry.get(groupId));

        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> registry.require(groupId));
        assertTrue(
            missing.getMessage()
                .contains("groupId=" + groupId));
    }

    @Test
    void noSilentFailuresForMutators() {
        SettingsGroupRegistry registry = new SettingsGroupRegistry();
        short missingGroupId = 42;
        StationTileCoord coord = StationTileCoord.of(5, 6);

        assertThrows(IllegalStateException.class, () -> registry.addMember(missingGroupId, coord));
        assertThrows(IllegalStateException.class, () -> registry.removeMember(missingGroupId, coord));
        assertThrows(IllegalStateException.class, () -> registry.rename(missingGroupId, "Name"));
        assertThrows(
            IllegalStateException.class,
            () -> registry.updateSettings(missingGroupId, new DummySettings("c")));
        assertThrows(IllegalStateException.class, () -> registry.delete(missingGroupId));
    }

    private static final class DummySettings implements ModuleSettings {

        private final String marker;

        private DummySettings(String marker) {
            this.marker = marker;
        }

        @Override
        public void applyTo(ModuleInstance instance) {}

        @Override
        public ModuleSettings from(ModuleInstance instance) {
            return this;
        }

        @Override
        public ModuleSettings copy() {
            return this;
        }

        @Override
        public String toString() {
            return marker;
        }
    }
}
