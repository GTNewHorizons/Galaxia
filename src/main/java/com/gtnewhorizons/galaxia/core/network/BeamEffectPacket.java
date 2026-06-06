package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class BeamEffectPacket implements IMessage {

    public int machineX, machineY, machineZ;
    public int playerX100, playerY100, playerZ100;

    public BeamEffectPacket() {}

    public BeamEffectPacket(int machineX, int machineY, int machineZ, double playerX, double playerY, double playerZ) {
        this.machineX = machineX;
        this.machineY = machineY;
        this.machineZ = machineZ;
        this.playerX100 = (int) (playerX * 100);
        this.playerY100 = (int) (playerY * 100);
        this.playerZ100 = (int) (playerZ * 100);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        machineX = buf.readInt();
        machineY = buf.readInt();
        machineZ = buf.readInt();
        playerX100 = buf.readInt();
        playerY100 = buf.readInt();
        playerZ100 = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(machineX);
        buf.writeInt(machineY);
        buf.writeInt(machineZ);
        buf.writeInt(playerX100);
        buf.writeInt(playerY100);
        buf.writeInt(playerZ100);
    }

    public static class Handler implements IMessageHandler<BeamEffectPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final BeamEffectPacket msg, MessageContext ctx) {
            spawnBeamParticles(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
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
}
