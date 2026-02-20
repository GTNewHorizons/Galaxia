package com.gtnewhorizons.galaxia.modules;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.core.Galaxia;

public class ModuleType {

    private final String id;
    private final String unlocalizedName;
    private final int internalSizeX, internalSizeY, internalSizeZ;
    private final int wallThickness;
    private final ResourceLocation modelLocation;

    private ModuleType(Builder b) {
        this.id = b.id;
        this.unlocalizedName = b.unlocalizedName != null ? b.unlocalizedName : "module." + b.id;
        this.internalSizeX = b.internalSizeX;
        this.internalSizeY = b.internalSizeY;
        this.internalSizeZ = b.internalSizeZ;
        this.wallThickness = b.wallThickness;
        this.modelLocation = b.modelLocation;
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

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public static class Builder {

        private final String id;
        private String unlocalizedName;
        private int internalSizeX = 2, internalSizeY = 2, internalSizeZ = 2;
        private int wallThickness = 1;
        private ResourceLocation modelLocation;

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

        public Builder model(String path) {
            this.modelLocation = new ResourceLocation(Galaxia.MODID, path);
            return this;
        }

        public ModuleType build() {
            return new ModuleType(this);
        }
    }
}
