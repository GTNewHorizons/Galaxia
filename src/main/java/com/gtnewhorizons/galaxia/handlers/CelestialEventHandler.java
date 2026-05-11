package com.gtnewhorizons.galaxia.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.ProfilerSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
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
    private int debugMetricsSyncCooldownTicks;

    public CelestialEventHandler() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        HammerTrajectoryLoadTracker.beginTick();

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

            handleSignal(entry.getValue(), orbitalTime);
        }

        HammerTrajectoryLoadTracker.endTick();
        debugMetricsSyncCooldownTicks--;
        if (debugMetricsSyncCooldownTicks <= 0) {
            debugMetricsSyncCooldownTicks = 10;
            syncHammerTrajectoryLoadDebug();
        }

        syncCooldownTicks--;
        if (syncCooldownTicks > 0) return;
        syncCooldownTicks = 20;

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
    private void handleSignal(List<LogisticSignal> signals, double orbitalTime) {
        int size = signals.size();

        for (int i = 0; i < size; i++) {
            LogisticSignal request = signals.get(i);
            if (!request.isRequest()) continue;

            for (int j = 0; j < size; j++) {
                LogisticSignal supply = signals.get(j);
                if (!supply.isSupply()) continue;

                if (!supply.resourceId()
                    .equals(request.resourceId())) continue;
                if (supply.outpostAssetId()
                    .equals(request.outpostAssetId())) continue;

                if (!(CelestialAssetStore.findAsset(supply.outpostAssetId()) instanceof AutomatedFacility supplier))
                    continue;
                if (!(CelestialAssetStore.findAsset(request.outpostAssetId()) instanceof AutomatedFacility requester))
                    continue;

                final boolean sameBody = supplier.celestialObjectId.equals(requester.celestialObjectId);

                final ItemStackWrapper resource = request.resourceId();

                final boolean success = supplier.allOperationalModules()
                    .filter(
                        m -> m.component() instanceof ModuleHammer h && h.canFire() && (sameBody || h.canPlanRoute(m)))
                    .anyMatch(m -> {
                        ModuleHammer hammer = (ModuleHammer) m.component();
                        UUID supplierTeam = CelestialAssetStore.getTeamId(supplier.assetId);
                        HammerDispatchPlanner.Result result = HammerDispatchPlanner.evaluate(
                            supplier,
                            m,
                            requester,
                            resource,
                            LogisticStore.activeDeliveries(),
                            orbitalTime,
                            new HammerDispatchPlanner.RouteObserver() {

                                @Override
                                public void beforeRouteComputation(ModuleInstance hammerModule, ModuleHammer hammer) {
                                    hammer.markRouteProbeAttempted();
                                }

                                @Override
                                public void afterRouteComputation(long elapsedNanos) {
                                    HammerTrajectoryLoadTracker.recordRouteComputation(supplierTeam, elapsedNanos);
                                }
                            });
                        HammerDispatchPlanner.Plan plan = result.plan();
                        if (result.code() != HammerDispatchStatus.Code.READY || plan == null) return false;

                        if (!supplier.tryConsumeInventory(plan.resource(), plan.sendAmount())) return false;
                        if (!hammer.trySpendShotEnergy(m, supplier, plan.requiredEnergy())) {
                            throw new IllegalStateException("HAMMER shot energy became inconsistent");
                        }
                        hammer.markShotDispatched(m);

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
                    });

                if (success) break;
            }
        }
    }

}
