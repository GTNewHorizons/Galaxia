package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

final class AsteroidKnowledgePersistenceAdapter {

    private static final Logger LOG = LogManager.getLogger(AsteroidKnowledgePersistenceAdapter.class);

    private AsteroidKnowledgePersistenceAdapter() {}

    static void load(File file, Gson gson) {
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no asteroid knowledge file at {}, skipping", file);
            return;
        }
        AsteroidKnowledgeRegistryJson registry;
        try (FileReader reader = new FileReader(file)) {
            registry = gson.fromJson(reader, AsteroidKnowledgeRegistryJson.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: asteroid knowledge read error " + file + ": " + e.getMessage(),
                e);
        }
        restore(registry);
    }

    static void save(File file, Gson gson) {
        AsteroidKnowledgeRegistryJson registry = snapshotRegistry();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(registry, writer);
        } catch (IOException e) {
            throw new IllegalStateException(
                "[PERSIST] SAVE FAILED: asteroid knowledge write error " + file + ": " + e.getMessage(),
                e);
        }
    }

    private static void restore(AsteroidKnowledgeRegistryJson registry) {
        if (registry == null || registry.teams == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: asteroid knowledge file contained no team list");
        }
        for (AsteroidTeamKnowledgeJson teamJson : registry.teams) {
            if (teamJson == null || teamJson.teamId == null
                || teamJson.fields == null
                || teamJson.scanProgress == null
                || teamJson.scanCompletions == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid team knowledge entry");
            }
            UUID teamId = UUID.fromString(teamJson.teamId);
            List<AsteroidFieldKnowledgeSnapshot> snapshots = new ArrayList<>();
            for (AsteroidFieldKnowledgeJson fieldJson : teamJson.fields) {
                snapshots.add(decodeFieldKnowledge(fieldJson));
            }
            SatelliteNetworkService.restoreAsteroidKnowledge(teamId, snapshots);

            List<AsteroidSatelliteScanSnapshot> scanSnapshots = new ArrayList<>();
            for (AsteroidScanProgressJson scanJson : teamJson.scanProgress) {
                scanSnapshots.add(decodeScanProgress(scanJson));
            }
            SatelliteNetworkService.restoreAsteroidScans(teamId, scanSnapshots);

            List<AsteroidSatelliteScanCompletionSnapshot> completionSnapshots = new ArrayList<>();
            for (AsteroidScanCompletionJson completionJson : teamJson.scanCompletions) {
                completionSnapshots.add(decodeScanCompletion(completionJson));
            }
            SatelliteNetworkService.restoreAsteroidScanCompletions(teamId, completionSnapshots);
        }
    }

    private static AsteroidKnowledgeRegistryJson snapshotRegistry() {
        AsteroidKnowledgeRegistryJson registry = new AsteroidKnowledgeRegistryJson();
        registry.teams = new ArrayList<>();
        Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> knowledgeByTeam = SatelliteNetworkService
            .asteroidKnowledgeSnapshotsByTeam();
        Map<UUID, List<AsteroidSatelliteScanSnapshot>> scansByTeam = SatelliteNetworkService
            .asteroidScanSnapshotsByTeam();
        Map<UUID, List<AsteroidSatelliteScanCompletionSnapshot>> completionsByTeam = SatelliteNetworkService
            .asteroidScanCompletionSnapshotsByTeam();

        LinkedHashSet<UUID> teamIds = new LinkedHashSet<>();
        teamIds.addAll(knowledgeByTeam.keySet());
        teamIds.addAll(scansByTeam.keySet());
        teamIds.addAll(completionsByTeam.keySet());
        for (UUID teamId : teamIds) {
            AsteroidTeamKnowledgeJson teamJson = new AsteroidTeamKnowledgeJson();
            teamJson.teamId = teamId.toString();
            teamJson.fields = new ArrayList<>();
            for (AsteroidFieldKnowledgeSnapshot snapshot : knowledgeByTeam.getOrDefault(teamId, List.of())) {
                teamJson.fields.add(encodeFieldKnowledge(snapshot));
            }
            teamJson.scanProgress = new ArrayList<>();
            for (AsteroidSatelliteScanSnapshot snapshot : scansByTeam.getOrDefault(teamId, List.of())) {
                teamJson.scanProgress.add(encodeScanProgress(snapshot));
            }
            teamJson.scanCompletions = new ArrayList<>();
            for (AsteroidSatelliteScanCompletionSnapshot snapshot : completionsByTeam.getOrDefault(teamId, List.of())) {
                teamJson.scanCompletions.add(encodeScanCompletion(snapshot));
            }
            registry.teams.add(teamJson);
        }
        return registry;
    }

    private static AsteroidFieldKnowledgeJson encodeFieldKnowledge(AsteroidFieldKnowledgeSnapshot snapshot) {
        AsteroidFieldKnowledgeJson json = new AsteroidFieldKnowledgeJson();
        json.beltId = snapshot.beltId()
            .name();
        json.entries = new ArrayList<>();
        for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
            AsteroidKnowledgeEntryJson entryJson = new AsteroidKnowledgeEntryJson();
            entryJson.index = entry.index();
            entryJson.detectionState = entry.detectionState()
                .name();
            entryJson.oreKnowledgeState = entry.oreKnowledgeState()
                .name();
            json.entries.add(entryJson);
        }
        return json;
    }

    private static AsteroidFieldKnowledgeSnapshot decodeFieldKnowledge(AsteroidFieldKnowledgeJson json) {
        if (json == null || json.beltId == null || json.entries == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid field knowledge entry");
        }
        CelestialObjectId beltId = requireEnum(
            CelestialObjectId.class,
            json.beltId,
            "[PERSIST] LOAD FAILED: unknown asteroid belt id " + json.beltId);
        List<AsteroidFieldKnowledgeSnapshot.Entry> entries = new ArrayList<>();
        for (AsteroidKnowledgeEntryJson entryJson : json.entries) {
            if (entryJson == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: null asteroid knowledge entry");
            }
            entries.add(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    entryJson.index,
                    requireEnum(
                        DiscoveryState.class,
                        entryJson.detectionState,
                        "[PERSIST] LOAD FAILED: unknown asteroid detection state " + entryJson.detectionState),
                    requireEnum(
                        AsteroidOreKnowledgeState.class,
                        entryJson.oreKnowledgeState,
                        "[PERSIST] LOAD FAILED: unknown asteroid ore knowledge state " + entryJson.oreKnowledgeState)));
        }
        return new AsteroidFieldKnowledgeSnapshot(beltId, entries);
    }

    private static AsteroidScanProgressJson encodeScanProgress(AsteroidSatelliteScanSnapshot snapshot) {
        AsteroidScanProgressJson json = new AsteroidScanProgressJson();
        json.satelliteId = snapshot.satelliteId()
            .toString();
        json.beltId = snapshot.beltId()
            .name();
        json.asteroidIndex = snapshot.asteroidId()
            .index();
        json.pass = snapshot.pass()
            .name();
        json.elapsedTicks = snapshot.elapsedTicks();
        return json;
    }

    private static AsteroidSatelliteScanSnapshot decodeScanProgress(AsteroidScanProgressJson json) {
        if (json == null || json.satelliteId == null || json.beltId == null || json.pass == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid scan progress entry");
        }
        CelestialObjectId beltId = requireEnum(
            CelestialObjectId.class,
            json.beltId,
            "[PERSIST] LOAD FAILED: unknown asteroid scan belt id " + json.beltId);
        return new AsteroidSatelliteScanSnapshot(
            CelestialAsset.ID.from(json.satelliteId),
            beltId,
            new MinorCelestialBodyId(beltId, json.asteroidIndex),
            requireEnum(
                AsteroidFieldScanPass.class,
                json.pass,
                "[PERSIST] LOAD FAILED: unknown asteroid scan pass " + json.pass),
            json.elapsedTicks);
    }

    private static AsteroidScanCompletionJson encodeScanCompletion(AsteroidSatelliteScanCompletionSnapshot snapshot) {
        AsteroidScanCompletionJson json = new AsteroidScanCompletionJson();
        json.beltId = snapshot.beltId()
            .name();
        json.anchorAsteroidIndex = snapshot.anchorAsteroidId()
            .index();
        json.generationVersion = snapshot.generationVersion();
        return json;
    }

    private static AsteroidSatelliteScanCompletionSnapshot decodeScanCompletion(AsteroidScanCompletionJson json) {
        if (json == null || json.beltId == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid scan completion entry");
        }
        CelestialObjectId beltId = requireEnum(
            CelestialObjectId.class,
            json.beltId,
            "[PERSIST] LOAD FAILED: unknown asteroid scan completion belt id " + json.beltId);
        return new AsteroidSatelliteScanCompletionSnapshot(
            beltId,
            new MinorCelestialBodyId(beltId, json.anchorAsteroidIndex),
            json.generationVersion);
    }

    private static <T extends Enum<T>> T requireEnum(Class<T> cls, String name, String message) {
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException(message, ex);
        }
    }

    static final class AsteroidKnowledgeRegistryJson {

        List<AsteroidTeamKnowledgeJson> teams;
    }

    static final class AsteroidTeamKnowledgeJson {

        String teamId;
        List<AsteroidFieldKnowledgeJson> fields;
        List<AsteroidScanProgressJson> scanProgress;
        List<AsteroidScanCompletionJson> scanCompletions;
    }

    static final class AsteroidFieldKnowledgeJson {

        String beltId;
        List<AsteroidKnowledgeEntryJson> entries;
    }

    static final class AsteroidKnowledgeEntryJson {

        int index;
        String detectionState;
        String oreKnowledgeState;
    }

    static final class AsteroidScanProgressJson {

        String satelliteId;
        String beltId;
        int asteroidIndex;
        String pass;
        int elapsedTicks;
    }

    static final class AsteroidScanCompletionJson {

        String beltId;
        int anchorAsteroidIndex;
        int generationVersion;
    }
}
