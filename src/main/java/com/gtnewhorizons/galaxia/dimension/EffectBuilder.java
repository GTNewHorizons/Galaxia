package com.gtnewhorizons.galaxia.dimension;

public class EffectBuilder {

    private int baseTemp;
    private boolean withering;
    private int oxygenPercent;
    private int radiation;
    private boolean spores;
    private int pressure;

    public EffectBuilder baseTemp(int baseTemp) {
        this.baseTemp = baseTemp;
        return this;
    }

    public EffectBuilder oxygenPercent(int oxygenPercent) {
        this.oxygenPercent = oxygenPercent;
        return this;
    }

    public EffectBuilder pressure(int pressure) {
        this.pressure = pressure;
        return this;
    }

    public EffectBuilder radiation(int radiation) {
        this.radiation = radiation;
        return this;
    }

    public EffectBuilder withering(boolean withering) {
        this.withering = withering;
        return this;
    }

    public EffectBuilder spores(boolean spores) {
        this.spores = spores;
        return this;
    }

    public EffectDef build() {
        return new EffectDef(baseTemp, withering, oxygenPercent, radiation, spores, pressure);
    }
}
