package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import javax.annotation.Nonnull;

/**
 * Immutable team knowledge about one celestial object.
 * <p>
 * TLDR: shared discovery + resource tier value used by server owner,
 * persistence, wire, and client read model — one facts object per Key.
 */
public record CelestialKnowledgeFacts(@Nonnull DiscoveryState discoveryState,
    @Nonnull CelestialResourceKnowledgeState resourceKnowledgeState) {

    /**
     * Player-facing visibility state for any celestial object whose existence can
     * be learned after world start.
     */
    public enum DiscoveryState {
        HIDDEN,
        DISCOVERED
    }

    public enum CelestialResourceKnowledgeState {

        UNKNOWN,
        PROFILE;

        public CelestialResourceKnowledgeState advance() {
            return PROFILE;
        }
    }

    public CelestialKnowledgeFacts {
        if (discoveryState == null) throw new IllegalArgumentException("discovery state is required");
        if (resourceKnowledgeState == null) throw new IllegalArgumentException("resource knowledge state is required");
        if (discoveryState == DiscoveryState.HIDDEN
            && resourceKnowledgeState != CelestialResourceKnowledgeState.UNKNOWN) {
            throw new IllegalArgumentException("HIDDEN facts cannot expose resource knowledge other than UNKNOWN");
        }
    }

    public static CelestialKnowledgeFacts discoveredUnknown() {
        return new CelestialKnowledgeFacts(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.UNKNOWN);
    }

    public static CelestialKnowledgeFacts hidden() {
        return new CelestialKnowledgeFacts(DiscoveryState.HIDDEN, CelestialResourceKnowledgeState.UNKNOWN);
    }

    public static CelestialKnowledgeFacts of(@Nonnull DiscoveryState discoveryState,
        @Nonnull CelestialResourceKnowledgeState resourceKnowledgeState) {
        return new CelestialKnowledgeFacts(discoveryState, resourceKnowledgeState);
    }
}
