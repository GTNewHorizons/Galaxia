package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class StationLayout {

    private final Map<StationTileCoord, PlacedTile> tiles;
    private long version;
    private Map<StationTileCoord, PlacedTile> cachedSnapshot;
    private long snapshotVersion = -1;

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

    public @Nullable ModuleInstance moduleAt(StationTileCoord coord) {
        PlacedTile tile = tiles.get(coord);
        return tile != null ? tile.module() : null;
    }

    public boolean isAnchorAt(StationTileCoord coord) {
        ModuleInstance module = moduleAt(coord);
        return module != null && coord.equals(module.anchor());
    }

    public void forEachAnchor(BiConsumer<StationTileCoord, ModuleInstance> action) {
        tiles.forEach((coord, tile) -> {
            ModuleInstance module = tile.module();
            if (module != null && coord.equals(module.anchor())) {
                action.accept(coord, module);
            }
        });
    }

    public void forEachTile(BiConsumer<StationTileCoord, PlacedTile> action) {
        tiles.forEach(action);
    }

    public boolean deconstruct(StationTileCoord anyTile) {
        PlacedTile tile = tiles.get(anyTile);
        if (tile == null || tile.module() == null) return false;
        ModuleInstance module = tile.module();
        for (StationTileCoord coord : module.shape()
            .tiles(module.anchor())) {
            tiles.remove(coord);
        }
        version++;
        return true;
    }

    public void place(ModuleInstance module) {
        StationTileState state = StationTileState.fromModuleStatus(module.status());
        for (StationTileCoord coord : module.shape()
            .tiles(module.anchor())) {
            tiles.put(coord, new PlacedTile(module, state));
        }
        version++;
    }

    public @Nonnull Map<StationTileCoord, PlacedTile> snapshot() {
        if (cachedSnapshot == null || snapshotVersion != version) {
            cachedSnapshot = Collections.unmodifiableMap(tiles);
            snapshotVersion = version;
        }
        return cachedSnapshot;
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
