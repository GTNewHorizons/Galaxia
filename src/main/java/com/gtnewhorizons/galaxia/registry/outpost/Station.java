package com.gtnewhorizons.galaxia.registry.outpost;

import net.minecraft.inventory.IInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import java.util.List;

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

    private TileStationController getTileController() {
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
