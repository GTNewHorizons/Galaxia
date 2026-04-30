package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;

public class RiderModule extends RocketModule {

    private final int capacity;
    private final double yOffset;

    public RiderModule(int id, String name, double height, double width, double weight, String modelName,
        double yOffset, int capacity) {
        super(id, name, height, width, weight, modelName);
        this.yOffset = yOffset;
        this.capacity = capacity;
        setCategory(EnumModuleCategory.RIDER);
        ModuleRegistry.register(this);
    }

    public int getCapacity() {
        return capacity;
    }

    public double getYOffset() {
        return yOffset;
    }
}
