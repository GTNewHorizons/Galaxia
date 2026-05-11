package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BeamEffectPacketClientHandler implements IMessageHandler<BeamEffectPacket, IMessage> {

    @Override
    public IMessage onMessage(final BeamEffectPacket msg, MessageContext ctx) {
        spawnBeamParticles(msg);
        return null;
    }

    private void spawnBeamParticles(BeamEffectPacket msg) {

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return;

        double startX = msg.machineX + 0.5;
        double startY = msg.machineY + 0.5;
        double startZ = msg.machineZ + 0.5;

        double endX = msg.playerX100 / 100.0;
        double endY = msg.playerY100 / 100.0;
        double endZ = msg.playerZ100 / 100.0;

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;

        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.1) return;

        int steps = Math.max(1, (int) (dist * 2));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;

            double px = startX + dx * t;
            double py = startY + dy * t;
            double pz = startZ + dz * t;

            world.spawnParticle("reddust", px, py, pz, 0.0001, 0.4, 1.0);
        }
    }
}
