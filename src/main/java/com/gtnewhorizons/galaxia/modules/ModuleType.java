package com.gtnewhorizons.galaxia.modules;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ModuleType {

    private final String id;
    private final String unlocalizedName;
    private final int internalSizeX, internalSizeY, internalSizeZ;
    private final int wallThickness;
    private final ResourceLocation textureLocation;
    private final ResourceLocation modelLocation;
    private final float scale;

    private ModuleType(Builder b) {
        this.id = b.id;
        this.unlocalizedName = b.unlocalizedName != null ? b.unlocalizedName : "module." + b.id;
        this.internalSizeX = b.internalSizeX;
        this.internalSizeY = b.internalSizeY;
        this.internalSizeZ = b.internalSizeZ;
        this.wallThickness = b.wallThickness;
        this.textureLocation = b.textureLocation;
        this.modelLocation = b.modelLocation;
        this.scale = b.scale;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    public int getInternalSizeX() {
        return internalSizeX;
    }

    public int getInternalSizeY() {
        return internalSizeY;
    }

    public int getInternalSizeZ() {
        return internalSizeZ;
    }

    public int getWallThickness() {
        return wallThickness;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation != null ? textureLocation
            : new ResourceLocation(Galaxia.MODID, "textures/models/modules/" + id + ".png");
    }

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public float getScale() {
        return scale;
    }

    @SideOnly(Side.CLIENT)
    private IModelCustom model;

    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null && modelLocation != null) {
            try {
                model = AdvancedModelLoader.loadModel(modelLocation);
            } catch (Exception ignored) {
                Galaxia.LOG.error("[Galaxia] Failed to load OBJ model: {}", modelLocation);
            }
        }
        return model;
    }

    public static class Builder {

        private final String id;
        private String unlocalizedName;
        private int internalSizeX = 3, internalSizeY = 3, internalSizeZ = 3;
        private int wallThickness = 1;
        private ResourceLocation textureLocation;
        private ResourceLocation modelLocation;
        private float scale = 0.0625F;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.unlocalizedName = name;
            return this;
        }

        public Builder internalSize(int x, int y, int z) {
            this.internalSizeX = x;
            this.internalSizeY = y;
            this.internalSizeZ = z;
            return this;
        }

        public Builder wallThickness(int t) {
            this.wallThickness = t;
            return this;
        }

        public Builder texture(String path) {
            this.textureLocation = new ResourceLocation(Galaxia.MODID, path);
            return this;
        }

        public Builder model(String path) {
            this.modelLocation = new ResourceLocation(Galaxia.MODID, path);
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public ModuleType build() {
            if (modelLocation == null) {
                throw new IllegalStateException("Module " + id + " must have an OBJ model");
            }
            return new ModuleType(this);
        }
    }
}
