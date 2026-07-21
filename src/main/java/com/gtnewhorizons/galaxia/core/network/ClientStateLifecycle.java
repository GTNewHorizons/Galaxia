package com.gtnewhorizons.galaxia.core.network;

import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientCatalogState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ClientStateLifecycle {

    private ClientStateLifecycle() {}

    @SideOnly(Side.CLIENT)
    public static void clearAll() {
        CelestialClient.clearLocalState();
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
        CelestialDiscoveryClientState.clear();
        SatelliteNetworkClientState.clear();
    }

    @SideOnly(Side.CLIENT)
    public static final class EventHandler {

        @SubscribeEvent
        public void onClientWorldLoad(WorldEvent.Load event) {
            if (event.world.isRemote) {
                clearAll();
            }
        }
    }
}
