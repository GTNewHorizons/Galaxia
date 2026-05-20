package com.gtnewhorizons.galaxia.core;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket;
import com.gtnewhorizons.galaxia.core.network.AssetCreateRequestPacket;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;
import com.gtnewhorizons.galaxia.core.network.AssetUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.BeamEffectPacket;
import com.gtnewhorizons.galaxia.core.network.CommitBlueprintAndOrderPacket;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.core.network.HazardWarningPacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.core.network.OxygenSyncPacket;
import com.gtnewhorizons.galaxia.core.network.ProfilerSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketDestinationSyncPacket;
import com.gtnewhorizons.galaxia.core.network.RocketLaunchPacket;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.core.network.ToggleRCSPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.relauncher.Side;

public final class NetworkManager {

    public static void registerPackets() {
        registerPackets(GALAXIA_NETWORK::registerMessage);
    }

    static void registerPackets(PacketRegistrar registrar) {
        int id = 0;
        id = registerServerPackets(registrar, id);
        registerClientPackets(registrar, id);
    }

    @FunctionalInterface
    interface PacketRegistrar {

        <REQ extends IMessage, REPLY extends IMessage> void registerMessage(
            Class<? extends IMessageHandler<REQ, REPLY>> handler, Class<REQ> packet, int discriminator, Side side);
    }

    // spotless:off
    private static int registerServerPackets(PacketRegistrar registrar, int id) {
        registrar.registerMessage(TeleportRequestPacket.Handler.class, TeleportRequestPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(DestinationSetPacket.Handler.class, DestinationSetPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(ToggleRCSPacket.Handler.class, ToggleRCSPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(RocketLaunchPacket.class, RocketLaunchPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(AssetUpdatePacket.Handler.class, AssetUpdatePacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(AssetBuildModulePacket.Handler.class, AssetBuildModulePacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(AssetCreateRequestPacket.Handler.class, AssetCreateRequestPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(AssetModuleUpdatePacket.Handler.class, AssetModuleUpdatePacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(AssetInventoryUpdatePacket.Handler.class, AssetInventoryUpdatePacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(LogisticsConfigUpdatePacket.Handler.class, LogisticsConfigUpdatePacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(RocketDestinationSyncPacket.Handler.class, RocketDestinationSyncPacket.class, id++,
            Side.SERVER);
        registrar.registerMessage(CommitBlueprintAndOrderPacket.Handler.class, CommitBlueprintAndOrderPacket.class, id++,
                Side.SERVER);
        return id;
    }

    private static int registerClientPackets(PacketRegistrar registrar, int id) {
        registrar.registerMessage(OxygenSyncPacket.Handler.class, OxygenSyncPacket.class, id++,
            Side.CLIENT);
        registrar.registerMessage(HazardWarningPacket.Handler.class, HazardWarningPacket.class, id++,
            Side.CLIENT);
        registrar.registerMessage(AssetSyncPacket.Handler.class, AssetSyncPacket.class, id++,
            Side.CLIENT);
        registrar.registerMessage(LogisticsSyncPacket.Handler.class, LogisticsSyncPacket.class, id++,
            Side.CLIENT);
        registrar.registerMessage(ProfilerSyncPacket.Handler.class, ProfilerSyncPacket.class, id++,
            Side.CLIENT);
        registrar.registerMessage(BeamEffectPacket.Handler.class, BeamEffectPacket.class, id++,
            Side.CLIENT);
        return id;
    }
    // spotless:on
}
