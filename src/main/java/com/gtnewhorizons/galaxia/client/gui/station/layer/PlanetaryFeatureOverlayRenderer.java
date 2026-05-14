package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureSet;

public final class PlanetaryFeatureOverlayRenderer {

    private PlanetaryFeatureOverlayRenderer() {}

    public static void draw(int tileX, int tileY, PlanetaryFeatureSet features) {
        if (features == null || features.isEmpty()) return;
        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        for (PlanetaryFeatureKey key : features.values()) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) definitions.add(definition);
        }
        for (PlanetaryFeatureOverlayLayout.Marker marker : PlanetaryFeatureOverlayLayout
            .markers(tileX, tileY, definitions)) {
            Gui.drawRect(
                marker.x(),
                marker.y(),
                marker.x() + marker.size(),
                marker.y() + marker.size(),
                marker.color());
        }
    }
}
