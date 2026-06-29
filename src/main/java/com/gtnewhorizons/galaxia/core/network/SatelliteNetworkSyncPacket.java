package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
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
    private List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge = List.of();

    public SatelliteNetworkSyncPacket() {}

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state) {
        this(state, List.of());
    }

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state,
        List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge) {
        this.state = state;
        this.asteroidKnowledge = List.copyOf(asteroidKnowledge == null ? List.of() : asteroidKnowledge);
    }

    public SatelliteNetworkState state() {
        return state;
    }

    public List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge() {
        return asteroidKnowledge;
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
            buf.writeLong(link.forwardUsedKbps());
            buf.writeLong(link.reverseUsedKbps());
        }
        buf.writeInt(
            state.pendingData()
                .size());
        for (SatelliteNetworkState.PendingData pending : state.pendingData()) {
            PacketUtil.writeEnum(buf, pending.bodyId());
            buf.writeInt(
                pending.destinationBodyIds()
                    .size());
            for (CelestialObjectId destinationBodyId : pending.destinationBodyIds()) {
                PacketUtil.writeEnum(buf, destinationBodyId);
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
        buf.writeInt(asteroidKnowledge.size());
        for (AsteroidFieldKnowledgeSnapshot snapshot : asteroidKnowledge) {
            PacketUtil.writeEnum(buf, snapshot.beltId());
            buf.writeInt(
                snapshot.entries()
                    .size());
            for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
                buf.writeInt(entry.index());
                PacketUtil.writeEnum(buf, entry.detectionState());
                PacketUtil.writeEnum(buf, entry.oreKnowledgeState());
            }
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
            long forwardUsedKbps = buf.readLong();
            long reverseUsedKbps = buf.readLong();
            links.add(
                new SatelliteNetworkState.Link(from, to, capacityKbps, usedKbps, forwardUsedKbps, reverseUsedKbps));
        }
        int pendingCount = buf.readInt();
        List<SatelliteNetworkState.PendingData> pendingData = new ArrayList<>(pendingCount);
        for (int i = 0; i < pendingCount; i++) {
            CelestialObjectId bodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            int destinationCount = buf.readInt();
            List<CelestialObjectId> destinationBodyIds = new ArrayList<>(destinationCount);
            for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
                destinationBodyIds.add(PacketUtil.readEnum(buf, CelestialObjectId.class));
            }
            SatelliteDataType type = PacketUtil.readEnum(buf, SatelliteDataType.class);
            SatelliteDataKey key = buf.readBoolean()
                ? SatelliteDataKey.origin(type, PacketUtil.readEnum(buf, CelestialObjectId.class))
                : SatelliteDataKey.any(type);
            long deciKb = buf.readLong();
            pendingData.add(new SatelliteNetworkState.PendingData(bodyId, destinationBodyIds, key, deciKb));
        }
        state = new SatelliteNetworkState(teamId, revision, bodies, links, pendingData);
        int asteroidSnapshotCount = buf.readInt();
        List<AsteroidFieldKnowledgeSnapshot> snapshots = new ArrayList<>(asteroidSnapshotCount);
        for (int i = 0; i < asteroidSnapshotCount; i++) {
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            int entryCount = buf.readInt();
            List<AsteroidFieldKnowledgeSnapshot.Entry> entries = new ArrayList<>(entryCount);
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                entries.add(
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        buf.readInt(),
                        PacketUtil.readEnum(buf, AsteroidDetectionState.class),
                        PacketUtil.readEnum(buf, AsteroidOreKnowledgeState.class)));
            }
            snapshots.add(new AsteroidFieldKnowledgeSnapshot(beltId, entries));
        }
        asteroidKnowledge = List.copyOf(snapshots);
    }

    public static final class Handler implements IMessageHandler<SatelliteNetworkSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteNetworkSyncPacket message, MessageContext ctx) {
            SatelliteNetworkClientState.update(message.state);
            AsteroidFieldClientState.update(message.asteroidKnowledge);
            return null;
        }
    }
}
