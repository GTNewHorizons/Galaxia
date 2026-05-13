package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public interface IRocketPartDef {

    int id();

    String name();

    double width();

    double height();

    double weight();

    ResourceLocation modelLocation();

    ResourceLocation textureLocation();

    default int getWidthCells() {
        return (int) Math.ceil(width() / 3.0);
    }

    default int getHeightCells() {
        return (int) Math.ceil(height() / 3.0);
    }
}
