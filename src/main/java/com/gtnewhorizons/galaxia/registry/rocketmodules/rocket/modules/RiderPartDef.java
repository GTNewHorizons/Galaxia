package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public record RiderPartDef(int id, String name, double width, double height, double weight, int riderCapacity,
    ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
