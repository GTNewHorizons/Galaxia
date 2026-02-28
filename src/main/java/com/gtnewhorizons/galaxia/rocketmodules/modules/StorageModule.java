package com.gtnewhorizons.galaxia.rocketmodules.modules;

import com.gtnewhorizons.galaxia.rocketmodules.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class StorageModule extends RocketModule implements IStackableModule {

    public StorageModule() {
        super(2, "Storage Unit", 4.0, 3.0, 900.0, "storage_unit_3x4x3");
    }

    @Override
    public int getMaxStackSize() {
        return 7;
    }
}
