package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class SatelliteDataBufferStore {

    /*
     * Produced data and consumer demand are tracked separately because they are not a single inventory.
     * A producer can overfill its local per-key buffer when a job completes, but new jobs only start while that
     * produced-key buffer is under the local satellite capacity. Demand is the destination-side reservation that tells
     * the transfer planner where compatible data can be sent next.
     */
    private final Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> pendingDeciKb = new HashMap<>();
    private final Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> demandDeciKb = new HashMap<>();

    public record Entry(CelestialObjectId bodyId, SatelliteDataKey key, long deciKb) {}

    public long pendingDeciKb(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key) {
        return amount(pendingDeciKb, teamId, bodyId, key);
    }

    /*
     * Starting a producer only checks the producer-side buffer for this exact output key. A finished job may push the
     * buffer over the limit; that overfill blocks the next job until transfers drain it.
     */
    public boolean canStart(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long localCapacityKbps) {
        return pendingDeciKb(teamId, bodyId, key) <= bufferLimitDeciKb(localCapacityKbps);
    }

    public void finishProduction(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long producedDeciKb) {
        add(pendingDeciKb, teamId, bodyId, key, producedDeciKb);
    }

    public long pendingDemandDeciKb(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key) {
        return amount(demandDeciKb, teamId, bodyId, key);
    }

    public void requestData(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long requestedDeciKb) {
        add(demandDeciKb, teamId, bodyId, key, requestedDeciKb);
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
    public long transfer(UUID teamId, CelestialObjectId sourceBodyId, SatelliteDataKey sourceKey,
        CelestialObjectId destinationBodyId, SatelliteDataKey demandKey, long requestedDeciKb) {
        long drained = drain(pendingDeciKb, sourceBodyId, sourceKey, requestedDeciKb, teamId);
        return drain(demandDeciKb, destinationBodyId, demandKey, drained, teamId);
    }

    public long drain(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey key, long requestedDeciKb) {
        return drain(pendingDeciKb, bodyId, key, requestedDeciKb, teamId);
    }

    public void clear() {
        pendingDeciKb.clear();
        demandDeciKb.clear();
    }

    private static void add(Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> buffers, UUID teamId,
        CelestialObjectId bodyId, SatelliteDataKey key, long deciKb) {
        if (teamId == null || bodyId == null || key == null || deciKb <= 0L) return;
        buffers.computeIfAbsent(teamId, ignored -> new HashMap<>())
            .computeIfAbsent(bodyId, ignored -> new HashMap<>())
            .merge(key, deciKb, SatelliteDataBufferStore::addSaturated);
    }

    private static long amount(Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> buffers, UUID teamId,
        CelestialObjectId bodyId, SatelliteDataKey key) {
        Map<CelestialObjectId, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return 0L;
        Map<SatelliteDataKey, Long> bodyBuffers = teamBuffers.get(bodyId);
        if (bodyBuffers == null) return 0L;
        return bodyBuffers.getOrDefault(key, 0L);
    }

    private static List<Entry> entries(Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> buffers,
        UUID teamId) {
        Map<CelestialObjectId, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<CelestialObjectId, Map<SatelliteDataKey, Long>> bodyEntry : teamBuffers.entrySet()) {
            for (Map.Entry<SatelliteDataKey, Long> keyEntry : bodyEntry.getValue()
                .entrySet()) {
                long amount = keyEntry.getValue();
                if (amount > 0L) entries.add(new Entry(bodyEntry.getKey(), keyEntry.getKey(), amount));
            }
        }
        return List.copyOf(entries);
    }

    private static long drain(Map<UUID, Map<CelestialObjectId, Map<SatelliteDataKey, Long>>> buffers,
        CelestialObjectId bodyId, SatelliteDataKey key, long requestedDeciKb, UUID teamId) {
        if (requestedDeciKb <= 0L) return 0L;
        Map<CelestialObjectId, Map<SatelliteDataKey, Long>> teamBuffers = buffers.get(teamId);
        if (teamBuffers == null) return 0L;
        Map<SatelliteDataKey, Long> bodyBuffers = teamBuffers.get(bodyId);
        if (bodyBuffers == null) return 0L;
        long current = bodyBuffers.getOrDefault(key, 0L);
        long drained = Math.min(current, requestedDeciKb);
        long remaining = current - drained;
        if (remaining > 0L) {
            bodyBuffers.put(key, remaining);
        } else {
            bodyBuffers.remove(key);
        }
        if (bodyBuffers.isEmpty()) teamBuffers.remove(bodyId);
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
