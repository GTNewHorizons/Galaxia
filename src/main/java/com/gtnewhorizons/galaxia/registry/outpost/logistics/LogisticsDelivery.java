package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.UUID;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public class LogisticsDelivery {

    public final ID deliveryId;
    private int remainingTicks;
    public final Data data;

    private LogisticsDelivery(ID id, Data data, int duration) {
        this.deliveryId = id;
        this.remainingTicks = duration;
        this.data = data;
    }

    public record Data(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId, ItemStackWrapper resourceId,
        long amount, LogisticSignal.Scope scope, CelestialObjectId fromBodyId, CelestialObjectId toBodyId,
        double departureOrbitalTime, double tofOrbitalSeconds) {}

    public static LogisticsDelivery create(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, LogisticSignal.Scope scope) {

        AutomatedOutpost from = CelestialClient.getByAssetId(fromAssetId) instanceof AutomatedOutpost o ? o : null;
        AutomatedOutpost to = CelestialClient.getByAssetId(toAssetId) instanceof AutomatedOutpost o ? o : null;
        CelestialObjectId fromBody = from != null ? from.celestialObjectId : CelestialObjectId.INVALID;
        CelestialObjectId toBody = to != null ? to.celestialObjectId : CelestialObjectId.INVALID;

        return createWithTrajectory(
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            scope,
            fromBody,
            toBody,
            0,
            0);
    }

    public static LogisticsDelivery createWithTrajectory(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, LogisticSignal.Scope scope,
        CelestialObjectId fromBodyId, CelestialObjectId toBodyId, double departureOrbitalTime,
        double tofOrbitalSeconds) {
        return createWithTrajectory(
            ID.create(),
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            scope,
            fromBodyId,
            toBodyId,
            departureOrbitalTime,
            tofOrbitalSeconds);
    }

    public static LogisticsDelivery createWithTrajectory(ID id, CelestialAsset.ID fromAssetId,
        CelestialAsset.ID toAssetId, ItemStackWrapper resourceId, long amount, int deliveryTicks,
        LogisticSignal.Scope scope, CelestialObjectId fromBodyId, CelestialObjectId toBodyId,
        double departureOrbitalTime, double tofOrbitalSeconds) {
        return new LogisticsDelivery(
            id,
            new Data(
                fromAssetId,
                toAssetId,
                resourceId,
                amount,
                scope,
                fromBodyId,
                toBodyId,
                departureOrbitalTime,
                tofOrbitalSeconds),
            deliveryTicks);
    }

    public LogisticsDelivery tick() {
        this.remainingTicks -= 1;
        return this;
    }

    public boolean isArrived() {
        return this.remainingTicks <= 0;
    }

    public int getRemainingTicks() {
        return this.remainingTicks;
    }

    public record ID(UUID id) implements WithUUID {

        public static ID create() {
            return new ID(UUID.randomUUID());
        }

        public static ID from(String value) {
            if (value == null) return null;
            return new ID(UUID.fromString(value));
        }

        public static ID from(UUID value) {
            return value == null ? null : new ID(value);
        }

        public static ID from(ID id) {
            if (id == null) return null;
            return new ID(id.id());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
