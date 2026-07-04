package com.gtnewhorizons.galaxia.core.network;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;

public final class ClientStateLifecycle {

    private ClientStateLifecycle() {}

    public static void clearAll() {
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientKnowledgeState.clear();
        AsteroidScanClientState.clear();
        SatelliteNetworkClientState.clear();
    }
}
