package com.gtnewhorizons.galaxia.modules;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ModuleType {

    private final String id;
    private final int internalSizeX, internalSizeY, internalSizeZ;
    private final int wallThickness;
    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final float scale;

    private ModuleType(Builder b) {
        this.id = b.id;
        this.internalSizeX = b.internalSizeX;
        this.internalSizeY = b.internalSizeY;
        this.internalSizeZ = b.internalSizeZ;
        this.wallThickness = b.wallThickness;
        this.modelLocation = b.modelLocation;
        this.textureLocation = b.textureLocation;
        this.scale = b.scale;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
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

    public float getScale() {
        return scale;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @SideOnly(Side.CLIENT)
    private IModelCustom model;

    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null && modelLocation != null) {
            model = AdvancedModelLoader.loadModel(modelLocation);
        }
        return model;
    }

    public static class Builder {

        private final String id;
        private int internalSizeX = 3, internalSizeY = 3, internalSizeZ = 3;
        private int wallThickness = 1;
        private ResourceLocation modelLocation;
        private ResourceLocation textureLocation;
        private float scale = 1F;

        private Builder(String id) {
            this.id = id;
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

        public Builder model(String path) {
            this.modelLocation = new ResourceLocation(Galaxia.MODID, path);
            return this;
        }

        public Builder texture(String path) {
            this.textureLocation = new ResourceLocation(Galaxia.MODID, path);
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public ModuleType build() {
            if (modelLocation == null) throw new IllegalStateException("Module " + id + " must have .model()");
            if (textureLocation == null) throw new IllegalStateException("Module " + id + " must have .texture()");
            return new ModuleType(this);
        }
    }
}
