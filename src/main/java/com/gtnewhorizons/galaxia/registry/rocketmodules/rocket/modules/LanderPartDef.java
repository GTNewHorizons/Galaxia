package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public record LanderPartDef(int id, String name, double width, double height, double weight,
    ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
