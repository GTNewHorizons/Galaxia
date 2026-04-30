package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class StationLayout {

    private static final Logger LOG = LogManager.getLogger(StationLayout.class);

    private final Map<StationTileCoord, PlacedTile> tiles;
    private long version;
    private Map<StationTileCoord, PlacedTile> cachedSnapshot;
    private long snapshotVersion = -1;

    public StationLayout() {
        this.tiles = new TreeMap<>();
        this.tiles.put(StationTileCoord.CORE, PlacedTile.CORE);
    }

    public @Nullable PlacedTile get(StationTileCoord coord) {
        return tiles.get(coord);
    }

    public boolean isOccupied(StationTileCoord coord) {
        return tiles.containsKey(coord);
    }

    public void place(StationTileCoord coord, PlacedTile tile) {
        PlacedTile existing = tiles.get(coord);
        if (existing != null && existing != PlacedTile.CORE && tile != PlacedTile.CORE) {
            throw new IllegalStateException(
                "StationLayout.place: coordinate (" + coord.dx()
                    + ","
                    + coord.dy()
                    + ") already occupied by "
                    + (existing.module() != null ? existing.module()
                        .kind() : "CORE")
                    + " — cannot place "
                    + (tile.module() != null ? tile.module()
                        .kind() : "CORE"));
        }
        tiles.put(coord, tile);
        version++;
        if (tile.module() != null) {
            tile.module()
                .initAnchor(coord);
        }
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
        StationTileCoord[] footprint = module.shape()
            .tiles(module.anchor());
        // Validate no overlap with a different module (silent overwrite is data corruption)
        for (StationTileCoord coord : footprint) {
            PlacedTile existing = tiles.get(coord);
            if (existing != null && existing != PlacedTile.CORE && existing.module() != module) {
                throw new IllegalStateException(
                    "StationLayout.place(ModuleInstance): coordinate (" + coord.dx()
                        + ","
                        + coord.dy()
                        + ") already occupied by "
                        + (existing.module() != null ? existing.module()
                            .kind() : "CORE")
                        + " (id="
                        + (existing.module() != null ? existing.module().id : "none")
                        + ") — cannot place "
                        + module.kind()
                        + " (id="
                        + module.id
                        + "). "
                        + "Two different modules overlap. A module was probably placed without prior validation or "
                        + "a persistence load produced overlapping footprints.");
            }
        }
        for (StationTileCoord coord : footprint) {
            tiles.put(coord, new PlacedTile(module, state));
            module.initAnchor(coord);
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
        int beforeSize = tiles.size();
        tiles.clear();
        for (Map.Entry<StationTileCoord, PlacedTile> entry : snapshot.entrySet()) {
            PlacedTile tile = entry.getValue();
            StationTileCoord coord = entry.getKey();
            if (!StationTileCoord.CORE.equals(coord) && (tile == null || tile.module() == null)) {
                throw new IllegalStateException(
                    "StationLayout.loadFromSnapshot: non-CORE tile at (" + coord.dx()
                        + ","
                        + coord.dy()
                        + ") has null module — zombie tile detected. "
                        + "A module was not properly decoded during persistence load.");
            }
            tiles.put(coord, tile);
        }
        tiles.putIfAbsent(StationTileCoord.CORE, PlacedTile.CORE);
        version++;
        LOG.debug(
            "[PERSIST] LAYOUT: loadFromSnapshot({} entries) restored snapshot with {} tiles (was {})",
            snapshot.size(),
            tiles.size(),
            beforeSize);
    }

    public int size() {
        return tiles.size();
    }

    public long version() {
        return version;
    }

    static final int[] DX = { 0, 1, 0, -1 };
    static final int[] DY = { -1, 0, 1, 0 };

    /**
     * Counts orthogonal same-kind neighbors of a module at the given coordinate.
     */
    public static int countOrthogonalNeighbors(StationLayout layout, StationTileCoord coord, FacilityModuleKind kind) {
        int count = 0;
        for (int i = 0; i < DX.length; i++) {
            int ndx = coord.dx() + DX[i];
            int ndy = coord.dy() + DY[i];
            if (ndx < StationTileCoord.MIN || ndx > StationTileCoord.MAX
                || ndy < StationTileCoord.MIN
                || ndy > StationTileCoord.MAX) {
                continue;
            }
            StationTileCoord ncoord = StationTileCoord.of(ndx, ndy);
            ModuleInstance neighborModule = layout.moduleAt(ncoord);
            if (neighborModule != null && neighborModule.kind() == kind) {
                count++;
            }
        }
        return count;
    }
}
