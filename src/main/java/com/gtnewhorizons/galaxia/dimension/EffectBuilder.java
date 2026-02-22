package com.gtnewhorizons.galaxia.dimension;

public class EffectBuilder {

    private int baseTemp = 273;
    private boolean withering = false;
    private int oxygenPercent = 100;
    private int radiation = 0;
    private boolean spores = false;
    private int pressure = 1;

    /**
     * @param baseTemp temperature in Kelvins
     */
    public EffectBuilder baseTemp(int baseTemp) {
        this.baseTemp = baseTemp;
        return this;
    }

    /**
     * @param oxygenPercent oxygen amount where 100 is earth atmosphere
     */
    public EffectBuilder oxygenPercent(int oxygenPercent) {
        this.oxygenPercent = oxygenPercent;
        return this;
    }

    /**
     * @param pressure pressure in earth atmospheres
     */
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
