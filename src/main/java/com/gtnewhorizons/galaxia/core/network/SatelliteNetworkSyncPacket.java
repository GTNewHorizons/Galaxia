package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class SatelliteNetworkSyncPacket implements IMessage {

    private SatelliteNetworkState state;

    public SatelliteNetworkSyncPacket() {}

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state) {
        this.state = state;
    }

    public SatelliteNetworkState state() {
        return state;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, state.teamId());
        buf.writeInt(state.revision());
        buf.writeInt(
            state.bodies()
                .size());
        for (SatelliteNetworkState.Body body : state.bodies()
            .values()) {
            PacketUtil.writeEnum(buf, body.bodyId());
            buf.writeLong(body.capacityKbps());
            buf.writeLong(body.usedKbps());
        }
        buf.writeInt(
            state.links()
                .size());
        for (SatelliteNetworkState.Link link : state.links()) {
            PacketUtil.writeEnum(buf, link.from());
            PacketUtil.writeEnum(buf, link.to());
            buf.writeLong(link.capacityKbps());
            buf.writeLong(link.usedKbps());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        UUID teamId = PacketUtil.readId(buf);
        int revision = buf.readInt();
        int bodyCount = buf.readInt();
        Map<CelestialObjectId, SatelliteNetworkState.Body> bodies = new EnumMap<>(CelestialObjectId.class);
        for (int i = 0; i < bodyCount; i++) {
            CelestialObjectId bodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            long capacityKbps = buf.readLong();
            long usedKbps = buf.readLong();
            bodies.put(bodyId, new SatelliteNetworkState.Body(bodyId, capacityKbps, usedKbps));
        }
        int linkCount = buf.readInt();
        List<SatelliteNetworkState.Link> links = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) {
            CelestialObjectId from = PacketUtil.readEnum(buf, CelestialObjectId.class);
            CelestialObjectId to = PacketUtil.readEnum(buf, CelestialObjectId.class);
            long capacityKbps = buf.readLong();
            long usedKbps = buf.readLong();
            links.add(new SatelliteNetworkState.Link(from, to, capacityKbps, usedKbps));
        }
        state = new SatelliteNetworkState(teamId, revision, bodies, links);
    }

    public static final class Handler implements IMessageHandler<SatelliteNetworkSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteNetworkSyncPacket message, MessageContext ctx) {
            SatelliteNetworkClientState.update(message.state);
            return null;
        }
    }
}
