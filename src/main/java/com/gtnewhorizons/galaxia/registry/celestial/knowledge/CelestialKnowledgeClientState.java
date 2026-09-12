package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/**
 * Client-side read model for team knowledge synced from the server.
 * <p>
 * TLDR: one atomic {@code Key → CelestialKnowledgeFacts} snapshot, not a
 * {@code Class<?> source} aggregation. {@link #discoveryView()} exposes only
 * synced facts so A1 {@code CelestialRegistry.children(view)} stays the sole child
 * owner; effective reads fall back to {@link CelestialRegistry#initialKnowledge}
 * so permission and resource lookups agree with the server.
 */
public final class CelestialKnowledgeClientState {

    private static Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = Map.of();
    private static int revision;

    private CelestialKnowledgeClientState() {}

    /** Bumped whenever the synced facts change, so readers can cache derived views. */
    public static int revision() {
        return revision;
    }

    public interface CelestialDiscoveryView {

        Optional<DiscoveryState> discoveryState(@Nonnull CelestialObjectKey key);

        /**
         * Whether {@code key} is visible for map/GUI child lists.
         * Uses synced discovery when present; otherwise falls back to {@code initialState}.
         * <p>
         * Client adapters may override this for temporary scan/sensor ghosts without
         * falsifying {@link #discoveryState(CelestialObjectKey)}.
         */
        default boolean isVisible(@Nonnull CelestialObjectKey key, @Nonnull DiscoveryState initialState) {
            DiscoveryState state = discoveryState(key).orElse(initialState);
            return state == DiscoveryState.DISCOVERED;
        }

        static CelestialDiscoveryView empty() {
            return key -> Optional.empty();
        }
    }

    public static void apply(Map<CelestialObjectKey, CelestialKnowledgeFacts> newFacts) {
        Map<CelestialObjectKey, CelestialKnowledgeFacts> copy = new LinkedHashMap<>();
        if (newFacts != null) {
            newFacts.forEach((key, value) -> {
                if (key == null) throw new IllegalArgumentException("knowledge key cannot be null");
                if (value == null) throw new IllegalArgumentException("knowledge facts cannot be null");
                copy.put(key, value);
            });
        }
        Map<CelestialObjectKey, CelestialKnowledgeFacts> updated = Map.copyOf(copy);
        if (facts.equals(updated)) return;
        facts = updated;
        revision++;
    }

    public static void clear() {
        if (facts.isEmpty()) return;
        facts = Map.of();
        revision++;
    }

    /** Synced-only discovery view; membership must not change from client defaults. */
    public static CelestialDiscoveryView discoveryView() {
        return CelestialKnowledgeClientState::syncedDiscoveryState;
    }

    private static Optional<DiscoveryState> syncedDiscoveryState(CelestialObjectKey key) {
        if (key == null) return Optional.empty();
        CelestialKnowledgeFacts synced = facts.get(key);
        return synced == null ? Optional.empty() : Optional.of(synced.discoveryState());
    }

    /** Effective facts: synced override, else registry definition default; empty if unresolvable. */
    public static Optional<CelestialKnowledgeFacts> facts(CelestialObjectKey key) {
        if (key == null) return Optional.empty();
        CelestialKnowledgeFacts synced = facts.get(key);
        if (synced != null) return Optional.of(synced);
        try {
            return Optional.of(CelestialRegistry.initialKnowledge(key));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static DiscoveryState effectiveDiscoveryState(CelestialObjectKey key) {
        return facts(key).map(CelestialKnowledgeFacts::discoveryState)
            .orElse(DiscoveryState.HIDDEN);
    }

    public static Optional<CelestialResourceKnowledgeState> resourceKnowledge(CelestialObjectKey key) {
        return facts(key).map(CelestialKnowledgeFacts::resourceKnowledgeState);
    }
}
