package com.gtnewhorizons.galaxia.registry.celestial;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public enum CelestialObjectId {

    INVALID("", null),
    NOVA_CAELUM("galaxia.celestial.novum_caelum", null),
    VAEL("galaxia.celestial.vael", null),
    ILIA("galaxia.celestial.ilia", null),
    PROXIMA_CENTAURI("galaxia.celestial.proxima_centauri", null),
    ROMULUS("galaxia.celestial.romulus", null),
    REMUS("galaxia.celestial.remus", null),
    EGORA("galaxia.celestial.egora", null),
    PANSPIRA("galaxia.celestial.panspira", DimensionEnum.PANSPIRA),
    HEMATERIA("galaxia.celestial.hemateria", DimensionEnum.HEMATERIA),
    THEIA("galaxia.celestial.theia", DimensionEnum.THEIA),
    FROZEN_BELT("galaxia.celestial.frozen_belt", DimensionEnum.FROZEN_BELT),
    AMBERGRIS_FRAGMENT("galaxia.celestial.ambergris_fragment", null),
    VITRIS_SPACE("galaxia.celestial.vitris_space", DimensionEnum.VITRIS_SPACE),

    ;

    private final String id;
    private final DimensionEnum dimension;

    CelestialObjectId(String id, DimensionEnum dimension) {
        this.id = id;
        this.dimension = dimension;
    }

    public String getId() {
        return this.id;
    }

    public String displayName() {
        return StatCollector.translateToLocal(this.id);
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
