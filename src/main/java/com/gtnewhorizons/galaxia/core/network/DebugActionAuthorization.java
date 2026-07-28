package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

final class DebugActionAuthorization {

    private DebugActionAuthorization() {}

    static boolean isAuthorized(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return player != null && player.capabilities.isCreativeMode
            && server != null
            && server.getConfigurationManager()
                .func_152596_g(player.getGameProfile());
    }
}
