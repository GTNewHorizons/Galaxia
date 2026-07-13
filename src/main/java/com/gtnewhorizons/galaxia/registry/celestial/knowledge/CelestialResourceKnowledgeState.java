package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

public enum CelestialResourceKnowledgeState {

    UNKNOWN,
    SIGNATURE,
    PROFILE;

    public CelestialResourceKnowledgeState advance() {
        return switch (this) {
            case UNKNOWN -> SIGNATURE;
            case SIGNATURE, PROFILE -> PROFILE;
        };
    }
}
