package com.gtnewhorizons.galaxia.dimension;

public class EffectDef {

    public final int baseTemp;
    public final boolean withering;
    public final int oxygenPercent;
    public final int radiation;
    public final boolean spores;
    public final int pressure;

    public EffectDef(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores, int pressure) {
        this.baseTemp = baseTemp;
        this.withering = withering;
        this.oxygenPercent = oxygenPercent;
        this.radiation = radiation;
        this.spores = spores;
        this.pressure = pressure;
    }

    public EffectDef() {
        this.baseTemp = 273;
        this.withering = false;
        this.oxygenPercent = 100;
        this.radiation = 0;
        this.spores = false;
        this.pressure = 1;
    }
}
