package com.gtnewhorizons.galaxia.registry.celestial;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public enum CelestialObjectId {

    INVALID("", null),
    NOVUM_CAELUM("galaxia.celestial.novum_caelum", null),
    SOL("galaxia.celestial.sol", null),
    BARNARDA("galaxia.celestial.barnarda", null),
    TCETI_A("galaxia.celestial.tceti_a", null),
    VEGA("galaxia.celestial.vega", null),
    ALPHA_CENTAURI_A("galaxia.celestial.alpha_centauri_a", null),
    VAEL("galaxia.celestial.vael", null),
    ILIA("galaxia.celestial.ilia", null),
    ROMULUS("galaxia.celestial.romulus", null),
    REMUS("galaxia.celestial.remus", null),
    EGORA("galaxia.celestial.egora", null),
    MERCURY("galaxia.celestial.mercury", null),
    VENUS("galaxia.celestial.venus", null),
    TENEBRAE("galaxia.celestial.tenebrae", DimensionEnum.TENEBRAE),
    MARS("galaxia.celestial.mars", DimensionEnum.MARS),
    PHOBOS("galaxia.celestial.phobos", null),
    DEIMOS("galaxia.celestial.deimos", null),
    MOON("galaxia.celestial.moon", DimensionEnum.MOON),
    CERES("galaxia.celestial.ceres", null),
    JUPITER("galaxia.celestial.jupiter", null),
    IO("galaxia.celestial.io", null),
    EUROPA("galaxia.celestial.europa", null),
    GANYMEDE("galaxia.celestial.ganymede", null),
    CALLISTO("galaxia.celestial.callisto", null),
    SATURN("galaxia.celestial.saturn", null),
    ENCELADUS("galaxia.celestial.enceladus", null),
    TITAN("galaxia.celestial.titan", null),
    URANUS("galaxia.celestial.uranus", null),
    MIRANDA("galaxia.celestial.miranda", null),
    OBERON("galaxia.celestial.oberon", null),
    NEPTUNE("galaxia.celestial.neptune", null),
    PROTEUS("galaxia.celestial.proteus", null),
    TRITON("galaxia.celestial.triton", null),
    PLUTO("galaxia.celestial.pluto", null),
    KUIPER_BELT("galaxia.celestial.kuiper_belt", null),
    HAUMEA("galaxia.celestial.haumea", null),
    MAKEMAKE("galaxia.celestial.makemake", null),
    BARNARD_C("galaxia.celestial.barnard_c", null),
    BARNARD_E("galaxia.celestial.barnard_e", null),
    BARNARD_F("galaxia.celestial.barnard_f", null),
    CENTAURI_BB("galaxia.celestial.centauri_bb", null),
    TCETI_E("galaxia.celestial.tceti_e", null),
    VEGA_B("galaxia.celestial.vega_b", null),
    FROZEN_BELT("galaxia.celestial.frozen_belt", DimensionEnum.FROZEN_BELT),
    OVERWORLD("galaxia.celestial.overworld", DimensionEnum.OVERWORLD),

    MIMAS("galaxia.celestial.mimas", null),
    TETHYS("galaxia.celestial.tethys", null),
    DIONE("galaxia.celestial.dione", null),
    RHEA("galaxia.celestial.rhea", null),
    IAPETUS("galaxia.celestial.iapetus", null),
    ARIEL("galaxia.celestial.ariel", null),
    UMBRIEL("galaxia.celestial.umbriel", null),
    TITANIA("galaxia.celestial.titania", null),
    CHARON("galaxia.celestial.charon", null),
    ERIS("galaxia.celestial.eris", null),
    ALPHA_CENTAURI_B("galaxia.celestial.alpha_centauri_b", null),
    BARNARD_B("galaxia.celestial.barnard_b", null),
    BARNARD_D("galaxia.celestial.barnard_d", null),
    BARNARD_G("galaxia.celestial.barnard_g", null),
    BARNARD_BELT("galaxia.celestial.barnard_belt", null),
    TCETI_B("galaxia.celestial.tceti_b", null),
    TCETI_C("galaxia.celestial.tceti_c", null),
    TCETI_D("galaxia.celestial.tceti_d", null),
    TCETI_F("galaxia.celestial.tceti_f", null),
    TCETI_G("galaxia.celestial.tceti_g", null),
    TCETI_BELT("galaxia.celestial.tceti_belt", null),
    VEGA_C("galaxia.celestial.vega_c", null),
    VEGA_INNER_BELT("galaxia.celestial.vega_inner_belt", null),
    VEGA_OUTER_BELT("galaxia.celestial.vega_outer_belt", null),
    ROSS_128_B("galaxia.celestial.ross_128_b", null),
    ROSS_128_BA("galaxia.celestial.ross_128_ba", null),
    AMUN("galaxia.celestial.amun", null),
    OSIRIS("galaxia.celestial.osiris", null),
    HORUS("galaxia.celestial.horus", null),
    BAAL("galaxia.celestial.baal", null),
    BAAL_RINGS("galaxia.celestial.baal_rings", null),
    KHONSU("galaxia.celestial.khonsu", null),
    NEPER("galaxia.celestial.neper", null),
    IAH("galaxia.celestial.iah", null),
    MEHEN_BELT("galaxia.celestial.mehen_belt", null),
    SEKHMET("galaxia.celestial.sekhmet", null),
    BAST("galaxia.celestial.bast", null),
    MAAHES("galaxia.celestial.maahes", null),
    THOTH("galaxia.celestial.thoth", null),
    SETH("galaxia.celestial.seth", null),
    ANUBIS("galaxia.celestial.anubis", null),
    KEBE("galaxia.celestial.kebe", null),
    ROSS_128("galaxia.celestial.ross_128", null),
    RA("galaxia.celestial.ra", null),

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
