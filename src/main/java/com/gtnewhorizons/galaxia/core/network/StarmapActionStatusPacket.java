package com.gtnewhorizons.galaxia.core.network;

import com.gtnewhorizons.galaxia.client.gui.station.StationNotificationHelper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class StarmapActionStatusPacket implements IMessage {

    private String message = "";

    public StarmapActionStatusPacket() {}

    public static StarmapActionStatusPacket rejected(String message) {
        StarmapActionStatusPacket packet = new StarmapActionStatusPacket();
        packet.message = message == null ? "" : message;
        return packet;
    }

    public String message() {
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        message = PacketUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeString(buf, message);
    }

    public static final class Handler implements IMessageHandler<StarmapActionStatusPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(StarmapActionStatusPacket packet, MessageContext ctx) {
            StationNotificationHelper.showFailure(packet.message);
            return null;
        }
    }
}
