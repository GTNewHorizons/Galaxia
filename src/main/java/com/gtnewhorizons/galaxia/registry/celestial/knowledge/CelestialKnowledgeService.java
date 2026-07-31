package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/**
 * Sole server-side owner of mutable team knowledge facts, keyed by
 * {@link CelestialObjectKey}.
 * <p>
 * TLDR: stores explicit per-team overrides; reads fall back to
 * {@link CelestialRegistry#initialKnowledge(CelestialObjectKey)} definition
 * defaults. Discovery domains choose scan work but must mutate facts only
 * through this service. Scan progress lifecycle lives in
 * {@link CelestialDiscoveryScanService}, not here.
 */
public final class CelestialKnowledgeService {

    private static final Map<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> FACTS_BY_TEAM = new LinkedHashMap<>();
    private static final ArrayList<CelestialDiscoveryDomain> DISCOVERY_DOMAINS = new ArrayList<>();

    static {
        resetDiscoveryDomainsForTesting();
    }

    private CelestialKnowledgeService() {}

    /**
     * Effective knowledge for a team: explicit stored fact, otherwise Registry
     * definition defaults. Unknown keys fail loudly.
     */
    @Nonnull
    public static CelestialKnowledgeFacts facts(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        requireTeamId(teamId);
        requireKey(key);
        Map<CelestialObjectKey, CelestialKnowledgeFacts> teamFacts = FACTS_BY_TEAM.get(teamId);
        if (teamFacts != null) {
            CelestialKnowledgeFacts stored = teamFacts.get(key);
            if (stored != null) return stored;
        }
        return CelestialRegistry.initialKnowledge(key);
    }

    @Nonnull
    public static DiscoveryState discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        return facts(teamId, key).discoveryState();
    }

    @Nonnull
    public static CelestialResourceKnowledgeState resourceKnowledge(@Nonnull UUID teamId,
        @Nonnull CelestialObjectKey key) {
        return facts(teamId, key).resourceKnowledgeState();
    }

    public static void putFacts(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key,
        @Nonnull CelestialKnowledgeFacts facts) {
        requireTeamId(teamId);
        requireKey(key);
        if (facts == null) throw new IllegalArgumentException("facts are required");
        // Validate the key is resolvable before storing so restore/mutation cannot
        // invent knowledge for bodies the registry cannot materialize.
        CelestialRegistry.initialKnowledge(key);
        FACTS_BY_TEAM.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
            .put(key, facts);
    }

    public static void clearFacts() {
        FACTS_BY_TEAM.clear();
    }

    @Nonnull
    public static Map<CelestialObjectKey, CelestialKnowledgeFacts> snapshot(@Nonnull UUID teamId) {
        requireTeamId(teamId);
        Map<CelestialObjectKey, CelestialKnowledgeFacts> teamFacts = FACTS_BY_TEAM.get(teamId);
        if (teamFacts == null || teamFacts.isEmpty()) return Map.of();
        return Map.copyOf(teamFacts);
    }

    @Nonnull
    public static Map<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> snapshotsByTeam() {
        Map<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> snapshots = new LinkedHashMap<>();
        for (UUID teamId : FACTS_BY_TEAM.keySet()) {
            Map<CelestialObjectKey, CelestialKnowledgeFacts> teamSnapshot = snapshot(teamId);
            if (!teamSnapshot.isEmpty()) snapshots.put(teamId, teamSnapshot);
        }
        return Map.copyOf(snapshots);
    }

    public static void restore(@Nonnull UUID teamId,
        @Nonnull Map<CelestialObjectKey, CelestialKnowledgeFacts> factsByKey) {
        requireTeamId(teamId);
        if (factsByKey == null) throw new IllegalArgumentException("facts map is required");
        if (factsByKey.isEmpty()) {
            FACTS_BY_TEAM.remove(teamId);
            return;
        }
        Map<CelestialObjectKey, CelestialKnowledgeFacts> restored = new LinkedHashMap<>();
        for (Map.Entry<CelestialObjectKey, CelestialKnowledgeFacts> entry : factsByKey.entrySet()) {
            CelestialObjectKey key = entry.getKey();
            CelestialKnowledgeFacts facts = entry.getValue();
            if (key == null) throw new IllegalArgumentException("facts key cannot be null");
            if (facts == null) throw new IllegalArgumentException("facts value cannot be null");
            if (restored.containsKey(key)) {
                throw new IllegalStateException("Duplicate celestial knowledge fact for " + key);
            }
            CelestialRegistry.initialKnowledge(key);
            restored.put(key, facts);
        }
        FACTS_BY_TEAM.put(teamId, restored);
    }

    public static void restoreAll(@Nonnull Map<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> factsByTeam) {
        if (factsByTeam == null) throw new IllegalArgumentException("facts by team is required");
        FACTS_BY_TEAM.clear();
        for (Map.Entry<UUID, Map<CelestialObjectKey, CelestialKnowledgeFacts>> entry : factsByTeam.entrySet()) {
            restore(entry.getKey(), entry.getValue());
        }
    }

    public static void registerDiscoveryDomain(@Nonnull CelestialDiscoveryDomain domain) {
        if (domain == null) throw new IllegalArgumentException("discovery domain is required");
        if (!DISCOVERY_DOMAINS.contains(domain)) {
            DISCOVERY_DOMAINS.add(domain);
        }
    }

    public static Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope) {
        requireTeamId(teamId);
        return discoveryDomain(scope).nextDiscoveryWork(teamId, scope);
    }

    public static void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryWork work) {
        requireTeamId(teamId);
        if (work == null) throw new IllegalArgumentException("discovery work is required");
        discoveryDomain(scope).completeDiscoveryWork(teamId, scope, work);
    }

    /** Clears discovery domain registrations without touching team facts. */
    public static void clearDiscoveryDomains() {
        DISCOVERY_DOMAINS.clear();
    }

    /** Test-only alias for {@link #clearDiscoveryDomains()}. */
    public static void resetDiscoveryDomainsForTesting() {
        clearDiscoveryDomains();
    }

    /** @deprecated use {@link #clearFacts()} and {@link #clearDiscoveryDomains()} */
    @Deprecated
    public static void resetProvidersForTesting() {
        clearFacts();
        clearDiscoveryDomains();
    }

    public static CelestialDiscoveryDomain discoveryDomain(CelestialDiscoveryScanScope scope) {
        if (scope == null) throw new IllegalArgumentException("discovery scope is required");
        List<CelestialDiscoveryDomain> owners = DISCOVERY_DOMAINS.stream()
            .filter(domain -> domain.ownsDiscoveryScope(scope))
            .toList();
        if (owners.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one discovery domain for " + scope + ", found " + owners.size());
        }
        return owners.get(0);
    }

    public static OptionalLong discoveryScopeRevision(CelestialObjectKey anchorKey) {
        List<CelestialDiscoveryDomain> owners = DISCOVERY_DOMAINS.stream()
            .filter(domain -> domain.ownsDiscoveryAnchor(anchorKey))
            .toList();
        if (owners.isEmpty()) return OptionalLong.empty();
        if (owners.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one discovery domain for anchor " + anchorKey + ", found " + owners.size());
        }
        return owners.get(0)
            .discoveryScopeRevision(anchorKey);
    }

    private static void requireTeamId(UUID teamId) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
    }

    private static void requireKey(CelestialObjectKey key) {
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
    }
}
