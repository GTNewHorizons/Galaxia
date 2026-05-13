package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public record EnginePartDef(int id, String name, double width, double height, double weight, double thrust,
    ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
