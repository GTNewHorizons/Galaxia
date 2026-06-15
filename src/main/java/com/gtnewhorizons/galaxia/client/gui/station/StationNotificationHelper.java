package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class StationNotificationHelper {

    private static final String PREFIX = "[Galaxia] ";

    private StationNotificationHelper() {}

    public static void showFailure(String message) {
        showMessage(message);
    }

    public static void showMessage(String message) {
        if (message == null || message.isBlank()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) return;
        minecraft.ingameGUI.getChatGUI()
            .printChatMessage(new ChatComponentText(PREFIX + message));
    }
}
