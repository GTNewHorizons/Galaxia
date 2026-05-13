package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public record DecouplerPartDef(int id, String name, double width, double height, double weight, int decouplerStage,
    ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
