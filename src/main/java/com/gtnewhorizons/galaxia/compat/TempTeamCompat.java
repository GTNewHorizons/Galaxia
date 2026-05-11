package com.gtnewhorizons.galaxia.compat;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TempTeamCompat {

    @SideOnly(Side.CLIENT)
    public static UUID getTeam() {
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        return player != null ? player.getUniqueID() : new UUID(0L, 0L);
    }

    public static UUID getTeam(EntityPlayer player) {
        return player != null ? player.getUniqueID() : new UUID(0L, 0L);
    }
}
