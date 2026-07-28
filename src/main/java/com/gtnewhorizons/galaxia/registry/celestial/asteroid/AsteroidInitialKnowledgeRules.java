package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.util.DeterministicHash;

final class AsteroidInitialKnowledgeRules {

    private AsteroidInitialKnowledgeRules() {}

    static DiscoveryState initialDetectionState(AsteroidFieldNode node) {
        return node.initialDetectionState();
    }

    static CelestialResourceKnowledgeState initialOreKnowledge(AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        if (node.sizeClass() != AsteroidSizeClass.LARGE) return CelestialResourceKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 5L);
    }

    static CelestialResourceKnowledgeState oreKnowledgeAfterDetection(AsteroidFieldNode node) {
        if (node.sizeClass() == AsteroidSizeClass.SMALL) return CelestialResourceKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 6L);
    }

    static DiscoveryState defaultInitialDetectionState(AsteroidSizeClass sizeClass) {
        return sizeClass == AsteroidSizeClass.LARGE ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN;
    }

    private static CelestialResourceKnowledgeState rolledOreKnowledge(AsteroidFieldNode node, long salt) {
        double roll = DeterministicHash.unitDouble(
            DeterministicHash.mix(
                node.appearance()
                    .variantSeed(),
                salt));
        if (roll < 0.20) return CelestialResourceKnowledgeState.PROFILE;
        if (roll < 0.55) return CelestialResourceKnowledgeState.SIGNATURE;
        return CelestialResourceKnowledgeState.UNKNOWN;
    }
}
