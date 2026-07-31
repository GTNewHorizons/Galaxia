package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapHitTester {

    private StationMapHitTester() {}

    static @Nullable StationTileCoord hitTestModuleFootprint(StationLayout layout, int localX, int localY,
        StationMapFrame frame) {
        if (layout == null) return null;
        Set<ModuleInstance.ID> checked = new LinkedHashSet<>();
        for (PlacedTile placedTile : layout.snapshot()
            .values()) {
            ModuleInstance module = moduleOf(placedTile);
            if (module == null || module.shape()
                .tileCount() <= 1 || !checked.add(module.id)) {
                continue;
            }
            if (ModuleFootprintProjection
                .contains(module.shape(), module.anchor(), module.rotation(), localX, localY, frame)) {
                return module.anchor();
            }
        }
        return null;
    }

    private static @Nullable ModuleInstance moduleOf(@Nullable PlacedTile tile) {
        return tile == null ? null : tile.module();
    }
}
