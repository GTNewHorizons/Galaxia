package com.cleanroommc.galaxia.dimension;

import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.WorldProvider;

public class DimensionBuilder {

    private int id;
    private String name;
    private Class<? extends WorldProvider> providerClass;
    private boolean keepLoaded = true;

    public DimensionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public DimensionBuilder id(int id) {
        this.id = id;
        return this;
    }

    public DimensionBuilder provider(Class<? extends WorldProvider> providerClass) {
        this.providerClass = providerClass;
        return this;
    }

    public DimensionBuilder keepLoaded(boolean keep) {
        this.keepLoaded = keep;
        return this;
    }

    public DimensionDef build() {
        if (name == null) throw new IllegalStateException("Name required");
        if (providerClass == null) throw new IllegalStateException("Provider required");

        DimensionManager.registerProviderType(id, providerClass, keepLoaded);

        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
        }

        DimensionDef def = new DimensionDef(
            name,
            id,
            providerClass,
            keepLoaded
        );

        DimensionRegistry.register(def);
        return def;
    }
}
