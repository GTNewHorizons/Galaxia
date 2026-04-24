package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class StationLayout {

    private final Map<StationTileCoord, PlacedTile> tiles;

    public StationLayout() {
        this.tiles = new LinkedHashMap<>();
        this.tiles.put(StationTileCoord.CORE, PlacedTile.CORE);
    }

    public @Nullable PlacedTile get(StationTileCoord coord) {
        return tiles.get(coord);
    }

    public boolean isOccupied(StationTileCoord coord) {
        return tiles.containsKey(coord);
    }

    public void place(StationTileCoord coord, PlacedTile tile) {
        tiles.put(coord, tile);
    }

    public void remove(StationTileCoord coord) {
        if (StationTileCoord.CORE.equals(coord)) return;
        tiles.remove(coord);
    }

    public void removeTileForModule(ModuleInstance.ID moduleId) {
        if (moduleId == null) return;
        tiles.entrySet()
            .removeIf(
                e -> !StationTileCoord.CORE.equals(e.getKey()) && e.getValue()
                    .module() != null
                    && moduleId.equals(
                        e.getValue()
                            .module().id));
    }

    public @Nonnull Map<StationTileCoord, PlacedTile> snapshot() {
        return Collections.unmodifiableMap(tiles);
    }

    public void loadFromSnapshot(@Nonnull Map<StationTileCoord, PlacedTile> snapshot) {
        tiles.clear();
        tiles.putAll(snapshot);
        tiles.putIfAbsent(StationTileCoord.CORE, PlacedTile.CORE);
    }

    public int size() {
        return tiles.size();
    }
}
