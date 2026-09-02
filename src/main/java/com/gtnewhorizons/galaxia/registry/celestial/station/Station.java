package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public class Station extends CelestialAsset implements IDistributedInventory {

    private BlockPos controller;

    public Station(ID assetId, CelestialObjectKey celestialObjectKey, Status status) {
        super(assetId, celestialObjectKey, Kind.STATION, status, null);
    }

    public Station(ID assetId, CelestialObjectId celestialObjectId, Status status) {
        this(assetId, CelestialObjectKey.registered(celestialObjectId), status);
    }

    public BlockPos getController() {
        return controller;
    }

    public void setController(BlockPos controller) {
        if (java.util.Objects.equals(this.controller, controller)) return;
        this.controller = controller;
        markDirty();
    }

    @Override
    public void tick() {
        TileStation teController = getTileController();
        if (teController == null) return;

        teController.tick();
    }

    @Override
    public List<IDistributedInventory> getChildren() {
        TileStation teController = getTileController();
        if (teController == null) return List.of();

        return teController.getConnectedInventories();
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper item, long target) {
        return 0L;
    }

    @Override
    public long insertIntoOwnFluidStorage(FluidKey fluid, long target) {
        return 0L;
    }

    @Override
    public boolean tryConsumeEnergy(long powerDraw) {
        return drawEnergy(powerDraw) > 0;
    }

    public long drawEnergy(long maxPowerDraw) {
        TileStation ctrl = getTileController();
        if (ctrl == null) return 0;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return 0;
        return graph.drawEnergy(maxPowerDraw);
    }

    @Override
    public long getEnergyStored() {
        TileStation ctrl = getTileController();
        if (ctrl == null) return 0;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return 0;
        return graph.getEnergyStored()
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Override
    public Stream<ModuleInstance> forEachModule() {
        TileStation ctrl = getTileController();
        if (ctrl == null) return Stream.of();
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return Stream.of();
        return graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .map(TileHammerCannon::getModuleInstance);
    }

    @Nullable
    public TileHammerCannon findHammerCannon(ModuleInstance module) {
        if (module == null) return null;
        TileStation ctrl = getTileController();
        if (ctrl == null) return null;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return null;
        return graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .filter(cannon -> module.equals(cannon.getModuleInstance()))
            .findFirst()
            .orElse(null);
    }

    public Map<ItemStackWrapper, Long> getCannonChestItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        TileStation ctrl = getTileController();
        if (ctrl == null) return result;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return result;
        graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .forEach(
                cannon -> cannon.getPackageItems()
                    .forEach((key, amount) -> result.merge(key, amount, Long::sum)));
        return result;
    }

    public long getCannonSupplyAmount(ItemStackWrapper resource, long reserve) {
        TileStation ctrl = getTileController();
        if (ctrl == null) return 0L;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return 0L;
        return graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .mapToLong(cannon -> Math.max(cannon.getPackageAmount(resource) - reserve, 0L))
            .sum();
    }

    /** Public so network handlers can route filter mutations. */
    public TileStation getTileController() {
        if (this.isDisabled()) return null;
        if (controller == null) return null;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        int dimId = celestialObjectKey.requireRegisteredBodyId()
            .dimension()
            .getId();
        WorldServer world = server.worldServerForDimension(dimId);
        if (world == null) return null;

        if (controller.getTE(world) instanceof TileStation s) return s;
        Galaxia.LOG.error("[Station] Something that should not be a controller is registered as such");

        return null;
    }
}
