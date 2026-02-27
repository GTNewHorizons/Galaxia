package com.gtnewhorizons.galaxia.rocketmodules;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public enum ModuleType {

    FUEL_TANK(0, "fuel_tank_3x5x3", 5.0, 3.0, 0),
    CAPSULE(1, "capsule_3x2.5x3", 2.5, 3.0, -1.75),
    STORAGE(2, "storage_unit_3x4x3", 4.0, 3.0, 0),
    ENGINE(3, "engine_3x1x3", 0.5, 3.0, 0);

    private final int id;
    private final String modelName;
    private final double height;
    private final double width;
    private final double sitOffset;

    @SideOnly(Side.CLIENT)
    private IModelCustom model;

    @SideOnly(Side.CLIENT)
    private ResourceLocation texture;

    ModuleType(int id, String modelName, double height, double width, double sitOffset) {
        this.id = id;
        this.modelName = modelName;
        this.height = height;
        this.width = width;
        this.sitOffset = sitOffset;
    }

    public static ModuleType fromId(int id) {
        for (ModuleType t : values()) {
            if (t.id == id) return t;
        }
        return null;
    }

    public int getId() {
        return id;
    }

    public String getModelName() {
        return modelName;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public double getSitOffset() {
        return sitOffset;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null) {
            ResourceLocation loc = LocationGalaxia(String.format("textures/model/modules/%s/model.obj", modelName));
            model = AdvancedModelLoader.loadModel(loc);
        }
        return model;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getTexture() {
        if (texture == null) {
            texture = LocationGalaxia(String.format("textures/model/modules/%s/texture.png", modelName));
        }
        return texture;
    }
}
