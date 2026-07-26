package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/**
 * Discovery domains are registered once during mod init, while {@code reset()} runs on every world load and unload.
 * Clearing domains on reset silently disables prospecting for the whole process.
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
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        CelestialServerRuntime runtime = CelestialServerRuntime.create();

        assertTrue(
            CelestialKnowledgeService.discoveryScopeRevision(belt)
                .isPresent(),
            "belt must own a discovery domain right after create()");

        runtime.reset();
        runtime.reset();

        OptionalLong afterReload = CelestialKnowledgeService.discoveryScopeRevision(belt);
        assertTrue(
            afterReload.isPresent(),
            "belt must still own a discovery domain after world reloads, otherwise prospecting satellites are skipped");
    }
}
