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
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public class Station extends CelestialAsset implements IDistributedInventory {

    private BlockPos controller;

    public Station(ID assetId, CelestialObjectKey celestialObjectKey, Status status) {
        super(assetId, celestialObjectKey, Kind.STATION, status);
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
        // A station that only gets part of its upkeep counts as unpowered
        return drawEnergy(powerDraw) >= powerDraw;
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

    /** Items the station's hammers can send: the cannons' own chests and the station's shared storage */
    public Map<ItemStackWrapper, Long> getCannonChestItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        List<TileHammerCannon> cannons = formedCannons();
        if (cannons.isEmpty()) return result;
        cannons.forEach(
            cannon -> cannon.getChestItems()
                .forEach((key, amount) -> result.merge(key, amount, Long::sum)));
        aggregatedItems().forEach((key, amount) -> result.merge(key, amount, Long::sum));
        return result;
    }

    /** How much of an item the station's hammers can send while keeping the reserve */
    public long getCannonSupplyAmount(ItemStackWrapper resource, long reserve) {
        List<TileHammerCannon> cannons = formedCannons();
        if (cannons.isEmpty()) return 0L;
        long available = getItemAmount(resource);
        for (TileHammerCannon cannon : cannons) available += cannon.getChestAmount(resource);
        return Math.max(available - reserve, 0L);
    }

    private List<TileHammerCannon> formedCannons() {
        TileStation ctrl = getTileController();
        if (ctrl == null) return List.of();
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return List.of();
        return graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .toList();
    }

    /** Public so network handlers can route filter mutations. */
    public TileStation getTileController() {
        if (this.isDisabled()) return null;
        if (controller == null) return null;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;

        // Stations only exist in the orbit of their body
        OrbitDimensions.Orbit orbit = OrbitDimensions.ofBody(celestialObjectKey)
            .orElse(null);
        if (orbit == null) return null;
        WorldServer world = server.worldServerForDimension(orbit.dimensionId());
        if (world == null) return null;

        if (controller.getTE(world) instanceof TileStation s) return s;

        // A station absorbed into another station's graph keeps its own asset even though it is no longer the
        // graph controller, so dismantling it leaves an orphaned operational asset behind. Destroy it on first
        // detection instead of logging an error every tick. Chunk-unloaded controllers are skipped here: the
        // tile's invalidate() already disabled the asset, so this path is not reached for a normal chunk unload.
        if (world.getChunkProvider()
            .chunkExists(controller.x() >> 4, controller.z() >> 4)) {
            Galaxia.LOG.error(
                "[Station] Controller at {} is not a TileStation; destroying orphaned station {}",
                controller,
                assetId);
            AssetStateSync.SERVER.destroyAsset(assetId);
        }
        return null;
    }
}
