package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class SyncSnapshotBuilder {

    private SyncSnapshotBuilder() {}

    static List<AssetSyncPacket> automatedFacilityDeltas(AutomatedFacility state) {
        List<AssetSyncPacket> deltas = new ArrayList<>();

        state.settingsGroups()
            .groups()
            .values()
            .stream()
            .sorted(Comparator.comparingInt(SettingsGroup::id))
            .forEach(group -> deltas.add(AssetSyncPacket.settingsGroupUpdated(state.assetId, group)));

        for (Map.Entry<Boolean, List<String>> e : state.filtersSnapshot()
            .entrySet()) {
            deltas.add(AssetSyncPacket.filterUpdated(state.assetId, e.getKey(), e.getValue()));
        }

        List<ModuleInstance> modules = state.modules();
        for (int i = 0; i < modules.size(); i++) {
            deltas.add(AssetSyncPacket.moduleAdded(state.assetId, i, modules.get(i)));
        }

        for (Map.Entry<ItemStackWrapper, Long> e : state.itemSnapshot()
            .entrySet()) {
            deltas.add(AssetSyncPacket.inventoryUpdate(state.assetId, e.getKey(), e.getValue()));
        }

        Map<? extends InventoryKey, InventoryBounds> itemBounds = state.getBounds(true);
        Map<? extends InventoryKey, InventoryBounds> fluidBounds = state.getBounds(false);
        if (!itemBounds.isEmpty() || !fluidBounds.isEmpty()) {
            deltas.add(AssetSyncPacket.inventoryBoundsSnapshot(state.assetId, itemBounds, fluidBounds));
        }

        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            deltas.add(
                AssetSyncPacket.logisticsConfigUpdated(
                    state.assetId,
                    e.getKey(),
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }

        StationLayout layout = state.stationLayout();
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> e : layout.snapshot()
                .entrySet()) {
                deltas.add(AssetSyncPacket.layoutTileUpdated(state.assetId, e.getKey(), e.getValue()));
            }
        }

        return deltas;
    }
}
