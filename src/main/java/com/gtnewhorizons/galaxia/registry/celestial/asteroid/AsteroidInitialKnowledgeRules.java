package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidInitialKnowledgeRules {

    private AsteroidInitialKnowledgeRules() {}

    static DiscoveryState initialDetectionState(AsteroidFieldNode node) {
        return node.initialDetectionState();
    }

    static AsteroidOreKnowledgeState initialOreKnowledge(AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        if (node.sizeClass() != AsteroidSizeClass.LARGE) return AsteroidOreKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 5L);
    }

    static AsteroidOreKnowledgeState oreKnowledgeAfterDetection(AsteroidFieldNode node) {
        if (node.sizeClass() == AsteroidSizeClass.SMALL) return AsteroidOreKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 6L);
    }

    static DiscoveryState defaultInitialDetectionState(AsteroidSizeClass sizeClass) {
        return sizeClass == AsteroidSizeClass.LARGE ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN;
    }

    private static AsteroidOreKnowledgeState rolledOreKnowledge(AsteroidFieldNode node, long salt) {
        double roll = AsteroidFieldDeterminism.unitDouble(
            AsteroidFieldDeterminism.mix(
                node.appearance()
                    .variantSeed(),
                salt));
        if (roll < 0.20) return AsteroidOreKnowledgeState.PROFILE;
        if (roll < 0.55) return AsteroidOreKnowledgeState.SIGNATURE;
        return AsteroidOreKnowledgeState.UNKNOWN;
    }
}
