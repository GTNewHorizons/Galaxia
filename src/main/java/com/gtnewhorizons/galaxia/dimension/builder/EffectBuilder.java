package com.gtnewhorizons.galaxia.dimension.builder;

/**
 * Builder class to get a list of effects on each planet as required
 */
public class EffectBuilder {

    private int baseTemp = 273; // Base temperature of planet
    private boolean withering = false; // Whether the planet has a withering effect
    private int oxygenPercent = 100; // Oxygen percent (relative to Overworld -> 1)
    private int radiation = 0; // The level of radiation relative to Overworld (OW -> 1)
    private boolean spores = false; // Whether the planet has fungal spores in atmosphere
    private int pressure = 1; // Pressure on surface relative to Overworld (OW -> 1)

    /**
     * Sets the base temperature of the dimension
     *
     * @param baseTemp The required temperature
     * @return Configured builder
     */
    public EffectBuilder baseTemp(int baseTemp) {
        this.baseTemp = baseTemp;
        return this;
    }

    /**
     * Sets the oxygen level relative to the overworld (OW -> 1)
     *
     * @param oxygenPercent The required oxygen level
     * @return Configured builder
     */
    public EffectBuilder oxygenPercent(int oxygenPercent) {
        this.oxygenPercent = oxygenPercent;
        return this;
    }

    /**
     * Sets the atmospheric pressure on the planet relative to the overworld (OW -> 1)
     *
     * @param pressure The required pressure level
     * @return Configured builder
     */
    public EffectBuilder pressure(int pressure) {
        this.pressure = pressure;
        return this;
    }

    /**
     * Sets the radiation level relative to the overworld (OW -> 1)
     *
     * @param radiation The required radiation level
     * @return Configured builder
     */
    public EffectBuilder radiation(int radiation) {
        this.radiation = radiation;
        return this;
    }

    /**
     * Sets whether the planet should have a withering effect (See Ambergris)
     *
     * @param withering Boolean : True => wither effect
     * @return Configured builder
     */
    public EffectBuilder withering(boolean withering) {
        this.withering = withering;
        return this;
    }

    /**
     * Sets whether the planet should have fungal spores in the atmosphere (See Mykelia)
     *
     * @param spores Boolean : True => spores effect
     * @return Configured builder
     */
    public EffectBuilder spores(boolean spores) {
        this.spores = spores;
        return this;
    }

    /**
     * Builds an Effect Definition based on current fields
     *
     * @return Configured EffectDef
     */
    public EffectDef build() {
        return new EffectDef(baseTemp, withering, oxygenPercent, radiation, spores, pressure);
    }
}
