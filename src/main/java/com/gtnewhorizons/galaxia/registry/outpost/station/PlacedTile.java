package com.gtnewhorizons.galaxia.registry.outpost.station;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public record PlacedTile(@Nullable ModuleInstance module, StationTileState state) {

    public static final PlacedTile CORE = new PlacedTile(null, StationTileState.OCCUPIED_OPERATIONAL);

    public boolean isCore() {
        return module == null;
    }
}
