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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class CelestialEventHandler {

    public static final long EU_PER_ITEM_PER_DV = 100L;

    // TODO: Is there a centralized way to get ticks?
    private int syncCooldownTicks;

    public CelestialEventHandler() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            // TODO: Ticks other assets
            if (asset.kind != CelestialAsset.Kind.AUTOMATED_OUTPOST
                && asset.kind != CelestialAsset.Kind.AUTOMATED_STATION) continue;

            AutomatedFacility outpost = (AutomatedFacility) asset;
            outpost.tick();
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
                if (asset instanceof AutomatedFacility outpost) {
                    playerOutpostPackets.addAll(AssetSyncPacket.figureOutWhatToSend(outpost, playerId));
                }
            }
            // TODO: make aggregate packet for this
            for (AssetSyncPacket pkt : playerOutpostPackets) {
                Galaxia.GALAXIA_NETWORK.sendTo(pkt, player);
            }

            List<LogisticsDelivery> relevantDeliveries = LogisticStore.activeDeliveries()
                .stream()
                .filter(d -> CelestialAssetStore.isOwnedBy(playerTeam, d.data.fromAssetId()))
                .collect(Collectors.toList());

            Galaxia.GALAXIA_NETWORK.sendTo(LogisticsSyncPacket.from(relevantDeliveries), player);
        }
    }

    // TODO: Optimize this (O(n^2))
    private void handleSignal(List<LogisticSignal> signals, double orbitalTime) {
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
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

                final boolean shareAnchor = GalaxiaCelestialAPI
                    .sharesPlanetaryAnchor(root, supplier.celestialObjectId, requester.celestialObjectId);

                final ItemStackWrapper resource = request.resourceId();
                final LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
                if (supplierCfg == null) continue;
                final long supplierStock = supplier.inventory.getAmount(resource);
                final long availableSurplus = supplierStock - supplierCfg.minReserve();
                if (availableSurplus <= 0) continue;

                LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
                if (requesterCfg == null) continue;
                final long requesterStock = requester.inventory.getAmount(resource);
                final long inboundInTransit = getInboundInTransitAmount(requester.assetId, resource);
                final long requestedAmount = Math
                    .max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);

                final boolean success = supplier.allOperationalModules()
                    .filter(
                        m -> m.component() instanceof ModuleHammer h && h.canFire()
                            && (shareAnchor || h.variant() == HammerVariant.BIG))
                    .map(m -> (ModuleHammer) m.component())
                    .anyMatch(hammer -> {
                        LogisticSignal.Scope deliveryScope = LogisticSignal.Scope.PLANETARY;
                        int travelTime = 1;
                        double osu = 0;

                        final long sendAmount = Math
                            .min(Math.min(requestedAmount, availableSurplus), hammer.maxBatchSize());
                        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

                        double departureDv = 1;
                        if (!supplier.celestialObjectId.equals(requester.celestialObjectId)) {
                            deliveryScope = LogisticSignal.Scope.SYSTEM;
                            CelestialObject srcBody = GalaxiaCelestialAPI
                                .findBodyById(root, supplier.celestialObjectId);
                            CelestialObject dstBody = GalaxiaCelestialAPI
                                .findBodyById(root, requester.celestialObjectId);
                            CelestialObject attractor = srcBody != null ? GalaxiaCelestialAPI.findStar(root, srcBody)
                                : null;

                            if (srcBody == null || dstBody == null || attractor == null) return false;
                            OrbitalTransferPlanner.TransferRoute route = OrbitalTransferPlanner
                                .computeRoute(root, attractor, srcBody, dstBody, orbitalTime, hammer.routePriority());
                            if (route == null) return false;

                            departureDv = route.departureDv();
                            travelTime = route.tofTicks();
                            osu = route.tofOsu();
                            if (!hammer.config()
                                .allows(departureDv, route.tofSeconds())) return false;
                        }

                        final long euPerItem = Math.max(1L, (long) Math.ceil(departureDv * EU_PER_ITEM_PER_DV));
                        final long affordableAmount = Math.min(supplier.getEnergyStored() / euPerItem, sendAmount);
                        final long euRequired = sendAmount * euPerItem;

                        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;
                        if (!supplier.tryConsumeEnergy(euRequired)) return false;
                        if (!supplier.inventory.tryConsume(resource, affordableAmount)) return false;

                        hammer.fire();

                        LogisticsDelivery task = LogisticsDelivery.createWithTrajectory(
                            supplier.assetId,
                            requester.assetId,
                            resource,
                            sendAmount,
                            travelTime,
                            deliveryScope,
                            supplier.celestialObjectId,
                            requester.celestialObjectId,
                            orbitalTime,
                            osu);

                        LogisticStore.addDelivery(task);
                        return true;
                    });

                if (success) break;
            }
        }
    }

    private long getInboundInTransitAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsDelivery task : LogisticStore.activeDeliveries()) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            total += task.data.amount();
        }
        return total;
    }
}
