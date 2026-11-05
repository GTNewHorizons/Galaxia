package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/**
 * Discovery domains are registered once during mod init, while {@code reset()} runs on every world load and unload.
 * Clearing domains on reset silently disables prospecting for the whole process.
 * <p>
 * Scan anchors are minor bodies (individual asteroids), not the belt container, so the ownership probe has to use an
 * asteroid key.
 */
final class CelestialServerRuntimeLifecycleTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void setUp() {
        CelestialKnowledgeService.clearFacts();
        CelestialKnowledgeService.resetDiscoveryDomainsForTesting();
    }

    @Test
    void discoveryStaysRegisteredAcrossWorldReloads() {
        CelestialObjectKey anchor = firstAsteroidAnchor();
        CelestialServerRuntime runtime = CelestialServerRuntime.create();

        assertTrue(
            CelestialKnowledgeService.discoveryScopeRevision(anchor)
                .isPresent(),
            "asteroid anchor must own a discovery domain right after create()");

        runtime.reset();
        runtime.reset();

        assertTrue(
            CelestialKnowledgeService.discoveryScopeRevision(anchor)
                .isPresent(),
            "anchor must still own a discovery domain after world reloads, otherwise prospecting satellites are skipped");
    }

    private static CelestialObjectKey firstAsteroidAnchor() {
        AsteroidFieldProfile profile = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        return CelestialObjectKey.minorBody(
            AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
                .get(0)
                .id());
    }
}
