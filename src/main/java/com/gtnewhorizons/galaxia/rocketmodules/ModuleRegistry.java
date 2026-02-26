package com.gtnewhorizons.galaxia.rocketmodules;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import com.github.bsideup.jabel.Desugar;

public class ModuleRegistry {

    @Desugar
    public record ModuleInfo(IModelCustom model, ResourceLocation texture, double height, double width) {}

    private static final Map<Integer, ModuleInfo> MODULES = new HashMap<>();

    public static void registerModule(int id, String name, double height, double width) {
        ResourceLocation modelLoc = LocationGalaxia(String.format("textures/model/modules/%s/model.obj", name));
        ResourceLocation texLoc = LocationGalaxia(String.format("textures/model/modules/%s/texture.png", name));
        IModelCustom model = AdvancedModelLoader.loadModel(modelLoc);
        MODULES.put(id, new ModuleInfo(model, texLoc, height, width));
    }

    public static ModuleInfo getModule(int id) {
        return MODULES.get(id);
    }

    static {
        registerModule(0, "fuel_tank_3x5x3", 5.0, 3);
        registerModule(1, "capsule_3x2.5x3", 2.5, 3);
        registerModule(2, "storage_unit_3x4x3", 4, 3);
        registerModule(3, "engine_3x1x3", 0.5, 3);
    }
}
