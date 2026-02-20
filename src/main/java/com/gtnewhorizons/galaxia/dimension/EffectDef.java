package com.gtnewhorizons.galaxia.dimension;

import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;
import net.minecraft.world.WorldProvider;

import java.util.Collections;
import java.util.List;

public class EffectDef {

    public final int baseTemp;
    public final int dayTempMod;
    public final int nightTempMod;
    public final boolean withering;
    public final int oxygenPercent;
    public final int radiation;
    public final boolean spores;
    public final int pressure;

    public EffectDef(int baseTemp, int dayTempMod,
              int nightTempMod, boolean withering, int oxygenPercent, int radiation, boolean spores, int pressure) {
        this.baseTemp = baseTemp;
        this.dayTempMod = dayTempMod;
        this.nightTempMod = nightTempMod;
        this.withering = withering;
        this.oxygenPercent = oxygenPercent;
        this.radiation = radiation;
        this.spores = spores;
        this.pressure = pressure;
    }

    public EffectDef() {
        this.baseTemp = 273;
        this.dayTempMod = 20;
        this.nightTempMod = -10;
        this.withering = false;
        this.oxygenPercent = 100;
        this.radiation = 0;
        this.spores = false;
        this.pressure = 1;
    }
}
