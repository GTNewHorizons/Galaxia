package com.gtnewhorizons.galaxia.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.ProfilerSyncPacket;
import com.gtnewhorizons.galaxia.core.network.SatelliteNetworkSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerTrajectoryLoadTracker;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class CelestialEventHandler {

    // TODO: Is there a centralized way to get ticks?
    private int syncCooldownTicks;

    private final CelestialServerRuntime celestialRuntime;

    public CelestialEventHandler(CelestialServerRuntime celestialRuntime) {
        this.celestialRuntime = celestialRuntime;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean profileHammerTrajectoryLoad = hasCreativeProfilerViewer();
        HammerTrajectoryLoadTracker.beginTick(profileHammerTrajectoryLoad);

        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            asset.tick();
        }
        celestialRuntime.tick();

        List<LogisticSignal> signals = LogisticStore.collectSignals(CelestialAssetStore.allAssets());
        LogisticStore.tickDeliveries();
        double orbitalTime = GalaxiaCelestialAPI.currentOrbitalTime();

        // All signals live in SYSTEM scope (one signal per resource per outpost).
        // Dispatch routing is decided at match time:
        // same planetary anchor → HAMMER
        // different planetary anchors -> BIG HAMMER
        for (Map.Entry<CelestialObjectKey, List<LogisticSignal>> entry : LogisticStore
            // TODO: Use different scopes also?
            .groupSignals(signals, LogisticSignal.Scope.SYSTEM)
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

        Map<UUID, SatelliteNetworkSyncPacket> satellitePackets = new HashMap<>();
        Map<UUID, CelestialKnowledgeSyncPacket> knowledgePackets = new HashMap<>();
        Map<UUID, LogisticsSyncPacket> logisticsPackets = new HashMap<>();
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (player == null) continue;

            UUID playerTeam = GTTeamsCompat.getTeam(player);
            UUID playerId = player.getUniqueID();
            final boolean toClear = TeamEventHandler.playersToClear.remove(playerId);
            if (toClear) {
                AssetStateSync.SERVER.resetRecipient(playerId);
            }

            SatelliteNetworkSyncPacket satellitePacket = satellitePackets.computeIfAbsent(
                playerTeam,
                team -> new SatelliteNetworkSyncPacket(SatelliteNetworkService.rebuild(team, orbitalTime)));
            Galaxia.GALAXIA_NETWORK.sendTo(satellitePacket, player);
            CelestialKnowledgeSyncPacket knowledgePacket = knowledgePackets.computeIfAbsent(
                playerTeam,
                team -> CelestialKnowledgeSyncPacket.forTeam(team, celestialRuntime.scans()));
            Galaxia.GALAXIA_NETWORK.sendTo(knowledgePacket, player);
            LogisticsSyncPacket logisticsPacket = logisticsPackets.computeIfAbsent(playerTeam, team -> {
                List<LogisticsDelivery> relevantDeliveries = LogisticStore.activeDeliveries()
                    .stream()
                    .filter(d -> CelestialAssetStore.isOwnedBy(team, d.data.fromAssetId()))
                    .collect(Collectors.toList());
                return LogisticsSyncPacket.from(relevantDeliveries, LogisticStore.signalsOwnedBy(team, signals));
            });
            Galaxia.GALAXIA_NETWORK.sendTo(logisticsPacket, player);
        }
        AssetStateSync.SERVER.publishPeriodic();
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

            UUID playerTeam = GTTeamsCompat.getTeam(player);
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
                if (!CelestialAssetStore.isOwnedBy(CelestialAssetStore.getTeamId(supplier.assetId), requester.assetId))
                    continue;

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

        boolean sameBody = supplier.celestialObjectKey.equals(requester.celestialObjectKey);

        Map<ModuleInstance, TileHammerCannon> moduleCannon = null;
        if (supplier instanceof Station station) {
            TileStation ctrl = station.getTileController();
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
                if (af.extract(plan.resource(), plan.sendAmount()) <= 0L) continue;
                if (!hammer.trySpendShotEnergy(module, af, plan.requiredEnergy())) {
                    throw new IllegalStateException("HAMMER shot energy became inconsistent");
                }
            } else if (moduleCannon != null) {
                if (!cannon.tryExtractPackage(plan.resource(), plan.sendAmount())) continue;
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
                supplier.celestialObjectKey,
                requester.celestialObjectKey,
                orbitalTime,
                plan.tofOrbitalOsu(),
                plan.route());
            LogisticStore.addDelivery(task);
            return true;
        }
        return false;
    }

}
