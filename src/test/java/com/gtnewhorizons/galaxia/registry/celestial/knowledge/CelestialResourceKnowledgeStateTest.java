package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CelestialResourceKnowledgeStateTest {

    @Test
    void advancesFromUnknownToSignatureToProfile() {
        assertEquals(CelestialResourceKnowledgeState.SIGNATURE, CelestialResourceKnowledgeState.UNKNOWN.advance());
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialResourceKnowledgeState.SIGNATURE.advance());
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialResourceKnowledgeState.PROFILE.advance());
    }
}
