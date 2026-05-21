package com.gtnewhorizons.galaxia.core.network;

import com.gtnewhorizons.galaxia.registry.items.tether.KineticTetherState;
import com.gtnewhorizons.galaxia.registry.items.tether.TetherData;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class TetherAnchorSyncPacket implements IMessage {

    private boolean active;
    private double x, y, z;
    private int dim;

    public TetherAnchorSyncPacket() {}

    public TetherAnchorSyncPacket(TetherData data) {
        this.active = data.tetherActive;
        this.x = data.anchorX;
        this.y = data.anchorY;
        this.z = data.anchorZ;
        this.dim = data.anchorDim;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        active = buf.readBoolean();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        dim = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(dim);
    }

    public static class Handler implements IMessageHandler<TetherAnchorSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(TetherAnchorSyncPacket message, MessageContext ctx) {
            TetherData clientData = KineticTetherState.getClient();
            clientData.tetherActive = message.active;
            clientData.anchorX = message.x;
            clientData.anchorY = message.y;
            clientData.anchorZ = message.z;
            clientData.anchorDim = message.dim;
            return null;
        }
    }
}
