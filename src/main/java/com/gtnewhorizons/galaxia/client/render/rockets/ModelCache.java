package com.gtnewhorizons.galaxia.client.render.rockets;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

public class ModelCache {

    private static final Map<ResourceLocation, IModelCustom> CACHE = new HashMap<>();

    public static IModelCustom get(ResourceLocation location) {

        return CACHE.computeIfAbsent(location, AdvancedModelLoader::loadModel);
    }
}
