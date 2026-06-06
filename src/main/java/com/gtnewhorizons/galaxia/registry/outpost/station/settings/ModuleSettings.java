package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public interface ModuleSettings {

    void applyTo(ModuleInstance instance);

    ModuleSettings from(ModuleInstance instance);

    ModuleSettings copy();
}
