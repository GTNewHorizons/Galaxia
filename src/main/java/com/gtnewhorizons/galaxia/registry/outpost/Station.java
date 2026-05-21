package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.StationGraph;
import com.gtnewhorizons.galaxia.registry.block.tile.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

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

        // TODO: Make this happen only when contents change or something, otherwise performance will be horrible
        getCannonChestItems().forEach(
            (item, amount) -> logisticsConfig.set(
                item,
                LogisticsResourceConfig.DEFAULT.withOrderSize((int) (long) amount)
                    .withSupplyEnabled(true)));

        LogisticStore.updateSignalsForFacility(this);
    }

    @Override
    public long updateContents(InventoryKey item, int delta, boolean sync) {
        return updateContents(item, delta);
    }

    @Override
    public List<IDistributedInventory> getChildren() {
        TileStationController teController = getTileController();
        if (teController == null) return List.of();

        return teController.getConnectedInventories();
    }

    @Override
    public boolean tryConsumeEnergy(long powerDraw) {
        // TODO
        return true;
    }

    @Override
    public long getEnergyStored() {
        // TODO
        return Integer.MAX_VALUE;
    }

    @Override
    public Stream<ModuleInstance> forEachModule() {
        TileStationController ctrl = getTileController();
        if (ctrl == null) return Stream.of();
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return Stream.of();
        return graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .map(TileHammerCannon::getModuleInstance);
    }

    public Map<ItemStackWrapper, Long> getCannonChestItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        TileStationController ctrl = getTileController();
        if (ctrl == null) return result;
        StationGraph graph = ctrl.getGraph();
        if (graph == null) return result;
        graph.getAttachments(TileHammerCannon.class)
            .filter(TileHammerCannon::isStructureValid)
            .flatMap(
                c -> c.getChestInventories()
                    .stream())
            .filter(Objects::nonNull)
            .forEach(inv -> {
                for (int s = 0; s < inv.getSizeInventory(); s++) {
                    ItemStack stack = inv.getStackInSlot(s);
                    if (stack == null) continue;
                    ItemStackWrapper key = ItemStackWrapper.of(stack);
                    if (key != null) result.merge(key, (long) stack.stackSize, Long::sum);
                }
            });
        return result;
    }

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
