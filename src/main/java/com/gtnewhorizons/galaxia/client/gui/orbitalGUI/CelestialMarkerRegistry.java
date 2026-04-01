package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CelestialMarkerRegistry {

    private static final List<CelestialMarkerProvider> PROVIDERS = new ArrayList<>();
    private static boolean bootstrapped;

    private CelestialMarkerRegistry() {}

    public static synchronized void registerDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(new AssetMarkerProvider());
        // Future marker providers can be registered here, e.g. hazards, upkeep, mission markers, or body effects.
    }

    public static synchronized void register(CelestialMarkerProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static synchronized List<CelestialMarker> getMarkers(CelestialMarkerContext context) {
        registerDefaults();

        Map<String, CelestialMarker> markersById = new LinkedHashMap<>();
        for (CelestialMarkerProvider provider : PROVIDERS) {
            for (CelestialMarker marker : provider.getMarkers(context)) {
                if (marker == null || marker.texture() == null) {
                    continue;
                }
                String markerId = getMarkerId(marker);
                CelestialMarker existing = markersById.get(markerId);
                if (existing == null || marker.alpha() > existing.alpha()) {
                    markersById.put(markerId, marker);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(markersById.values()));
    }

    private static String getMarkerId(CelestialMarker marker) {
        if (marker.id() != null && !marker.id().isEmpty()) {
            return marker.id();
        }
        return marker.texture().toString();
    }
}
