package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

/**
 * Server-side owner for team knowledge about celestial objects.
 *
 * Object-specific providers own their storage and rules; callers use this
 * service for shared discovery-state reads and domain routing.
 */
public final class CelestialKnowledgeService {

    private static final CelestialKnowledgeProvider REGISTERED_BODIES = CelestialKnowledgeService::registeredBodyState;
    private static final ArrayList<CelestialKnowledgeProvider> KNOWLEDGE_PROVIDERS = new ArrayList<>();
    private static final ArrayList<CelestialDiscoveryDomain> DISCOVERY_DOMAINS = new ArrayList<>();

    static {
        resetProvidersForTesting();
    }

    private CelestialKnowledgeService() {}

    public static void registerProvider(@Nonnull CelestialKnowledgeProvider provider) {
        if (provider == null) throw new IllegalArgumentException("knowledge provider is required");
        if (!KNOWLEDGE_PROVIDERS.contains(provider)) {
            KNOWLEDGE_PROVIDERS.add(KNOWLEDGE_PROVIDERS.size() - 1, provider);
        }
    }

    public static DiscoveryState discoveryState(@Nonnull UUID teamId, @Nonnull CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        return KNOWLEDGE_PROVIDERS.stream()
            .map(provider -> provider.discoveryState(teamId, key))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No discovery provider for celestial object: " + key));
    }

    public static void registerDiscoveryDomain(@Nonnull CelestialDiscoveryDomain domain) {
        if (domain == null) throw new IllegalArgumentException("discovery domain is required");
        if (!DISCOVERY_DOMAINS.contains(domain)) {
            DISCOVERY_DOMAINS.add(domain);
        }
    }

    public static Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        return discoveryDomain(scope).nextDiscoveryWork(teamId, scope);
    }

    public static void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryWork work) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (work == null) throw new IllegalArgumentException("discovery work is required");
        discoveryDomain(scope).completeDiscoveryWork(teamId, scope, work);
    }

    static void resetProvidersForTesting() {
        KNOWLEDGE_PROVIDERS.clear();
        KNOWLEDGE_PROVIDERS.add(REGISTERED_BODIES);
        DISCOVERY_DOMAINS.clear();
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

    private static Optional<DiscoveryState> registeredBodyState(UUID teamId, CelestialObjectKey key) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (key == null) throw new IllegalArgumentException("celestial object key is required");
        if (!key.isRegistered()) return Optional.empty();
        if (CelestialRegistry.get(key)
            .isEmpty()) {
            throw new IllegalStateException("Unknown celestial object: " + key);
        }
        return Optional.of(DiscoveryState.DISCOVERED);
    }
}
