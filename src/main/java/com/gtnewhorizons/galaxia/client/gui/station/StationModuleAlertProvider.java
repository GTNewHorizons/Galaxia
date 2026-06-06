package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

@FunctionalInterface
public interface StationModuleAlertProvider {

    List<StationModuleAlert> alerts(AutomatedFacility facility, ModuleInstance module);
}
