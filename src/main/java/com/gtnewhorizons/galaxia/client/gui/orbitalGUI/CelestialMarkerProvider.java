package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

public interface CelestialMarkerProvider {

    List<CelestialMarker> getMarkers(CelestialMarkerContext context);
}
