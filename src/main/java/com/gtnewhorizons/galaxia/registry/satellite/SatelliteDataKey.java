package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/*
 * Data keys are typed, with an optional origin body. A null origin means "any origin" demand; produced data keeps its
 * real origin so consumers can ask for either any prospecting data or data from a specific planet.
 */
public record SatelliteDataKey(SatelliteDataType type, CelestialObjectKey origin) {

    public SatelliteDataKey {
        type = Objects.requireNonNull(type, "type");
    }

    public static SatelliteDataKey any(SatelliteDataType type) {
        return new SatelliteDataKey(type, null);
    }

    public static SatelliteDataKey origin(SatelliteDataType type, CelestialObjectKey origin) {
        return new SatelliteDataKey(type, Objects.requireNonNull(origin, "origin"));
    }

    public static SatelliteDataKey origin(SatelliteDataType type, CelestialObjectId origin) {
        return origin(type, CelestialObjectKey.registered(Objects.requireNonNull(origin, "origin")));
    }

    public boolean hasOrigin() {
        return origin != null;
    }

    public boolean matchesProduced(SatelliteDataKey producedKey) {
        if (producedKey == null || type != producedKey.type) return false;
        return origin == null || origin.equals(producedKey.origin);
    }

    /*
     * Exact origin requests must consume first. If any-origin demand was mixed in here, a generic consumer could drain
     * data that a specific-origin machine is waiting for.
     */
    public static List<SatelliteDataKey> matchingDemandKeys(SatelliteDataKey producedKey,
        Collection<SatelliteDataKey> demandKeys) {
        List<SatelliteDataKey> exact = new ArrayList<>();
        List<SatelliteDataKey> any = new ArrayList<>();
        if (producedKey == null || demandKeys == null) return exact;
        for (SatelliteDataKey demandKey : demandKeys) {
            if (demandKey == null || !demandKey.matchesProduced(producedKey)) continue;
            if (demandKey.origin == null) {
                any.add(demandKey);
            } else {
                exact.add(demandKey);
            }
        }
        return exact.isEmpty() ? any : exact;
    }
}
