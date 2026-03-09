package com.gtnewhorizons.galaxia.rocketmodules.rocket.modules;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class StorageModule extends RocketModule {

    private int capacity;

    public StorageModule(int id, String name, double height, double width, double weight, String modelName,
        int capacity) {
        super(id, name, height, width, weight, modelName);
        this.capacity = capacity;
        setCategory(EnumModuleCategory.FUNCTIONAL);
    }

    public int getCapacity() {
        return this.capacity;
    }

}
