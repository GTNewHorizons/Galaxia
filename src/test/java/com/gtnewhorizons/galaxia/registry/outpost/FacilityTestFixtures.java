package com.gtnewhorizons.galaxia.registry.outpost;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

public final class FacilityTestFixtures {

    private FacilityTestFixtures() {}

    public static void addModule(AutomatedFacility facility, ModuleInstance module) {
        facility.addModule(module);
    }

    public static ModuleInstance addFluidTank(AutomatedFacility facility) {
        GalaxiaTestBootstrap.ensureFacilityModules();
        var module = FacilityModuleKind.TANK
            .create(StationTileCoord.of(7, 7), FacilityModuleKind.TANK.defaultShape(), ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }
}
