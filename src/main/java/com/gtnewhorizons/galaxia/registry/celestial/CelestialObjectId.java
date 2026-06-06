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
    MARS("galaxia.celestial.mars", DimensionEnum.MARS),
    MOON("galaxia.celestial.moon", DimensionEnum.MOON),
    FROZEN_BELT("galaxia.celestial.frozen_belt", DimensionEnum.FROZEN_BELT),
    AMBERGRIS_FRAGMENT("galaxia.celestial.ambergris_fragment", null),
    OVERWORLD("galaxia.celestial.overworld", DimensionEnum.OVERWORLD),
    OVERWORLD_ORBIT("galaxia.celestial.overworld_orbit", DimensionEnum.OVERWORLD_ORBIT),

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
