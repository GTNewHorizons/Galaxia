package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationCopyModuleActionModel {

    private StationCopyModuleActionModel() {}

    static @Nullable Source resolve(@Nullable AutomatedFacility facility, @Nullable StationTileCoord selection) {
        if (facility == null || selection == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(selection);
        if (tile == null || tile.isCore() || tile.module() == null) return null;
        ModuleInstance module = tile.module();
        int moduleIndex = moduleIndex(facility, module.id);
        return moduleIndex < 0 ? null : new Source(moduleIndex, module.id);
    }

    private static int moduleIndex(AutomatedFacility facility, ModuleInstance.ID moduleId) {
        for (int i = 0; i < facility.modules()
            .size(); i++) {
            if (facility.modules()
                .get(i).id.equals(moduleId)) return i;
        }
        return -1;
    }

    record Source(int moduleIndex, ModuleInstance.ID moduleId) {}
}
