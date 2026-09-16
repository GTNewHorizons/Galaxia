package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class SatelliteDataBufferStore {

    private final Map<CelestialObjectKey, Map<SatelliteDataKey, Long>> pendingDeciKb = new HashMap<>();

    public record Entry(CelestialObjectKey bodyKey, SatelliteDataKey key, long deciKb) {

    }

    public long pendingDeciKb(CelestialObjectKey bodyKey, SatelliteDataKey key) {
        Map<SatelliteDataKey, Long> bodyBuffers = pendingDeciKb.get(bodyKey);
        if (bodyBuffers == null) return 0L;
        return bodyBuffers.getOrDefault(key, 0L);
    }

    /*
     * Starting a producer only checks the producer-side buffer for this exact output key. A finished job may push the
     * buffer over the limit; that overfill blocks the next job until transfers drain it.
     */
    public boolean canStart(CelestialObjectKey bodyKey, SatelliteDataKey key, long localCapacityKbps) {
        return pendingDeciKb(bodyKey, key) <= bufferLimitDeciKb(localCapacityKbps);
    }

    public void finishProduction(CelestialObjectKey bodyKey, SatelliteDataKey key, long producedDeciKb) {
        if (bodyKey == null || key == null || producedDeciKb <= 0L) return;
        pendingDeciKb.computeIfAbsent(bodyKey, ignored -> new HashMap<>())
            .merge(key, producedDeciKb, SatelliteDataBufferStore::addSaturated);
    }

    public List<Entry> producedEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<CelestialObjectKey, Map<SatelliteDataKey, Long>> bodyEntry : pendingDeciKb.entrySet()) {
            for (Map.Entry<SatelliteDataKey, Long> keyEntry : bodyEntry.getValue()
                .entrySet()) {
                long amount = keyEntry.getValue();
                if (amount > 0L) entries.add(new Entry(bodyEntry.getKey(), keyEntry.getKey(), amount));
            }
        }
        return List.copyOf(entries);
    }

    public long drain(CelestialObjectKey bodyKey, SatelliteDataKey key, long requestedDeciKb) {
        if (requestedDeciKb <= 0L) return 0L;
        Map<SatelliteDataKey, Long> bodyBuffers = pendingDeciKb.get(bodyKey);
        if (bodyBuffers == null) return 0L;
        long current = bodyBuffers.getOrDefault(key, 0L);
        long drained = Math.min(current, requestedDeciKb);
        long remaining = current - drained;
        if (remaining > 0L) {
            bodyBuffers.put(key, remaining);
        } else {
            bodyBuffers.remove(key);
        }
        if (bodyBuffers.isEmpty()) pendingDeciKb.remove(bodyKey);
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
