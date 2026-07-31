package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Objects;
import java.util.UUID;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
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

    public static final class Data {

        private final CelestialAsset.ID fromAssetId;
        private final CelestialAsset.ID toAssetId;
        private final ItemStackWrapper resourceId;
        private long amount;
        private final LogisticSignal.Scope scope;
        private final CelestialObjectKey fromBodyKey;
        private final CelestialObjectKey toBodyKey;
        private final double departureOrbitalTime;
        private final double tofOrbitalSeconds;
        private final OrbitalTransferPlanner.TransferRoute transferRoute;

        public Data(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId, ItemStackWrapper resourceId,
            long amount, LogisticSignal.Scope scope, CelestialObjectKey fromBodyKey, CelestialObjectKey toBodyKey,
            double departureOrbitalTime, double tofOrbitalSeconds, OrbitalTransferPlanner.TransferRoute transferRoute) {
            this.fromAssetId = fromAssetId;
            this.toAssetId = toAssetId;
            this.resourceId = resourceId;
            this.amount = amount;
            this.scope = scope;
            this.fromBodyKey = fromBodyKey;
            this.toBodyKey = toBodyKey;
            this.departureOrbitalTime = departureOrbitalTime;
            this.tofOrbitalSeconds = tofOrbitalSeconds;
            this.transferRoute = transferRoute;
        }

        public CelestialAsset.ID fromAssetId() {
            return fromAssetId;
        }

        public CelestialAsset.ID toAssetId() {
            return toAssetId;
        }

        public ItemStackWrapper resourceId() {
            return resourceId;
        }

        public long amount() {
            return amount;
        }

        void setAmount(long amount) {
            this.amount = amount;
        }

        public LogisticSignal.Scope scope() {
            return scope;
        }

        public CelestialObjectKey fromBodyKey() {
            return fromBodyKey;
        }

        public CelestialObjectKey toBodyKey() {
            return toBodyKey;
        }

        public double departureOrbitalTime() {
            return departureOrbitalTime;
        }

        public double tofOrbitalSeconds() {
            return tofOrbitalSeconds;
        }

        public OrbitalTransferPlanner.TransferRoute transferRoute() {
            return transferRoute;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Data other)) return false;
            return amount == other.amount
                && Double.doubleToLongBits(departureOrbitalTime) == Double.doubleToLongBits(other.departureOrbitalTime)
                && Double.doubleToLongBits(tofOrbitalSeconds) == Double.doubleToLongBits(other.tofOrbitalSeconds)
                && Objects.equals(fromAssetId, other.fromAssetId)
                && Objects.equals(toAssetId, other.toAssetId)
                && Objects.equals(resourceId, other.resourceId)
                && scope == other.scope
                && Objects.equals(fromBodyKey, other.fromBodyKey)
                && Objects.equals(toBodyKey, other.toBodyKey)
                && Objects.equals(transferRoute, other.transferRoute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                fromAssetId,
                toAssetId,
                resourceId,
                amount,
                scope,
                fromBodyKey,
                toBodyKey,
                departureOrbitalTime,
                tofOrbitalSeconds,
                transferRoute);
        }

        @Override
        public String toString() {
            return "Data[fromAssetId=" + fromAssetId
                + ", toAssetId="
                + toAssetId
                + ", resourceId="
                + resourceId
                + ", amount="
                + amount
                + ", scope="
                + scope
                + ", fromBodyKey="
                + fromBodyKey
                + ", toBodyKey="
                + toBodyKey
                + ", departureOrbitalTime="
                + departureOrbitalTime
                + ", tofOrbitalSeconds="
                + tofOrbitalSeconds
                + ", transferRoute="
                + transferRoute
                + "]";
        }
    }

    public static LogisticsDelivery create(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, LogisticSignal.Scope scope) {

        AutomatedFacility from = CelestialClient.getByAssetId(fromAssetId) instanceof AutomatedFacility o ? o : null;
        AutomatedFacility to = CelestialClient.getByAssetId(toAssetId) instanceof AutomatedFacility o ? o : null;
        CelestialObjectKey fromBody = from != null ? from.celestialObjectKey : null;
        CelestialObjectKey toBody = to != null ? to.celestialObjectKey : null;

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
            0,
            null);
    }

    public static LogisticsDelivery createWithTrajectory(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, LogisticSignal.Scope scope,
        CelestialObjectKey fromBodyKey, CelestialObjectKey toBodyKey, double departureOrbitalTime,
        double tofOrbitalSeconds) {
        return createWithTrajectory(
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            scope,
            fromBodyKey,
            toBodyKey,
            departureOrbitalTime,
            tofOrbitalSeconds,
            null);
    }

    public static LogisticsDelivery createWithTrajectory(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, LogisticSignal.Scope scope,
        CelestialObjectKey fromBodyKey, CelestialObjectKey toBodyKey, double departureOrbitalTime,
        double tofOrbitalSeconds, OrbitalTransferPlanner.TransferRoute transferRoute) {
        return createWithTrajectory(
            ID.create(),
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            scope,
            fromBodyKey,
            toBodyKey,
            departureOrbitalTime,
            tofOrbitalSeconds,
            transferRoute);
    }

    public static LogisticsDelivery createWithTrajectory(ID id, CelestialAsset.ID fromAssetId,
        CelestialAsset.ID toAssetId, ItemStackWrapper resourceId, long amount, int deliveryTicks,
        LogisticSignal.Scope scope, CelestialObjectKey fromBodyKey, CelestialObjectKey toBodyKey,
        double departureOrbitalTime, double tofOrbitalSeconds) {
        return createWithTrajectory(
            id,
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            scope,
            fromBodyKey,
            toBodyKey,
            departureOrbitalTime,
            tofOrbitalSeconds,
            null);
    }

    public static LogisticsDelivery createWithTrajectory(ID id, CelestialAsset.ID fromAssetId,
        CelestialAsset.ID toAssetId, ItemStackWrapper resourceId, long amount, int deliveryTicks,
        LogisticSignal.Scope scope, CelestialObjectKey fromBodyKey, CelestialObjectKey toBodyKey,
        double departureOrbitalTime, double tofOrbitalSeconds, OrbitalTransferPlanner.TransferRoute transferRoute) {
        return new LogisticsDelivery(
            id,
            new Data(
                fromAssetId,
                toAssetId,
                resourceId,
                amount,
                scope,
                fromBodyKey,
                toBodyKey,
                departureOrbitalTime,
                tofOrbitalSeconds,
                transferRoute),
            deliveryTicks);
    }

    public LogisticsDelivery tick() {
        this.remainingTicks -= 1;
        return this;
    }

    public LogisticsDelivery setAmount(long amount) {
        this.data.setAmount(amount);
        return this;
    }

    public LogisticsDelivery withAmount(long amount) {
        return setAmount(amount);
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
