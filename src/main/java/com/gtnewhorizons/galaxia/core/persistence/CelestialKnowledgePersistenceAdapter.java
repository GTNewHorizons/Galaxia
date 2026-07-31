package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.gtnewhorizons.galaxia.core.persistence.CelestialObjectKeyJsonCodec.CelestialObjectKeyJson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;

/**
 * Persists shared team knowledge facts keyed by {@link CelestialObjectKey}.
 * <p>
 * TLDR: forward-only {@code _celestial_knowledge.json} for the single
 * {@link CelestialKnowledgeService} store. Restore is atomic — duplicate,
 * unknown-Key, and invalid-enum entries fail before any team is applied, and a
 * missing file means an empty shared store.
 */
final class CelestialKnowledgePersistenceAdapter {

    private static final Logger LOG = LogManager.getLogger(CelestialKnowledgePersistenceAdapter.class);

    void load(File file, Gson gson) {
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no celestial knowledge file at {}, clearing facts", file);
            CelestialKnowledgeService.clearFacts();
            return;
        }
        KnowledgeRegistryJson registry;
        try (FileReader reader = new FileReader(file)) {
            registry = gson.fromJson(reader, KnowledgeRegistryJson.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: celestial knowledge read error " + file + ": " + e.getMessage(),
                e);
        }
        if (registry == null || registry.teams == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: celestial knowledge file contained no team list");
        }

        Map<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> factsByTeam = new LinkedHashMap<>();
        for (KnowledgeTeamJson team : registry.teams) {
            if (team == null || team.teamId == null || team.facts == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed celestial knowledge team entry");
            }
            UUID teamId = UUID.fromString(team.teamId);
            Map<CelestialObjectKey, CelestialKnowledgeFacts> teamFacts = new LinkedHashMap<>();
            for (KnowledgeFactJson fact : team.facts) {
                if (fact == null || fact.key == null
                    || fact.discoveryState == null
                    || fact.resourceKnowledgeState == null) {
                    throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed celestial knowledge fact entry");
                }
                CelestialObjectKey key = CelestialObjectKeyJsonCodec.decode(fact.key);
                // Fail loudly before any restore if the key cannot be materialized.
                CelestialRegistry.initialKnowledge(key);
                CelestialKnowledgeFacts facts = CelestialKnowledgeFacts.of(
                    CelestialObjectKeyJsonCodec.requireEnum(
                        DiscoveryState.class,
                        fact.discoveryState,
                        "[PERSIST] LOAD FAILED: unknown discovery state " + fact.discoveryState),
                    CelestialObjectKeyJsonCodec.requireEnum(
                        CelestialResourceKnowledgeState.class,
                        fact.resourceKnowledgeState,
                        "[PERSIST] LOAD FAILED: unknown resource knowledge state " + fact.resourceKnowledgeState));
                if (teamFacts.put(key, facts) != null) {
                    throw new IllegalStateException("[PERSIST] LOAD FAILED: duplicate celestial knowledge fact " + key);
                }
            }
            if (factsByTeam.put(teamId, teamFacts) != null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: duplicate celestial knowledge team " + teamId);
            }
        }
        CelestialKnowledgeService.restoreAll(factsByTeam);
    }

    void save(File file, Gson gson) {
        KnowledgeRegistryJson registry = new KnowledgeRegistryJson();
        registry.teams = new ArrayList<>();
        for (Map.Entry<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> entry : CelestialKnowledgeService
            .snapshotsByTeam()
            .entrySet()) {
            KnowledgeTeamJson team = new KnowledgeTeamJson();
            team.teamId = entry.getKey()
                .toString();
            team.facts = new ArrayList<>();
            for (Map.Entry<CelestialObjectKey, CelestialKnowledgeFacts> fact : entry.getValue()
                .entrySet()) {
                KnowledgeFactJson json = new KnowledgeFactJson();
                json.key = CelestialObjectKeyJsonCodec.encode(fact.getKey());
                json.discoveryState = fact.getValue()
                    .discoveryState()
                    .name();
                json.resourceKnowledgeState = fact.getValue()
                    .resourceKnowledgeState()
                    .name();
                team.facts.add(json);
            }
            registry.teams.add(team);
        }
        AtomicJsonWriter.write(file, gson, registry, "celestial knowledge");
    }

    static final class KnowledgeRegistryJson {

        List<KnowledgeTeamJson> teams;
    }

    static final class KnowledgeTeamJson {

        String teamId;
        List<KnowledgeFactJson> facts;
    }

    static final class KnowledgeFactJson {

        CelestialObjectKeyJson key;
        String discoveryState;
        String resourceKnowledgeState;
    }
}
