package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import com.gtnewhorizons.galaxia.core.network.AssetCreateRequestPacket;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetStateFramePacket;
import com.gtnewhorizons.galaxia.core.network.AssetStateResyncRequestPacket;
import com.gtnewhorizons.galaxia.core.network.AssetUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.BeamEffectPacket;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncPacket;
import com.gtnewhorizons.galaxia.core.network.CommitBlueprintAndOrderPacket;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.core.network.FacilityCommandPacket;
import com.gtnewhorizons.galaxia.core.network.HazardWarningPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.OxygenSyncPacket;
import com.gtnewhorizons.galaxia.core.network.ProfilerSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketDestinationSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketLaunchPacket;
import com.gtnewhorizons.galaxia.core.network.SatelliteNetworkSyncPacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionStatusPacket;
import com.gtnewhorizons.galaxia.core.network.TeamConfigPacket;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.core.network.TetherAnchorSyncPacket;
import com.gtnewhorizons.galaxia.core.network.TetherPacket;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;

import cpw.mods.fml.relauncher.Side;

public final class NetworkManager {

    // spotless:off
    public static void registerPackets() {
        int id = 0;

        GALAXIA_NETWORK.registerMessage(TetherPacket.Handler.class, TetherPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(TeleportRequestPacket.Handler.class, TeleportRequestPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(DestinationSetPacket.Handler.class, DestinationSetPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(ToggleRCSPacket.Handler.class, ToggleRCSPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(RocketLaunchPacket.class, RocketLaunchPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(AssetUpdatePacket.Handler.class, AssetUpdatePacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(AssetCreateRequestPacket.Handler.class, AssetCreateRequestPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(AssetInventoryUpdatePacket.Handler.class, AssetInventoryUpdatePacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(LogisticsConfigUpdatePacket.Handler.class, LogisticsConfigUpdatePacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(RocketDestinationSyncPacket.Handler.class, RocketDestinationSyncPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(TeamConfigPacket.Handler.class, TeamConfigPacket.class, id++,
            Side.SERVER);
        GALAXIA_NETWORK.registerMessage(CommitBlueprintAndOrderPacket.Handler.class, CommitBlueprintAndOrderPacket.class, id++,
                Side.SERVER);
        GALAXIA_NETWORK.registerMessage(AssetStateResyncRequestPacket.Handler.class,
            AssetStateResyncRequestPacket.class, id++, Side.SERVER);
        GALAXIA_NETWORK.registerMessage(FacilityCommandPacket.Handler.class, FacilityCommandPacket.class, id++,
            Side.SERVER);

        GALAXIA_NETWORK.registerMessage(OxygenSyncPacket.Handler.class, OxygenSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(HazardWarningPacket.Handler.class, HazardWarningPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(AssetStateFramePacket.Handler.class, AssetStateFramePacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(LogisticsSyncPacket.Handler.class, LogisticsSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(ProfilerSyncPacket.Handler.class, ProfilerSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(BeamEffectPacket.Handler.class, BeamEffectPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(TetherAnchorSyncPacket.Handler.class, TetherAnchorSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(SatelliteNetworkSyncPacket.Handler.class, SatelliteNetworkSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(CelestialKnowledgeSyncPacket.Handler.class, CelestialKnowledgeSyncPacket.class, id++,
            Side.CLIENT);
        GALAXIA_NETWORK.registerMessage(StarmapActionStatusPacket.Handler.class, StarmapActionStatusPacket.class, id++,
            Side.CLIENT);
    }
    // spotless:on
}
