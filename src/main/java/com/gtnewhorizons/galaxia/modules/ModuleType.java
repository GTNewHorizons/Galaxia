package com.gtnewhorizons.galaxia.modules;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Class for Rocket Module Types
 */
public class ModuleType {

    private final String id;
    private final int internalSizeX, internalSizeY, internalSizeZ;
    private final int wallThickness;
    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final float scale;
    private final double offsetX, offsetY, offsetZ;

    private final AxisAlignedBB modelBounds;

    private ModuleType(Builder b) {
        this.id = b.id;
        this.internalSizeX = b.internalSizeX;
        this.internalSizeY = b.internalSizeY;
        this.internalSizeZ = b.internalSizeZ;
        this.wallThickness = b.wallThickness;
        this.modelLocation = b.modelLocation;
        this.textureLocation = b.textureLocation;
        this.scale = b.scale;
        this.offsetX = b.offsetX;
        this.offsetY = b.offsetY;
        this.offsetZ = b.offsetZ;

        this.modelBounds = AxisAlignedBB
            .getBoundingBox(b.modelMinX, b.modelMinY, b.modelMinZ, b.modelMaxX, b.modelMaxY, b.modelMaxZ);
    }

    /**
     * Creates a new builder for the module type to be created with
     * 
     * @param id The ID of the new module
     * @return A new unconfigured builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Getter for ID
     * 
     * @return ID string
     */
    public String getId() {
        return id;
    }

    /**
     * Getter for Internal X size
     * 
     * @return Internal X size
     */
    public int getInternalSizeX() {
        return internalSizeX;
    }

    /**
     * Getter for Internal Y size
     * 
     * @return Internal Y size
     */
    public int getInternalSizeY() {
        return internalSizeY;
    }

    /**
     * Getter for Internal Z size
     * 
     * @return Internal Z size
     */
    public int getInternalSizeZ() {
        return internalSizeZ;
    }

    /**
     * Getter for Wall Thickness
     * 
     * @return Wall Thickness
     */
    public int getWallThickness() {
        return wallThickness;
    }

    /**
     * Getter for the X offset
     * 
     * @return The X offset
     */
    public double getOffsetX() {
        return offsetX;
    }

    /**
     * Getter for the Y offset
     * 
     * @return The Y offset
     */
    public double getOffsetY() {
        return offsetY;
    }

    /**
     * Getter for the Z offset
     * 
     * @return The Z offset
     */
    public double getOffsetZ() {
        return offsetZ;
    }

    /**
     * Getter for the scale of the module
     * 
     * @return The scale of the module
     */
    public float getScale() {
        return scale;
    }

    /**
     * Getter for the texture location
     * 
     * @return The texture ResourceLocation
     */
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @SideOnly(Side.CLIENT)
    private IModelCustom model;

    /**
     * Getter for the model of the type
     * 
     * @return The model of the type
     */
    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null && modelLocation != null) {
            model = AdvancedModelLoader.loadModel(modelLocation);
        }
        return model;
    }

    /**
     * Getter for the bounding box of the model
     * 
     * @return The bounding box of the model
     */
    public AxisAlignedBB getModelBounds() {
        return modelBounds;
    }

    /**
     * Module Type builder class
     */
    public static class Builder {

        private final String id;
        private int internalSizeX = 3, internalSizeY = 3, internalSizeZ = 3;
        private int wallThickness = 1;
        private ResourceLocation modelLocation;
        private ResourceLocation textureLocation;
        private float scale = 1F;
        private double offsetX = 0, offsetY = 0, offsetZ = 0;

        private double modelMinX = -0.5, modelMinY = -0.5, modelMinZ = -0.5;
        private double modelMaxX = 0.5, modelMaxY = 0.5, modelMaxZ = 0.5;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the internal size
         * 
         * @param x X size
         * @param y Y size
         * @param z Z size
         * @return Configured builder
         */
        public Builder internalSize(int x, int y, int z) {
            this.internalSizeX = x;
            this.internalSizeY = y;
            this.internalSizeZ = z;
            return this;
        }

        /**
         * Sets the wall thickness
         * 
         * @param t Desired wall thickness
         * @return Configured builder
         */
        public Builder wallThickness(int t) {
            this.wallThickness = t;
            return this;
        }

        /**
         * Sets the model of the module
         * 
         * @param path Desired model path
         * @return Configured builder
         */
        public Builder model(String path) {
            this.modelLocation = LocationGalaxia(path);
            return this;
        }

        /**
         * Sets the texture path
         * 
         * @param path The texture path
         * @return Configured builder
         */
        public Builder texture(String path) {
            this.textureLocation = LocationGalaxia(path);
            return this;
        }

        /**
         * Sets the offset
         * 
         * @param x The x offset
         * @param y The y offset
         * @param z The z offset
         * @return Configured builder
         */
        public Builder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        /**
         * Sets the scale of the model
         * 
         * @param scale The desired scale multiplier
         * @return Configured builder
         */
        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        /**
         * Sets the bounds of the model
         * 
         * @param minX Minimum X bound
         * @param minY Minimum Y bound
         * @param minZ Minimum Z bound
         * @param maxX Maximum X bound
         * @param maxY Maximum Y bound
         * @param maxZ Maximum Z bound
         * @return Configured builder
         */
        public Builder modelBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.modelMinX = minX;
            this.modelMinY = minY;
            this.modelMinZ = minZ;
            this.modelMaxX = maxX;
            this.modelMaxY = maxY;
            this.modelMaxZ = maxZ;
            return this;
        }

        /**
         * Builds the Module Type based on the builder fields
         * 
         * @return ModuleType with configurations
         * @throws IllegalStateException - Model location or texture location are null
         */
        public ModuleType build() {
            if (modelLocation == null) throw new IllegalStateException("Module " + id + " must have .model()");
            if (textureLocation == null) throw new IllegalStateException("Module " + id + " must have .texture()");
            return new ModuleType(this);
        }
    }
}
