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
    public record ModuleInfo(IModelCustom model, ResourceLocation texture, double height) {}

    private static final Map<Integer, ModuleInfo> MODULES = new HashMap<>();

    public static void registerModule(int id, String modelPath, String texturePath, double height) {
        ResourceLocation modelLoc = LocationGalaxia(modelPath);
        ResourceLocation texLoc = LocationGalaxia(texturePath);
        IModelCustom model = AdvancedModelLoader.loadModel(modelLoc);
        MODULES.put(id, new ModuleInfo(model, texLoc, height));
    }

    public static ModuleInfo getModule(int id) {
        return MODULES.get(id);
    }

    static {
        registerModule(
            0,
            "textures/model/modules/hub_3x3/model.obj",
            "textures/model/modules/hub_3x3/texture.png",
            5.0);
    }
}
