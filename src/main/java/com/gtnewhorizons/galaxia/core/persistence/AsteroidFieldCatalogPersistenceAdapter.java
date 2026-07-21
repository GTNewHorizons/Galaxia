package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;

/**
 * Persists asteroid node content, separate from team knowledge facts.
 * <p>
 * TLDR: forward-only {@code _asteroid_catalog.json} holding a world-scoped node
 * content union — already-restored payloads, nodes referenced by any team fact or
 * scan, and initial-discovered nodes of touched belts. Protects generated body
 * definitions across profile changes without duplicating team knowledge.
 */
final class AsteroidFieldCatalogPersistenceAdapter {

    private static final Logger LOG = LogManager.getLogger(AsteroidFieldCatalogPersistenceAdapter.class);

    private final CelestialDiscoveryScanService scans;

    AsteroidFieldCatalogPersistenceAdapter(CelestialDiscoveryScanService scans) {
        this.scans = scans;
    }

    void load(File file, Gson gson) {
        AsteroidFieldNodeCatalog.clearRestored();
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no asteroid catalog file at {}, using generated content", file);
            return;
        }
        CatalogRegistryJson registry;
        try (FileReader reader = new FileReader(file)) {
            registry = gson.fromJson(reader, CatalogRegistryJson.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: asteroid catalog read error " + file + ": " + e.getMessage(),
                e);
        }
        if (registry == null || registry.belts == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: asteroid catalog file contained no belt list");
        }
        Set<CelestialObjectId> seenBelts = new LinkedHashSet<>();
        for (CatalogBeltJson belt : registry.belts) {
            if (belt == null || belt.beltId == null || belt.nodes == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid catalog belt entry");
            }
            CelestialObjectId beltId = CelestialObjectKeyJsonCodec.requireEnum(
                CelestialObjectId.class,
                belt.beltId,
                "[PERSIST] LOAD FAILED: unknown asteroid belt id " + belt.beltId);
            if (!seenBelts.add(beltId)) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: duplicate asteroid catalog belt " + beltId);
            }
            AsteroidFieldProfile profile = profile(beltId);
            if (profile == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: no asteroid field profile for belt " + beltId);
            }
            AsteroidFieldNodeCatalog.restore(beltId, profile, belt.nodes);
        }
    }

    void save(File file, Gson gson) {
        Map<CelestialObjectId, LinkedHashMap<Integer, AsteroidFieldNodeSnapshot>> byBelt = new LinkedHashMap<>();

        AsteroidFieldNodeCatalog.catalogSnapshotsForMinors(touchedMinorKeys())
            .forEach((beltId, nodes) -> {
                LinkedHashMap<Integer, AsteroidFieldNodeSnapshot> merged = byBelt
                    .computeIfAbsent(beltId, belt -> new LinkedHashMap<>());
                for (AsteroidFieldNodeSnapshot node : nodes) merged.putIfAbsent(node.index(), node);
            });

        for (CelestialObjectId beltId : AsteroidFieldNodeCatalog.restoredBeltIds()) {
            AsteroidFieldNodeCatalog.restored(beltId)
                .ifPresent(catalog -> {
                    LinkedHashMap<Integer, AsteroidFieldNodeSnapshot> merged = byBelt
                        .computeIfAbsent(beltId, belt -> new LinkedHashMap<>());
                    for (AsteroidFieldNodeSnapshot node : catalog.snapshots()) merged.putIfAbsent(node.index(), node);
                });
        }

        CatalogRegistryJson registry = new CatalogRegistryJson();
        registry.belts = new ArrayList<>();
        for (Map.Entry<CelestialObjectId, LinkedHashMap<Integer, AsteroidFieldNodeSnapshot>> entry : byBelt
            .entrySet()) {
            if (entry.getValue()
                .isEmpty()) continue;
            CatalogBeltJson belt = new CatalogBeltJson();
            belt.beltId = entry.getKey()
                .name();
            belt.nodes = new ArrayList<>(
                entry.getValue()
                    .values());
            registry.belts.add(belt);
        }
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(registry, writer);
        } catch (IOException e) {
            throw new IllegalStateException(
                "[PERSIST] SAVE FAILED: asteroid catalog write error " + file + ": " + e.getMessage(),
                e);
        }
    }

    private List<CelestialObjectKey> touchedMinorKeys() {
        Set<CelestialObjectKey> minorKeys = new LinkedHashSet<>();
        CelestialKnowledgeService.snapshotsByTeam()
            .values()
            .forEach(
                teamFacts -> teamFacts.keySet()
                    .forEach(key -> { if (key.isMinorBody()) minorKeys.add(key); }));
        scans.snapshotsByTeam()
            .values()
            .forEach(teamScans -> {
                for (CelestialDiscoveryScanSnapshot snapshot : teamScans) {
                    if (snapshot.anchorKey()
                        .isMinorBody()) minorKeys.add(snapshot.anchorKey());
                    if (snapshot.targetKey() != null && snapshot.targetKey()
                        .isMinorBody()) minorKeys.add(snapshot.targetKey());
                }
            });
        return new ArrayList<>(minorKeys);
    }

    private static AsteroidFieldProfile profile(CelestialObjectId beltId) {
        return GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile())
            .orElse(null);
    }

    static final class CatalogRegistryJson {

        List<CatalogBeltJson> belts;
    }

    static final class CatalogBeltJson {

        String beltId;
        List<AsteroidFieldNodeSnapshot> nodes;
    }
}
