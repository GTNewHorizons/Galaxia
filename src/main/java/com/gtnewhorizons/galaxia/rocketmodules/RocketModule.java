package com.gtnewhorizons.galaxia.rocketmodules;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class RocketModule {

    private final int id;
    private final String name;
    private final double height;
    private final double width;
    private final double weight;
    private final String modelName;

    @SideOnly(Side.CLIENT)
    private IModelCustom model;
    @SideOnly(Side.CLIENT)
    private ResourceLocation texture;

    protected RocketModule(int id, String name, double height, double width, double weight, String modelName) {
        this.id = id;
        this.name = name;
        this.height = height;
        this.width = width;
        this.weight = weight;
        this.modelName = modelName;
        ModuleRegistry.register(this);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    public double getWeight() {
        return weight;
    }

    public double getSitOffset() {
        return 0;
    }

    public String getModelName() {
        return modelName;
    }

    public double getFuelCapacity() {
        return 0;
    }

    public double getThrust() {
        return 0;
    }

    public int getPassengerCapacity() {
        return 0;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/modules/" + modelName + "/model.obj");
            model = AdvancedModelLoader.loadModel(loc);
        }
        return model;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getTexture() {
        if (texture == null) {
            texture = LocationGalaxia("textures/model/modules/" + modelName + "/texture.png");
        }
        return texture;
    }

    public boolean isStackableWith(RocketModule other) {
        return getClass() == other.getClass();
    }
}
