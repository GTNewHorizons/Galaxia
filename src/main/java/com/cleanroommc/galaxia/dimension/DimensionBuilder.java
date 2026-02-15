package com.cleanroommc.galaxia.dimension;

import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.WorldProvider;

import java.util.HashMap;
import java.util.Map;

public class DimensionBuilder {

    private static final Map<String, DimensionDef> BY_NAME = new HashMap<>();
    private static final Map<Integer, DimensionDef> BY_ID = new HashMap<>();

    public static DimensionDef get(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

    public static DimensionDef get(int id) {
        return BY_ID.get(id);
    }

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

        DimensionDef def = new DimensionDef(name, id, providerClass, keepLoaded);

        BY_NAME.put(name.toLowerCase(), def);
        BY_ID.put(id, def);

        return def;
    }
}
