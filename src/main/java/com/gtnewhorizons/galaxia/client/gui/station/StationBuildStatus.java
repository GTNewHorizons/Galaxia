package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

final class StationBuildStatus {

    private StationBuildStatus() {}

    static void notifyFailure(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) return;
        minecraft.ingameGUI.getChatGUI()
            .printChatMessage(new ChatComponentText("[Galaxia] " + message));
    }
}
