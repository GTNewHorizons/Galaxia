package com.gtnewhorizons.galaxia.dimension;

/**
 * Class to hold effects of the dimension (effectively a posh dataclass)
 */
public class EffectDef {

    public final int baseTemp;
    public final boolean withering;
    public final int oxygenPercent;
    public final int radiation;
    public final boolean spores;
    public final int pressure;

    /**
     * Constructs a definition based on the given parameters
     * 
     * @param baseTemp      The temperature of the planet
     * @param withering     Whether withering is enabled on the planet
     * @param oxygenPercent The relative oxygen level of the planet
     * @param radiation     The relative radiation level of the planet
     * @param spores        Whether spores are enabled on the planet
     * @param pressure      The relative pressure on the planet
     */
    public EffectDef(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores, int pressure) {
        this.baseTemp = baseTemp;
        this.withering = withering;
        this.oxygenPercent = oxygenPercent;
        this.radiation = radiation;
        this.spores = spores;
        this.pressure = pressure;
    }

    /**
     * A default implementation of the fleshed out constructor, with overworld like stats
     */
    public EffectDef() {
        this.baseTemp = 273;
        this.withering = false;
        this.oxygenPercent = 100;
        this.radiation = 0;
        this.spores = false;
        this.pressure = 1;
    }
}
