package com.gtnewhorizons.galaxia.registry.outpost.station;

import javax.annotation.Nonnull;

public record ModulePlacement(@Nonnull StationTileCoord anchor, int rotation) {

    public ModulePlacement {
        rotation = ModuleShape.normalizeRotation(rotation);
    }

    public static ModulePlacement at(StationTileCoord anchor) {
        return new ModulePlacement(anchor, 0);
    }
}
