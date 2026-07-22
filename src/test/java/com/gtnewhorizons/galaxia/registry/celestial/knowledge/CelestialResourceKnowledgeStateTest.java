package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;

final class CelestialResourceKnowledgeStateTest {

    @Test
    void advancesFromUnknownDirectlyToProfile() {
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialResourceKnowledgeState.UNKNOWN.advance());
        assertEquals(CelestialResourceKnowledgeState.PROFILE, CelestialResourceKnowledgeState.PROFILE.advance());
    }
}
