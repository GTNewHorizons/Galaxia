package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationCopyModuleActionModelTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void resolveReturnsSelectedModuleIdentity() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = FacilityModuleKind.POWER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        facility.addModule(module);
        facility.stationLayout()
            .place(module);

        StationCopyModuleActionModel.Source source = StationCopyModuleActionModel
            .resolve(facility, StationTileCoord.of(1, 0));

        assertNotNull(source);
        assertEquals(0, source.moduleIndex());
        assertEquals(module.id, source.moduleId());
    }

    @Test
    void resolveRejectsCoreAndEmptyTiles() {
        AutomatedFacility facility = createFacility();

        assertNull(StationCopyModuleActionModel.resolve(facility, StationTileCoord.CORE));
        assertNull(StationCopyModuleActionModel.resolve(facility, StationTileCoord.of(1, 0)));
        assertNull(StationCopyModuleActionModel.resolve(null, StationTileCoord.of(1, 0)));
        assertNull(StationCopyModuleActionModel.resolve(facility, null));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
