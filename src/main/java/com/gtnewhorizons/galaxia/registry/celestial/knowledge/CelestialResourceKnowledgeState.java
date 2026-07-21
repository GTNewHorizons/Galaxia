package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

public enum CelestialResourceKnowledgeState {

    UNKNOWN,
    PROFILE;

    public CelestialResourceKnowledgeState advance() {
        return PROFILE;
    }
}
