package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import net.minecraft.util.ResourceLocation;

public record RocketPartDef(
    int id,
    String name,
    RocketPartType type,
    double width,
    double height,
    double weight,
    double fuelCapacity,
    double thrust,
    int decouplerStage,
    int riderCapacity,
    ResourceLocation model,
    ResourceLocation texture
) {
    public RocketPartDef {
        if (type == RocketPartType.ENGINE && thrust <= 0)
            throw new IllegalArgumentException("Engine needs positive thrust");
        if (type == RocketPartType.FUEL_TANK && fuelCapacity <= 0)
            throw new IllegalArgumentException("Fuel tank needs positive capacity");
        if (type == RocketPartType.DECOUPLER && decouplerStage < 0)
            throw new IllegalArgumentException("Decoupler needs stage >=0");
        if (type == RocketPartType.RIDER && riderCapacity <= 0)
            throw new IllegalArgumentException("Rider needs capacity >0");
    }

    public int getWidthCells() {
        return (int) Math.ceil(width / 3.0);
    }

    public int getHeightCells() {
        return (int) Math.ceil(height / 3.0);
    }
}
