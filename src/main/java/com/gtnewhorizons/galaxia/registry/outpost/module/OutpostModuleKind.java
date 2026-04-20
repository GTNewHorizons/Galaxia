package com.gtnewhorizons.galaxia.registry.outpost.module;

import net.minecraft.util.StatCollector;

public enum OutpostModuleKind {

    HAMMER,
    BIG_HAMMER,
    MINER,
    POWER;

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.outpost.module." + this.name()
                .toLowerCase());
    }

    public ModuleInstance createInstance() {
        return OutpostModuleRegistry.createInstance(this);
    }

    public ModuleInstance createInstance(ModuleInstance.ID id) {
        return OutpostModuleRegistry.createInstance(id, this);
    }

    public ModuleInstance createInstance(ModuleComponent component) {
        return OutpostModuleRegistry.createInstance(null, this, component);
    }
}
