package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidKnowledgePersistenceAdapter {

    private static final Logger LOG = LogManager.getLogger(AsteroidKnowledgePersistenceAdapter.class);

    private static java.util.Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }

    void load(File file, Gson gson) {
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

    void save(File file, Gson gson) {
        AsteroidKnowledgeRegistryJson registry = snapshotRegistry();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(registry, writer);
        } catch (IOException e) {
            throw new IllegalStateException(
                "[PERSIST] SAVE FAILED: asteroid knowledge write error " + file + ": " + e.getMessage(),
                e);
        }
    }

    private void restore(AsteroidKnowledgeRegistryJson registry) {
        if (registry == null || registry.teams == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: asteroid knowledge file contained no team list");
        }
        for (AsteroidTeamKnowledgeJson teamJson : registry.teams) {
            if (teamJson == null || teamJson.teamId == null || teamJson.fields == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asteroid team knowledge entry");
            }
            UUID teamId = UUID.fromString(teamJson.teamId);
            List<AsteroidFieldKnowledgeSnapshot> snapshots = new ArrayList<>();
            for (AsteroidFieldKnowledgeJson fieldJson : teamJson.fields) {
                snapshots.add(decodeFieldKnowledge(fieldJson));
            }
            AsteroidFieldKnowledgeStore.global()
                .restore(teamId, snapshots, AsteroidKnowledgePersistenceAdapter::profile);
        }
    }

    private AsteroidKnowledgeRegistryJson snapshotRegistry() {
        AsteroidKnowledgeRegistryJson registry = new AsteroidKnowledgeRegistryJson();
        registry.teams = new ArrayList<>();
        Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> knowledgeByTeam = AsteroidFieldKnowledgeStore.global()
            .snapshotsByTeam();
        for (UUID teamId : knowledgeByTeam.keySet()) {
            AsteroidTeamKnowledgeJson teamJson = new AsteroidTeamKnowledgeJson();
            teamJson.teamId = teamId.toString();
            teamJson.fields = new ArrayList<>();
            for (AsteroidFieldKnowledgeSnapshot snapshot : knowledgeByTeam.getOrDefault(teamId, List.of())) {
                teamJson.fields.add(encodeFieldKnowledge(snapshot));
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
        json.nodeSnapshots = new ArrayList<>(snapshot.nodeSnapshots());
        return json;
    }

    private static AsteroidFieldKnowledgeSnapshot decodeFieldKnowledge(AsteroidFieldKnowledgeJson json) {
        if (json == null || json.beltId == null || json.entries == null || json.nodeSnapshots == null) {
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
                        CelestialResourceKnowledgeState.class,
                        entryJson.oreKnowledgeState,
                        "[PERSIST] LOAD FAILED: unknown asteroid ore knowledge state " + entryJson.oreKnowledgeState)));
        }
        return new AsteroidFieldKnowledgeSnapshot(beltId, entries, json.nodeSnapshots);
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
    }

    static final class AsteroidFieldKnowledgeJson {

        String beltId;
        List<AsteroidKnowledgeEntryJson> entries;
        List<AsteroidFieldNodeSnapshot> nodeSnapshots;
    }

    static final class AsteroidKnowledgeEntryJson {

        int index;
        String detectionState;
        String oreKnowledgeState;
    }

}
