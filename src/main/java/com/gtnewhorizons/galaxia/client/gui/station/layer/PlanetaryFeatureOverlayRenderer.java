package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;

public final class PlanetaryFeatureOverlayRenderer {

    private static final int MARKER_SIZE = 4;
    private static final int MARKER_GAP = 1;
    private static final int MARKER_PADDING = 2;

    private PlanetaryFeatureOverlayRenderer() {}

    public static void draw(int tileX, int tileY, Iterable<PlanetaryFeatureKey> features) {
        if (features == null) return;
        List<PlanetaryFeatureDefinition> definitions = new ArrayList<>();
        for (PlanetaryFeatureKey key : features) {
            PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
            if (definition != null) definitions.add(definition);
        }
        for (Marker marker : markers(tileX, tileY, definitions)) {
            Gui.drawRect(
                marker.x(),
                marker.y(),
                marker.x() + marker.size(),
                marker.y() + marker.size(),
                marker.color());
        }
    }

    static List<Marker> markers(int tileX, int tileY, Iterable<PlanetaryFeatureDefinition> features) {
        List<Marker> markers = new ArrayList<>();
        if (features == null) return markers;
        int maxMarkers = (StationMapViewport.TILE_SIZE - 2 * MARKER_PADDING + MARKER_GAP) / (MARKER_SIZE + MARKER_GAP);
        for (PlanetaryFeatureDefinition feature : features) {
            if (feature == null || markers.size() >= maxMarkers) continue;
            int index = markers.size();
            int x = tileX + MARKER_PADDING + index * (MARKER_SIZE + MARKER_GAP);
            int y = tileY + StationMapViewport.TILE_SIZE - MARKER_PADDING - MARKER_SIZE;
            markers.add(new Marker(x, y, MARKER_SIZE, feature.overlayColor()));
        }
        return markers;
    }

    record Marker(int x, int y, int size, int color) {}
}
