package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.client.AndroidClient;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.ConnectionStatus;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Client-visible control state. Actor identity is supplied only by the server. */
public final class AndroidControlPacket implements IMessage {

    private int actorEntityId;
    private boolean active;
    private long battery;
    private long capacity;
    private int countdown;
    private boolean portalWarning;
    private boolean explosion;

    public AndroidControlPacket() {}

    public AndroidControlPacket(int actorEntityId, boolean active, long battery, long capacity, int countdown,
        boolean portalWarning, boolean explosion) {
        this.actorEntityId = actorEntityId;
        this.active = active;
        this.battery = battery;
        this.capacity = capacity;
        this.countdown = countdown;
        this.portalWarning = portalWarning;
        this.explosion = explosion;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        actorEntityId = buffer.readInt();
        active = buffer.readBoolean();
        battery = buffer.readLong();
        capacity = buffer.readLong();
        countdown = buffer.readInt();
        portalWarning = buffer.readBoolean();
        explosion = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(actorEntityId);
        buffer.writeBoolean(active);
        buffer.writeLong(battery);
        buffer.writeLong(capacity);
        buffer.writeInt(countdown);
        buffer.writeBoolean(portalWarning);
        buffer.writeBoolean(explosion);
    }

    public static final class Handler implements IMessageHandler<AndroidControlPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AndroidControlPacket message, MessageContext context) {
            Minecraft.getMinecraft()
                .func_152344_a(
                    () -> AndroidClient.status(
                        message.actorEntityId,
                        message.active,
                        message.battery,
                        message.capacity,
                        message.countdown,
                        message.portalWarning,
                        message.explosion));
            return null;
        }
    }

    /** An actual injury to the capsule occupant, delivered only to its remote operator. */
    public static final class BiologicalDamage implements IMessage {

        private int actorEntityId;

        public BiologicalDamage() {}

        public BiologicalDamage(int actorEntityId) {
            this.actorEntityId = actorEntityId;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            actorEntityId = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(actorEntityId);
        }

        public static final class Handler implements IMessageHandler<BiologicalDamage, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(BiologicalDamage message, MessageContext context) {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> AndroidClient.biologicalDamage(message.actorEntityId));
                return null;
            }
        }
    }

    /** Physical station status delivered only to its occupant. */
    public static final class StationStatus implements IMessage {

        private ConnectionStatus status;

        public StationStatus() {}

        public StationStatus(ConnectionStatus status) {
            this.status = status;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            status = ConnectionStatus.values()[buffer.readUnsignedByte()];
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeByte(status.ordinal());
        }

        public static final class Handler implements IMessageHandler<StationStatus, IMessage> {

            @Override
            @SideOnly(Side.CLIENT)
            public IMessage onMessage(StationStatus message, MessageContext context) {
                Minecraft.getMinecraft()
                    .func_152344_a(() -> AndroidClient.stationConnection(message.status));
                return null;
            }
        }
    }

    /** The server resolves the sender's current session without accepting a client-selected body. */
    public static final class EndRequest implements IMessage {

        @Override
        public void fromBytes(ByteBuf buffer) {}

        @Override
        public void toBytes(ByteBuf buffer) {}

        public static final class Handler implements IMessageHandler<EndRequest, IMessage> {

            @Override
            public IMessage onMessage(EndRequest message, MessageContext context) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ServerTickTaskQueue.schedule(() -> AndroidControl.endControl(player));
                return null;
            }
        }
    }
}
