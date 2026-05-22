package com.gtnewhorizons.galaxia.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.ProfilerSyncPacket;
import com.gtnewhorizons.galaxia.registry.block.tile.StationGraph;
import com.gtnewhorizons.galaxia.registry.block.tile.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerTrajectoryLoadTracker;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class CelestialEventHandler {

    // TODO: Is there a centralized way to get ticks?
    private int syncCooldownTicks;

    public CelestialEventHandler() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean profileHammerTrajectoryLoad = hasCreativeProfilerViewer();
        HammerTrajectoryLoadTracker.beginTick(profileHammerTrajectoryLoad);

        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            asset.tick();
        }

        LogisticStore.tickDeliveries();
        double orbitalTime = GalaxiaCelestialAPI.currentOrbitalTime();

        // All signals live in SYSTEM scope (one signal per resource per outpost).
        // Dispatch routing is decided at match time:
        // same planetary anchor → HAMMER
        // different planetary anchors -> BIG HAMMER
        for (Map.Entry<CelestialObjectId, List<LogisticSignal>> entry : LogisticStore
            // TODO: Use different scopes also?
            .allSignalsForScope(LogisticSignal.Scope.SYSTEM)
            .entrySet()) {

            handleSignal(entry.getValue(), orbitalTime, profileHammerTrajectoryLoad);
        }

        HammerTrajectoryLoadTracker.endTick();

        syncCooldownTicks--;
        if (syncCooldownTicks > 0) return;
        syncCooldownTicks = 20;

        if (profileHammerTrajectoryLoad) {
            syncHammerTrajectoryLoadDebug();
        }

        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (player == null) continue;

            UUID playerTeam = TempTeamCompat.getTeam(player);
            UUID playerId = player.getUniqueID();
            Map<CelestialObjectId, Set<CelestialAsset>> teamAssets = CelestialAssetStore.getTeamAssets(playerTeam);
            if (teamAssets == null) continue;
            Set<CelestialAsset> aggregatedAssets = teamAssets.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            List<AssetSyncPacket> playerOutpostPackets = new ArrayList<>();
            for (CelestialAsset asset : aggregatedAssets) {
                playerOutpostPackets.addAll(AssetSyncPacket.figureOutWhatToSend(asset, playerId));
            }
            // TODO: make aggregate packet for this
            for (AssetSyncPacket pkt : playerOutpostPackets) {
                Galaxia.GALAXIA_NETWORK.sendTo(pkt, player);
            }
            for (CelestialAsset asset : aggregatedAssets) {
                asset.clean();
            }

            List<LogisticsDelivery> relevantDeliveries = LogisticStore.activeDeliveries()
                .stream()
                .filter(d -> CelestialAssetStore.isOwnedBy(playerTeam, d.data.fromAssetId()))
                .collect(Collectors.toList());

            Galaxia.GALAXIA_NETWORK.sendTo(LogisticsSyncPacket.from(relevantDeliveries), player);
        }
    }

    private boolean hasCreativeProfilerViewer() {
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (player != null && player.capabilities.isCreativeMode) return true;
        }
        return false;
    }

    private void syncHammerTrajectoryLoadDebug() {
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (player == null || !player.capabilities.isCreativeMode) continue;

            UUID playerTeam = TempTeamCompat.getTeam(player);
            HammerTrajectoryLoadTracker.Snapshot snapshot = HammerTrajectoryLoadTracker.snapshot(playerTeam);
            Galaxia.GALAXIA_NETWORK.sendTo(
                ProfilerSyncPacket.hammerTrajectoryLoad(snapshot.ownMsPerTick(), snapshot.allMsPerTick()),
                player);
        }
    }

    // TODO: Optimize this (O(n^2))
    private void handleSignal(List<LogisticSignal> signals, double orbitalTime, boolean profileHammerTrajectoryLoad) {
        for (int i = 0; i < signals.size(); i++) {
            LogisticSignal request = signals.get(i);
            if (!request.isRequest()) continue;

            for (int j = 0; j < signals.size(); j++) {
                LogisticSignal supply = signals.get(j);
                if (!supply.isSupply()) continue;

                if (!supply.resourceId()
                    .equals(request.resourceId())) continue;
                if (supply.outpostAssetId()
                    .equals(request.outpostAssetId())) continue;

                CelestialAsset supplier = CelestialAssetStore.findAsset(supply.outpostAssetId());
                if (supplier == null) continue;
                CelestialAsset requester = CelestialAssetStore.findAsset(request.outpostAssetId());
                if (requester == null) continue;

                if (handleDispatch(
                    supplier,
                    requester,
                    request.resourceId(),
                    orbitalTime,
                    profileHammerTrajectoryLoad)) {
                    break;
                }
            }
        }
    }

    private boolean handleDispatch(CelestialAsset supplier, CelestialAsset requester, ItemStackWrapper resource,
        double orbitalTime, boolean profileHammerTrajectoryLoad) {

        boolean sameBody = supplier.celestialObjectId.equals(requester.celestialObjectId);

        Map<ModuleInstance, TileHammerCannon> moduleCannon = null;
        if (supplier instanceof Station station) {
            TileStationController ctrl = station.getTileController();
            StationGraph graph = ctrl != null ? ctrl.getGraph() : null;
            if (graph == null) return false;
            moduleCannon = new HashMap<>();
            for (TileHammerCannon c : graph.getAttachments(TileHammerCannon.class)
                .toList()) {
                if (c.isStructureValid()) {
                    moduleCannon.put(c.getModuleInstance(), c);
                }
            }
        }

        UUID routeProfileTeamId = profileHammerTrajectoryLoad ? CelestialAssetStore.getTeamId(supplier.assetId) : null;

        for (ModuleInstance module : supplier.forEachModule()
            .toList()) {
            if (!module.isOperational()) continue;
            if (!(module.component() instanceof ModuleHammer hammer)) continue;
            if (!hammer.canFire()) continue;
            if (!sameBody && !hammer.canPlanRoute(module)) continue;

            TileHammerCannon cannon = moduleCannon != null ? moduleCannon.get(module) : null;
            if (moduleCannon != null && cannon == null) continue;

            if (cannon != null) {
                ResourceFilter<ItemStackWrapper> filter = cannon.getFilter();
                if (!filter.isEmpty() && !filter.test(resource)) continue;
            }

            HammerDispatchPlanner.Result result = HammerDispatchPlanner
                .evaluate(supplier, module, requester, resource, orbitalTime, routeProfileTeamId);

            HammerDispatchPlanner.Plan plan = result.plan();
            if (result.code() != HammerDispatchStatus.Code.READY || plan == null) continue;

            if (supplier instanceof AutomatedFacility af) {
                if (af.updateContents(plan.resource(), -(int) Math.min(plan.sendAmount(), Integer.MAX_VALUE), true)
                    <= 0L) continue;
                if (!hammer.trySpendShotEnergy(module, af, plan.requiredEnergy())) {
                    throw new IllegalStateException("HAMMER shot energy became inconsistent");
                }
            } else if (moduleCannon != null) {
                long remaining = plan.sendAmount();
                for (TileHammerCannon c : moduleCannon.values()) {
                    for (IInventory inv : c.getChestInventories()) {
                        for (int slot = 0; slot < inv.getSizeInventory() && remaining > 0; slot++) {
                            ItemStack stack = inv.getStackInSlot(slot);
                            if (stack != null && resource.item() == stack.getItem()
                                && resource.meta() == stack.getItemDamage()) {
                                long deduct = Math.min(remaining, stack.stackSize);
                                stack.stackSize -= (int) deduct;
                                if (stack.stackSize <= 0) inv.setInventorySlotContents(slot, null);
                                remaining -= deduct;
                            }
                        }
                    }
                    c.markDirty();
                }
                if (remaining > 0) continue;
                if (!hammer.trySpendShotEnergy(plan.requiredEnergy())) {
                    throw new IllegalStateException("HAMMER shot energy became inconsistent");
                }
            } else continue;

            hammer.markShotDispatched(module);

            LogisticsDelivery task = LogisticsDelivery.createWithTrajectory(
                supplier.assetId,
                requester.assetId,
                plan.resource(),
                plan.sendAmount(),
                plan.travelTimeTicks(),
                plan.deliveryScope(),
                supplier.celestialObjectId,
                requester.celestialObjectId,
                orbitalTime,
                plan.tofOrbitalSeconds(),
                plan.route());
            LogisticStore.addDelivery(task);
            return true;
        }
        return false;
    }

}
