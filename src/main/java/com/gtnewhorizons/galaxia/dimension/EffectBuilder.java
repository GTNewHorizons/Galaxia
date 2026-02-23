package com.gtnewhorizons.galaxia.dimension;

public class EffectBuilder {

    private int baseTemp = 273;
    private boolean withering = false;
    private int oxygenPercent = 100;
    private int radiation = 0;
    private boolean spores = false;
    private int pressure = 1;

    private EffectDef.IEffectModifier tempMod;
    private EffectDef.IEffectModifier oxygenMod;
    private EffectDef.IEffectModifier radiationMod;
    private EffectDef.IEffectModifier pressureMod;

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

    public EffectBuilder tempMod(EffectDef.IEffectModifier modifier) {
        tempMod = modifier;
        return this;
    }

    public EffectBuilder oxygenMod(EffectDef.IEffectModifier modifier) {
        oxygenMod = modifier;
        return this;
    }

    public EffectBuilder pressureMod(EffectDef.IEffectModifier modifier) {
        pressureMod = modifier;
        return this;
    }

    public EffectBuilder radiationMod(EffectDef.IEffectModifier modifier) {
        radiationMod = modifier;
        return this;
    }

    public EffectDef build() {
        EffectDef def = new EffectDef(baseTemp, withering, oxygenPercent, radiation, spores, pressure);
        def.pressureModifier = pressureMod;
        def.radiationModifier = radiationMod;
        def.oxygenModifier = oxygenMod;
        def.tempModifier = tempMod;
        return def;
    }
}
