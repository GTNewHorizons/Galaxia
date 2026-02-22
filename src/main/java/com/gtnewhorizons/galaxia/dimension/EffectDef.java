package com.gtnewhorizons.galaxia.dimension;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record EffectDef(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
    int pressure) {

    public EffectDef() {
        this(273, false, 100, 0, false, 1);
    }
}
