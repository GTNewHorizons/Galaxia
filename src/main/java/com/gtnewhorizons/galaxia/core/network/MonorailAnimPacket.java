package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailAnimationState;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class MonorailAnimPacket implements IMessage {

    public static final byte DIR_TO_SILO = 0;
    public static final byte DIR_TO_MA = 1;

    private int siloX, siloY, siloZ;
    private int moduleId;
    private byte direction;

    public MonorailAnimPacket() {}

    public MonorailAnimPacket(int siloX, int siloY, int siloZ, int moduleId, byte direction) {
        this.siloX = siloX;
        this.siloY = siloY;
        this.siloZ = siloZ;
        this.moduleId = moduleId;
        this.direction = direction;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(siloX);
        buf.writeInt(siloY);
        buf.writeInt(siloZ);
        buf.writeInt(moduleId);
        buf.writeByte(direction);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        siloX = buf.readInt();
        siloY = buf.readInt();
        siloZ = buf.readInt();
        moduleId = buf.readInt();
        direction = buf.readByte();
    }

    public static class Handler implements IMessageHandler<MonorailAnimPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(MonorailAnimPacket msg, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> handleOnMainThread(msg));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleOnMainThread(MonorailAnimPacket msg) {
            net.minecraft.world.World world = Minecraft.getMinecraft().theWorld;
            if (world == null) return;

            TileEntity te = world.getTileEntity(msg.siloX, msg.siloY, msg.siloZ);
            if (!(te instanceof TileEntitySilo silo)) return;

            MonorailAnimationState anim = silo.getAnimationState();
            if (msg.direction == DIR_TO_SILO) {
                anim.enqueueToSilo(msg.moduleId);
            } else {
                anim.enqueueToMA(msg.moduleId);
            }
        }
    }
}
