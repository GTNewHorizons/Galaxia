package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record GtOreVeinDefinition(String id, String displayName, String primaryOre, String secondaryOre,
    String betweenOre, String sporadicOre, int minY, int maxY, int weight, int density, int size) {

    public List<String> ores() {
        return Collections.unmodifiableList(Arrays.asList(primaryOre, secondaryOre, betweenOre, sporadicOre));
    }

    public int abundanceScore() {
        return weight * Math.max(1, density) * Math.max(1, size);
    }
}
