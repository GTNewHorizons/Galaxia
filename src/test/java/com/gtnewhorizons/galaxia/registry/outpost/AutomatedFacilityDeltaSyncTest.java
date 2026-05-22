package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AutomatedFacilityDeltaSyncTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    @Test
    void needsFullSyncForNewPlayer() {
        AutomatedFacility facility = createFacility();
        assertTrue(facility.needsFullSyncFor(PLAYER_A));
        assertTrue(facility.needsFullSyncFor(PLAYER_B));
    }

    @Test
    void markSyncedPreventsFullSync() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);
        facility.clean();

        assertFalse(facility.needsFullSyncFor(PLAYER_A));
        assertTrue(facility.needsFullSyncFor(PLAYER_B), "Player B should still need full sync");
    }

    @Test
    void addModuleMarksDirty() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);
        facility.clean();

        ModuleInstance module = addModule(facility, FacilityModuleKind.STORAGE);
        assertTrue(facility.isDirty());

        List<ModuleInstance> dirty = facility.drainDirtyModules();
        facility.clean();
        assertEquals(1, dirty.size());
        assertEquals(module.id, dirty.get(0).id);

        assertFalse(facility.isDirty(), "must not be dirty after drain");
    }

    @Test
    void removeModuleMarksDirty() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);
        facility.clean();

        ModuleInstance module = addModule(facility, FacilityModuleKind.STORAGE);
        facility.drainDirtyModules(); // clear add dirtiness

        facility.removeModule(module.id);
        assertTrue(facility.isDirty());

        List<ModuleInstance.ID> removed = facility.drainRemovedIds();
        facility.clean();
        assertEquals(1, removed.size());
        assertEquals(module.id, removed.get(0));

        assertFalse(facility.isDirty(), "must not be dirty after drain");
    }

    @Test
    void multipleAddsAccumulate() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);
        facility.clean();

        ModuleInstance a = addModule(facility, FacilityModuleKind.STORAGE);
        ModuleInstance b = addModule(facility, FacilityModuleKind.TANK);
        ModuleInstance c = addModule(facility, FacilityModuleKind.BATTERY);

        assertTrue(facility.isDirty());
        List<ModuleInstance> dirty = facility.drainDirtyModules();
        facility.clean();
        assertEquals(3, dirty.size());
        assertFalse(facility.isDirty());
    }

    @Test
    void markModuleDirtyTracksExternalMutations() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);

        ModuleInstance module = addModule(facility, FacilityModuleKind.STORAGE);
        facility.drainDirtyModules();

        // Simulate external mutation (e.g. SET_TIER via packet)
        facility.markModuleDirty(module.id);
        assertTrue(facility.isDirty());

        List<ModuleInstance> dirty = facility.drainDirtyModules();
        assertEquals(1, dirty.size());
        assertEquals(module.id, dirty.get(0).id);
    }

    @Test
    void addThenRemoveSameModuleClearsAddDirtiness() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);

        ModuleInstance module = addModule(facility, FacilityModuleKind.STORAGE);
        assertTrue(facility.isDirty());

        facility.removeModule(module.id);
        assertTrue(facility.isDirty());

        List<ModuleInstance> dirty = facility.drainDirtyModules();
        assertTrue(dirty.isEmpty(), "add dirtiness should be cleared by remove");

        List<ModuleInstance.ID> removed = facility.drainRemovedIds();
        assertEquals(1, removed.size());
    }

    @Test
    void drainClearsOnlyRespectiveSets() {
        AutomatedFacility facility = createFacility();
        facility.markSyncedFor(PLAYER_A);

        ModuleInstance a = addModule(facility, FacilityModuleKind.STORAGE);
        ModuleInstance b = addModule(facility, FacilityModuleKind.TANK);
        facility.removeModule(b.id);

        // Both dirty and removed should be populated
        assertTrue(facility.isDirty());

        List<ModuleInstance.ID> removed = facility.drainRemovedIds();
        assertEquals(1, removed.size());

        // Still dirty because addModule marked `a` dirty
        assertTrue(facility.isDirty());

        List<ModuleInstance> dirty = facility.drainDirtyModules();
        assertEquals(1, dirty.size());
        assertEquals(a.id, dirty.get(0).id);

        facility.clean();

        assertFalse(facility.isDirty());
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addModule(AutomatedFacility facility, FacilityModuleKind kind) {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            kind,
            StationTileCoord.of(1 + kind.ordinal(), 0),
            ModuleShape.SINGLE,
            kind.defaultTier());
        facility.addModule(module);
        return module;
    }
}
