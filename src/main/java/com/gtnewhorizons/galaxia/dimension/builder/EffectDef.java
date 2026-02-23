package com.gtnewhorizons.galaxia.dimension.builder;

import com.github.bsideup.jabel.Desugar;

/**
 * Record to hold effects of the dimension (effectively a posh dataclass)
 * Constructs a definition based on the given parameters
 * *
 * * @param baseTemp The temperature of the planet
 * * @param withering Whether withering is enabled on the planet
 * * @param oxygenPercent The relative oxygen level of the planet
 * * @param radiation The relative radiation level of the planet
 * * @param spores Whether spores are enabled on the planet
 * * @param pressure The relative pressure on the planet
 */
@Desugar
public record EffectDef(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
    int pressure) {

    /**
     * A default implementation of the fleshed out constructor, with overworld like stats
     */
    public EffectDef() {
        this(273, false, 100, 0, false, 1);
    }
}
