package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

/** Shared station-tile target helpers for module picker flows. */
final class StationTargetPicker {

    private StationTargetPicker() {}

    static StationTileCoord normalizeTarget(AutomatedFacility facility, StationTileCoord coord) {
        if (facility == null || coord == null) return coord;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return coord;
        ModuleInstance module = layout.moduleAt(coord);
        return module == null ? coord : module.anchor();
    }
}
