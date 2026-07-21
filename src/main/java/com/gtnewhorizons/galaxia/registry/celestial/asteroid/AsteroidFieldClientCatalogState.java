package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Client-side asteroid node content synced from the server, separate from team facts.
 * <p>
 * TLDR: holds only node payloads ({@link AsteroidFieldNodeSnapshot}) so logical
 * clients can decorate minors without sharing the server-restored static catalog
 * in an integrated JVM. It never stores discovery/resource facts.
 */
public final class AsteroidFieldClientCatalogState {

    private static Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> nodesByBelt = Map.of();
    private static int version = 0;

    private AsteroidFieldClientCatalogState() {}

    public static void update(Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> catalogs) {
        if (catalogs == null || catalogs.isEmpty()) {
            nodesByBelt = Map.of();
            version++;
            return;
        }
        Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> copy = new LinkedHashMap<>();
        catalogs.forEach((beltId, nodes) -> {
            if (beltId == null) throw new IllegalArgumentException("catalog belt id cannot be null");
            copy.put(beltId, List.copyOf(nodes == null ? List.of() : nodes));
        });
        nodesByBelt = Map.copyOf(copy);
        version++;
    }

    public static void clear() {
        nodesByBelt = Map.of();
        version++;
    }

    public static int version() {
        return version;
    }

    /** Node catalog for a belt: synced content when present, else server-restored/generated. */
    public static AsteroidFieldNodeCatalog catalog(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        List<AsteroidFieldNodeSnapshot> saved = nodesByBelt.get(beltId);
        if (saved != null && !saved.isEmpty()) return AsteroidFieldNodeCatalog.fromSnapshots(beltId, profile, saved);
        return AsteroidFieldNodeCatalog.restored(beltId)
            .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile));
    }
}
