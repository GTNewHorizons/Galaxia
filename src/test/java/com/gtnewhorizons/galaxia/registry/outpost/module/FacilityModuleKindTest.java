package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleKindTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void spaceshipDockPlaceholderIsRegisteredAndBuildable() {
        FacilityModuleKind kind = FacilityModuleKind.SPACESHIP_DOCK;

        assertTrue(FacilityModuleRegistry.isRegistered(kind));
        assertEquals(StationModuleCategory.LOGISTICS, kind.getCategory());
        assertEquals(ModuleTier.NONE, kind.defaultTier());
        assertEquals(ModuleShape.SINGLE, kind.defaultShape());
        assertTrue(kind.isAllowedOn(CelestialAsset.Kind.AUTOMATED_OUTPOST));
        assertTrue(kind.isAllowedOn(CelestialAsset.Kind.AUTOMATED_STATION));

        ModuleInstance module = kind.create(StationTileCoord.of(0, 0), kind.defaultShape(), kind.defaultTier());

        assertEquals(kind, module.kind());
        assertNotNull(module.component());
        assertDoesNotThrow(
            () -> FacilityModuleRegistry.get(kind)
                .applyBehavior()
                .accept(module, null));
    }
}
