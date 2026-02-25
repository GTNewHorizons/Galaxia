package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.block.tileentities.TileSiloController;
import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleData;
import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleType;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAddModule implements IMessage {

    private int x, y, z;
    private byte moduleOrdinal;

    public PacketAddModule() {}

    public PacketAddModule(int x, int y, int z, ModuleType type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.moduleOrdinal = (byte) type.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        moduleOrdinal = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(moduleOrdinal);
    }

    public static class Handler implements IMessageHandler<PacketAddModule, IMessage> {

        @Override
        public IMessage onMessage(PacketAddModule msg, MessageContext ctx) {
            System.out.println(
                "[PacketAddModule SERVER] packet accepted, coords=" + msg.x
                    + ","
                    + msg.y
                    + ","
                    + msg.z
                    + " typeOrdinal="
                    + msg.moduleOrdinal);

            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof TileSiloController tile) {
                ModuleType type = ModuleType.values()[msg.moduleOrdinal];
                tile.addModule(new ModuleData(type));
                if (!ctx.side.isClient()) {
                    S35PacketUpdateTileEntity pkt = (S35PacketUpdateTileEntity) tile.getDescriptionPacket();
                    ctx.getServerHandler().playerEntity.playerNetServerHandler.sendPacket(pkt);
                }
            }
            return null;
        }
    }
}
