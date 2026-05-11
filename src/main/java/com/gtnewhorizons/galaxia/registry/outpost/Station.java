package com.gtnewhorizons.galaxia.registry.outpost;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

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
        if (this.isDisabled()) return;
        if (controller == null) return;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        int dimId = celestialObjectId.dimension()
            .getId();
        WorldServer world = server.worldServerForDimension(dimId);
        if (world == null) return;

        TileStationController teController = controller.getTE(world);
        if (teController == null) return;

        teController.tick();
    }
}
