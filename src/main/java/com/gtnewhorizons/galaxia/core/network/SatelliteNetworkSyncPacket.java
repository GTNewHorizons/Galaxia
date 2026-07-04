package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
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
        writeBodies(buf);
        writeLinks(buf);
        writePendingData(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        UUID teamId = PacketUtil.readId(buf);
        int revision = buf.readInt();
        Map<CelestialObjectKey, SatelliteNetworkState.Body> bodies = readBodies(buf);
        List<SatelliteNetworkState.Link> links = readLinks(buf);
        List<SatelliteNetworkState.PendingData> pendingData = readPendingData(buf);
        state = new SatelliteNetworkState(teamId, revision, bodies, links, pendingData);
    }

    private void writeBodies(ByteBuf buf) {
        buf.writeInt(
            state.bodies()
                .size());
        for (SatelliteNetworkState.Body body : state.bodies()
            .values()) {
            PacketUtil.writeCelestialObjectKey(buf, body.bodyKey());
            buf.writeLong(body.capacityKbps());
            buf.writeLong(body.usedKbps());
        }
    }

    private static Map<CelestialObjectKey, SatelliteNetworkState.Body> readBodies(ByteBuf buf) {
        int bodyCount = buf.readInt();
        Map<CelestialObjectKey, SatelliteNetworkState.Body> bodies = new HashMap<>();
        for (int i = 0; i < bodyCount; i++) {
            CelestialObjectKey bodyKey = PacketUtil.readCelestialObjectKey(buf);
            long capacityKbps = buf.readLong();
            long usedKbps = buf.readLong();
            bodies.put(bodyKey, new SatelliteNetworkState.Body(bodyKey, capacityKbps, usedKbps));
        }
        return bodies;
    }

    private void writeLinks(ByteBuf buf) {
        buf.writeInt(
            state.links()
                .size());
        for (SatelliteNetworkState.Link link : state.links()) {
            PacketUtil.writeCelestialObjectKey(buf, link.from());
            PacketUtil.writeCelestialObjectKey(buf, link.to());
            buf.writeLong(link.capacityKbps());
            buf.writeLong(link.usedKbps());
            buf.writeLong(link.forwardUsedKbps());
            buf.writeLong(link.reverseUsedKbps());
        }
    }

    private static List<SatelliteNetworkState.Link> readLinks(ByteBuf buf) {
        int linkCount = buf.readInt();
        List<SatelliteNetworkState.Link> links = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) {
            CelestialObjectKey from = PacketUtil.readCelestialObjectKey(buf);
            CelestialObjectKey to = PacketUtil.readCelestialObjectKey(buf);
            long capacityKbps = buf.readLong();
            long usedKbps = buf.readLong();
            long forwardUsedKbps = buf.readLong();
            long reverseUsedKbps = buf.readLong();
            links.add(
                new SatelliteNetworkState.Link(from, to, capacityKbps, usedKbps, forwardUsedKbps, reverseUsedKbps));
        }
        return links;
    }

    private void writePendingData(ByteBuf buf) {
        buf.writeInt(
            state.pendingData()
                .size());
        for (SatelliteNetworkState.PendingData pending : state.pendingData()) {
            PacketUtil.writeCelestialObjectKey(buf, pending.bodyKey());
            buf.writeInt(
                pending.destinationBodyKeys()
                    .size());
            for (CelestialObjectKey destinationBodyKey : pending.destinationBodyKeys()) {
                PacketUtil.writeCelestialObjectKey(buf, destinationBodyKey);
            }
            PacketUtil.writeEnum(
                buf,
                pending.key()
                    .type());
            buf.writeBoolean(
                pending.key()
                    .hasOrigin());
            if (pending.key()
                .hasOrigin())
                PacketUtil.writeEnum(
                    buf,
                    pending.key()
                        .origin());
            buf.writeLong(pending.deciKb());
        }
    }

    private static List<SatelliteNetworkState.PendingData> readPendingData(ByteBuf buf) {
        int pendingCount = buf.readInt();
        List<SatelliteNetworkState.PendingData> pendingData = new ArrayList<>(pendingCount);
        for (int i = 0; i < pendingCount; i++) {
            CelestialObjectKey bodyKey = PacketUtil.readCelestialObjectKey(buf);
            int destinationCount = buf.readInt();
            List<CelestialObjectKey> destinationBodyKeys = new ArrayList<>(destinationCount);
            for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
                destinationBodyKeys.add(PacketUtil.readCelestialObjectKey(buf));
            }
            SatelliteDataType type = PacketUtil.readEnum(buf, SatelliteDataType.class);
            SatelliteDataKey key = buf.readBoolean()
                ? SatelliteDataKey.origin(type, PacketUtil.readEnum(buf, CelestialObjectId.class))
                : SatelliteDataKey.any(type);
            long deciKb = buf.readLong();
            pendingData.add(new SatelliteNetworkState.PendingData(bodyKey, destinationBodyKeys, key, deciKb));
        }
        return pendingData;
    }

    public static final class Handler implements IMessageHandler<SatelliteNetworkSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteNetworkSyncPacket message, MessageContext ctx) {
            SatelliteNetworkClientState.update(message.state);
            return null;
        }
    }
}
