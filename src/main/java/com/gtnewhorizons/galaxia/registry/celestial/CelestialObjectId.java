package com.gtnewhorizons.galaxia.registry.celestial;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public enum CelestialObjectId {

    // TODO: Remove duplication with the dimension enum
    NOVA_CAELUM("novum_caelum", "Novum caelum", null),
    VAEL("vael", "Vael", null),
    ILIA("ilia", "Ilia", null),
    PROXIMA_CENTAURI("proxima_centauri", "Proxima centauri", null),
    ROMULUS("romulus", "Romulus", null),
    REMUS("remus", "Remus", null),
    EGORA("egora", "Egora", null),
    PANSPIRA("panspira", "Panspira", DimensionEnum.PANSPIRA),
    HEMATERIA("hemateria", "Hemateria", DimensionEnum.HEMATERIA),
    THEIA("theia", "Theia", DimensionEnum.THEIA),
    FROZEN_BELT("frozen_belt", "Frozen belt", DimensionEnum.FROZEN_BELT),
    AMBERGRIS_FRAGMENT("ambergris_fragment", "Ambergris fragment", null),
    VITRIS_SPACE("vitris_space", "Vitris space", DimensionEnum.VITRIS_SPACE),

    ;

    private final String id;
    private final String displayName;
    private final DimensionEnum dimension;

    CelestialObjectId(String id, String displayName, DimensionEnum dimension) {
        this.id = id;
        this.displayName = displayName;
        this.dimension = dimension;
    }

    public String getId() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public DimensionEnum dimension() {
        return dimension;
    }

    public static CelestialObjectId fromString(String id) {
        if (id == null) return null;
        for (CelestialObjectId value : values()) {
            if (value.id.equals(id) || value.name()
                .equalsIgnoreCase(id)) {
                return value;
            }
        }
        return null;
    }

    public static CelestialObjectId fromDimension(DimensionEnum dimension) {
        if (dimension == null) return null;
        for (CelestialObjectId value : values()) {
            if (value.dimension == dimension) {
                return value;
            }
        }
        return null;
    }
}
