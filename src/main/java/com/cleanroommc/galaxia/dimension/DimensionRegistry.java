package com.cleanroommc.galaxia.dimension;

import java.util.HashMap;
import java.util.Map;

public final class DimensionRegistry {

    private static final Map<String, DimensionDef> BY_NAME = new HashMap<>();
    private static final Map<Integer, DimensionDef> BY_ID = new HashMap<>();

    static void register(DimensionDef def) {
        BY_NAME.put(def.name.toLowerCase(), def);
        BY_ID.put(def.id, def);
    }

    public static DimensionDef get(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

    public static DimensionDef get(int id) {
        return BY_ID.get(id);
    }
}
