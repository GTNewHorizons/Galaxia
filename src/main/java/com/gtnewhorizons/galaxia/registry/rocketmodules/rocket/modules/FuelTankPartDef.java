package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public record FuelTankPartDef(int id, String name, double width, double height, double weight, double fuelCapacity,
    ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
