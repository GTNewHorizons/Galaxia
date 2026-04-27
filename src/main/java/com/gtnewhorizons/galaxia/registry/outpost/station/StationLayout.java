package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class StationLayout {

    private final Map<StationTileCoord, PlacedTile> tiles;
    private long version;

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
        version++;
    }

    public void remove(StationTileCoord coord) {
        if (StationTileCoord.CORE.equals(coord)) return;
        if (tiles.remove(coord) != null) version++;
    }

    public void removeTileForModule(ModuleInstance.ID moduleId) {
        if (moduleId == null) return;
        boolean removed = tiles.entrySet()
            .removeIf(
                e -> !StationTileCoord.CORE.equals(e.getKey()) && e.getValue()
                    .module() != null
                    && moduleId.equals(
                        e.getValue()
                            .module().id));
        if (removed) version++;
    }

    public @Nonnull Map<StationTileCoord, PlacedTile> snapshot() {
        return Collections.unmodifiableMap(tiles);
    }

    public void loadFromSnapshot(@Nonnull Map<StationTileCoord, PlacedTile> snapshot) {
        tiles.clear();
        tiles.putAll(snapshot);
        tiles.putIfAbsent(StationTileCoord.CORE, PlacedTile.CORE);
        version++;
    }

    public int size() {
        return tiles.size();
    }

    public long version() {
        return version;
    }
}
