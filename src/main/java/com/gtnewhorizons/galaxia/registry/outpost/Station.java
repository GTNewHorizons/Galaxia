package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;

public class Station extends CelestialAsset {

    private BlockPos controller;

    public Station(ID assetId, CelestialObjectId celestialObjectId, Status status) {
        super(assetId, celestialObjectId, Kind.STATION, status, null);
    }

    public BlockPos getController() {
        return controller;
    }

    public void setController(BlockPos controller) {
        this.controller = controller;
        markDirty();
    }

    @Override
    public void tick() {
        TileStationController teController = getTileController();
        if (teController == null) return;

        teController.tick();
        LogisticStore.updateSignalsForFacility(this);
    }

    @Override
    public List<IInventory> getInventories() {
        TileStationController teController = getTileController();
        if (teController == null) return List.of();

        return teController.getConnectedInventories();
    }

    @Override
    public String getInventoryName() {
        return "Station inventory";
    }

    // ── Filter delegation ──

    @Override
    public Map<Integer, List<ItemStack>> filtersSnapshot() {
        TileStationController ctrl = getTileController();
        if (ctrl == null) return Collections.emptyMap();
        return ctrl.filtersSnapshot();
    }

    @Override
    public List<ItemStack> getFiltersFor(int i) {
        TileStationController ctrl = getTileController();
        if (ctrl == null) return super.getFiltersFor(i);
        return ctrl.getFiltersFor(i);
    }

    @Override
    public void setFilters(int slot, List<ItemStack> filterList) {
        TileStationController ctrl = getTileController();
        if (ctrl == null) {
            super.setFilters(slot, filterList);
            return;
        }
        ctrl.setFilters(slot, filterList);
    }

    @Override
    public void addFilter(int slot, ItemStack filter) {
        TileStationController ctrl = getTileController();
        if (ctrl == null) {
            super.addFilter(slot, filter);
            return;
        }
        ctrl.addFilter(slot, filter);
    }

    @Override
    public void removeFilter(int slot, ItemStack filter) {
        TileStationController ctrl = getTileController();
        if (ctrl == null) {
            super.removeFilter(slot, filter);
            return;
        }
        ctrl.removeFilter(slot, filter);
    }

    @Override
    public void clearFilters(int slot) {
        TileStationController ctrl = getTileController();
        if (ctrl == null) {
            super.clearFilters(slot);
            return;
        }
        ctrl.clearFilters(slot);
    }

    // ── Controller lookup ──

    /** Public so network handlers can route filter mutations. */
    public TileStationController getTileController() {
        if (this.isDisabled()) return null;
        if (controller == null) return null;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        int dimId = celestialObjectId.dimension()
            .getId();
        WorldServer world = server.worldServerForDimension(dimId);
        if (world == null) return null;

        return controller.getTE(world);
    }
}
