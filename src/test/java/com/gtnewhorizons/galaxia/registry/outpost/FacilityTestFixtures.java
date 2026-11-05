package com.gtnewhorizons.galaxia.registry.outpost;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class FacilityTestFixtures {

    private FacilityTestFixtures() {}

    public static void addModule(AutomatedFacility facility, ModuleInstance module) {
        facility.addModule(module);
    }
}
