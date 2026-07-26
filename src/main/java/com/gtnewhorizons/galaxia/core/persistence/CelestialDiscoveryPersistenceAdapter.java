package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.galaxia.core.persistence.CelestialObjectKeyJsonCodec.CelestialObjectKeyJson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;

final class CelestialDiscoveryPersistenceAdapter {

    private static final Logger LOG = LogManager.getLogger(CelestialDiscoveryPersistenceAdapter.class);

    private final CelestialDiscoveryScanService scans;

    CelestialDiscoveryPersistenceAdapter(CelestialDiscoveryScanService scans) {
        this.scans = scans;
    }

    void load(File file, Gson gson) {
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no celestial discovery file at {}, clearing scans", file);
            scans.clear();
            return;
        }
        DiscoveryRegistryJson registry;
        try (FileReader reader = new FileReader(file)) {
            registry = gson.fromJson(reader, DiscoveryRegistryJson.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: celestial discovery read error " + file + ": " + e.getMessage(),
                e);
        }
        if (registry == null || registry.teams == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: celestial discovery file contained no team list");
        }
        Map<UUID, List<CelestialDiscoveryScanSnapshot>> snapshotsByTeam = new LinkedHashMap<>();
        Set<PersistedScanKey> scanKeys = new HashSet<>();
        for (DiscoveryTeamJson team : registry.teams) {
            if (team == null || team.teamId == null || team.scans == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed celestial discovery team entry");
            }
            UUID teamId = UUID.fromString(team.teamId);
            List<CelestialDiscoveryScanSnapshot> snapshots = new ArrayList<>();
            for (DiscoveryScanJson scan : team.scans) {
                CelestialDiscoveryScanSnapshot snapshot = decodeScan(teamId, scan);
                PersistedScanKey key = new PersistedScanKey(teamId, snapshot.anchorKey(), snapshot.capability());
                if (!scanKeys.add(key)) throw new IllegalArgumentException("duplicate discovery scan key " + key);
                snapshots.add(snapshot);
            }
            if (snapshotsByTeam.put(teamId, List.copyOf(snapshots)) != null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: duplicate celestial discovery team " + teamId);
            }
        }
        scans.clear();
        snapshotsByTeam.forEach(scans::restore);
    }

    void save(File file, Gson gson) {
        DiscoveryRegistryJson registry = new DiscoveryRegistryJson();
        registry.teams = new ArrayList<>();
        for (Map.Entry<UUID, List<CelestialDiscoveryScanSnapshot>> entry : scans.snapshotsByTeam()
            .entrySet()) {
            DiscoveryTeamJson team = new DiscoveryTeamJson();
            team.teamId = entry.getKey()
                .toString();
            team.scans = new ArrayList<>();
            for (CelestialDiscoveryScanSnapshot snapshot : entry.getValue()) team.scans.add(encodeScan(snapshot));
            registry.teams.add(team);
        }
        AtomicJsonWriter.write(file, gson, registry, "celestial discovery");
    }

    private static DiscoveryScanJson encodeScan(CelestialDiscoveryScanSnapshot snapshot) {
        DiscoveryScanJson json = new DiscoveryScanJson();
        json.anchor = CelestialObjectKeyJsonCodec.encode(snapshot.anchorKey());
        json.radius = snapshot.radius();
        json.revision = snapshot.scopeRevision();
        json.capability = snapshot.capability()
            .name();
        json.status = snapshot.status()
            .name();
        if (snapshot.status() == CelestialDiscoveryScanSnapshot.Status.ACTIVE) {
            json.target = CelestialObjectKeyJsonCodec.encode(snapshot.targetKey());
            json.step = snapshot.step()
                .name();
            json.elapsedTicks = snapshot.elapsedTicks();
        }
        return json;
    }

    private static CelestialDiscoveryScanSnapshot decodeScan(UUID teamId, DiscoveryScanJson json) {
        if (json == null || json.anchor == null || json.capability == null || json.status == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed celestial discovery scan entry");
        }
        CelestialDiscoveryScanSnapshot.Status status = CelestialObjectKeyJsonCodec.requireEnum(
            CelestialDiscoveryScanSnapshot.Status.class,
            json.status,
            "[PERSIST] LOAD FAILED: unknown celestial discovery scan status " + json.status);
        return new CelestialDiscoveryScanSnapshot(
            teamId,
            CelestialObjectKeyJsonCodec.decode(json.anchor),
            json.radius,
            json.revision,
            CelestialObjectKeyJsonCodec.requireEnum(
                CelestialDiscoveryCapability.class,
                json.capability,
                "[PERSIST] LOAD FAILED: unknown discovery capability " + json.capability),
            status,
            status == CelestialDiscoveryScanSnapshot.Status.ACTIVE ? CelestialObjectKeyJsonCodec.decode(json.target)
                : null,
            status == CelestialDiscoveryScanSnapshot.Status.ACTIVE ? CelestialObjectKeyJsonCodec.requireEnum(
                CelestialDiscoveryStep.class,
                json.step,
                "[PERSIST] LOAD FAILED: unknown discovery step " + json.step) : null,
            json.elapsedTicks);
    }

    private record PersistedScanKey(UUID teamId, CelestialObjectKey anchorKey,
        CelestialDiscoveryCapability capability) {}

    static final class DiscoveryRegistryJson {

        List<DiscoveryTeamJson> teams;
    }

    static final class DiscoveryTeamJson {

        String teamId;
        List<DiscoveryScanJson> scans;
    }

    static final class DiscoveryScanJson {

        CelestialObjectKeyJson anchor;
        double radius;
        long revision;
        String capability;
        String status;
        CelestialObjectKeyJson target;
        String step;
        long elapsedTicks;
    }
}
