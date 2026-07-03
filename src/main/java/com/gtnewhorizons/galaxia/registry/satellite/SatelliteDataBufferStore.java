package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class SatelliteDataBufferStore {

    /*
     * Produced data and consumer demand are tracked separately because they are not a single inventory.
     * A producer can overfill its local per-key buffer when a job completes, but new jobs only start while that
     * produced-key buffer is under the local satellite capacity. Demand is the destination-side reservation that tells
     * the transfer planner where compatible data can be sent next.
     */
    private final Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> pendingDeciKb = new HashMap<>();
    private final Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> demandDeciKb = new HashMap<>();

    public record Entry(CelestialObjectKey bodyKey, SatelliteDataKey key, long deciKb) {

        public CelestialObjectId bodyId() {
            return bodyKey.requireRegisteredBodyId();
        }
    }

    public long pendingDeciKb(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key) {
        return amount(pendingDeciKb, teamId, bodyKey, key);
    }

    public long pendingDeciKb(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key) {
        return pendingDeciKb(teamId, CelestialObjectKey.registered(bodyId), key);
    }

    /*
     * Starting a producer only checks the producer-side buffer for this exact output key. A finished job may push the
     * buffer over the limit; that overfill blocks the next job until transfers drain it.
     */
    public boolean canStart(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key, long localCapacityKbps) {
        return pendingDeciKb(teamId, bodyKey, key) <= bufferLimitDeciKb(localCapacityKbps);
    }

    public boolean canStart(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long localCapacityKbps) {
        return canStart(teamId, CelestialObjectKey.registered(bodyId), key, localCapacityKbps);
    }

    public void finishProduction(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key, long producedDeciKb) {
        add(pendingDeciKb, teamId, bodyKey, key, producedDeciKb);
    }

    public void finishProduction(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long producedDeciKb) {
        finishProduction(teamId, CelestialObjectKey.registered(bodyId), key, producedDeciKb);
    }

    public long pendingDemandDeciKb(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key) {
        return amount(demandDeciKb, teamId, bodyKey, key);
    }

    public long pendingDemandDeciKb(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key) {
        return pendingDemandDeciKb(teamId, CelestialObjectKey.registered(bodyId), key);
    }

    public void requestData(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key, long requestedDeciKb) {
        add(demandDeciKb, teamId, bodyKey, key, requestedDeciKb);
    }

    public void requestData(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long requestedDeciKb) {
        requestData(teamId, CelestialObjectKey.registered(bodyId), key, requestedDeciKb);
    }

    public List<Entry> producedEntries(UUID teamId) {
        return entries(pendingDeciKb, teamId);
    }

    public List<Entry> demandEntries(UUID teamId) {
        return entries(demandDeciKb, teamId);
    }

    /*
     * Transfer drains both sides together: source pending data and destination demand. If either side has less than the
     * requested amount, the smaller side determines how much actually moved.
     */
    public long transfer(UUID teamId, CelestialObjectKey sourceBodyKey, SatelliteDataKey sourceKey,
        CelestialObjectKey destinationBodyKey, SatelliteDataKey demandKey, long requestedDeciKb) {
        long drained = drain(pendingDeciKb, sourceBodyKey, sourceKey, requestedDeciKb, teamId);
        return drain(demandDeciKb, destinationBodyKey, demandKey, drained, teamId);
    }

    public long transfer(UUID teamId, CelestialObjectId sourceBodyId, SatelliteDataKey sourceKey,
        CelestialObjectId destinationBodyId, SatelliteDataKey demandKey, long requestedDeciKb) {
        return transfer(
            teamId,
            CelestialObjectKey.registered(sourceBodyId),
            sourceKey,
            CelestialObjectKey.registered(destinationBodyId),
            demandKey,
            requestedDeciKb);
    }

    public long drain(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key, long requestedDeciKb) {
        return drain(pendingDeciKb, bodyKey, key, requestedDeciKb, teamId);
    }

    public long drain(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long requestedDeciKb) {
        return drain(teamId, CelestialObjectKey.registered(bodyId), key, requestedDeciKb);
    }

    public void clear() {
        pendingDeciKb.clear();
        demandDeciKb.clear();
    }

    private static void add(Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> buffers, UUID teamId,
        CelestialObjectKey bodyKey, SatelliteDataKey key, long deciKb) {
        if (teamId == null || bodyKey == null || key == null || deciKb <= 0L) return;
        buffers.computeIfAbsent(teamId, ignored -> new HashMap<>())
            .computeIfAbsent(bodyKey, ignored -> new HashMap<>())
            .merge(key, deciKb, SatelliteDataBufferStore::addSaturated);
    }

    private static long amount(Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> buffers, UUID teamId,
        CelestialObjectKey bodyKey, SatelliteDataKey key) {
        Map<CelestialObjectKey, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return 0L;
        Map<SatelliteDataKey, Long> bodyBuffers = teamBuffers.get(bodyKey);
        if (bodyBuffers == null) return 0L;
        return bodyBuffers.getOrDefault(key, 0L);
    }

    private static List<Entry> entries(Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> buffers,
        UUID teamId) {
        Map<CelestialObjectKey, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<CelestialObjectKey, Map<SatelliteDataKey, Long>> bodyEntry : teamBuffers.entrySet()) {
            for (Map.Entry<SatelliteDataKey, Long> keyEntry : bodyEntry.getValue()
                .entrySet()) {
                long amount = keyEntry.getValue();
                if (amount > 0L) entries.add(new Entry(bodyEntry.getKey(), keyEntry.getKey(), amount));
            }
        }
        return List.copyOf(entries);
    }

    private static long drain(Map<UUID, Map<CelestialObjectKey, Map<SatelliteDataKey, Long>>> buffers,
        CelestialObjectKey bodyKey, SatelliteDataKey key, long requestedDeciKb, UUID teamId) {
        if (requestedDeciKb <= 0L) return 0L;
        Map<CelestialObjectKey, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return 0L;
        Map<SatelliteDataKey, Long> bodyBuffers = teamBuffers.get(bodyKey);
        if (bodyBuffers == null) return 0L;
        long current = bodyBuffers.getOrDefault(key, 0L);
        long drained = Math.min(current, requestedDeciKb);
        long remaining = current - drained;
        if (remaining > 0L) {
            bodyBuffers.put(key, remaining);
        } else {
            bodyBuffers.remove(key);
        }
        if (bodyBuffers.isEmpty()) teamBuffers.remove(bodyKey);
        if (teamBuffers.isEmpty()) buffers.remove(teamId);
        return drained;
    }

    private static long bufferLimitDeciKb(long localCapacityKbps) {
        return SatelliteBandwidthFormatter.kilobits(localCapacityKbps);
    }

    private static long addSaturated(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
