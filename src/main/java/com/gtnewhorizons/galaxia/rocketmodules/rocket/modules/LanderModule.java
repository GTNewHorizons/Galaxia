package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class LanderModule extends RocketModule {

    private double sitOffset;
    private int capacity;

    public LanderModule(int id, String name, double height, double width, double weight, String modelName,
        double sitOffset, int capacity) {
        super(id, name, height, width, weight, modelName);
        this.sitOffset = sitOffset;
        this.capacity = capacity;
        setCategory(EnumModuleCategory.LANDER);
        ModuleRegistry.register(this);
    }

    public double getSitOffset() {
        return sitOffset;
    }

    public int getCapacity() {
        return capacity;
    }

}
