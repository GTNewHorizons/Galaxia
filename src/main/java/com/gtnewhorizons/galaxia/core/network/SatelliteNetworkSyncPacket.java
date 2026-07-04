package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
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
    private List<AsteroidSatelliteScanSnapshot> asteroidScans = List.of();
    private List<AsteroidSatelliteScanCompletionSnapshot> asteroidScanCompletions = List.of();

    public SatelliteNetworkSyncPacket() {}

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state) {
        this(state, List.of());
    }

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state,
        List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge) {
        this(state, asteroidKnowledge, List.of(), List.of());
    }

    public SatelliteNetworkSyncPacket(SatelliteNetworkState state,
        List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge, List<AsteroidSatelliteScanSnapshot> asteroidScans,
        List<AsteroidSatelliteScanCompletionSnapshot> asteroidScanCompletions) {
        this.state = state;
        this.asteroidKnowledge = List.copyOf(asteroidKnowledge == null ? List.of() : asteroidKnowledge);
        this.asteroidScans = List.copyOf(asteroidScans == null ? List.of() : asteroidScans);
        this.asteroidScanCompletions = List
            .copyOf(asteroidScanCompletions == null ? List.of() : asteroidScanCompletions);
    }

    public SatelliteNetworkState state() {
        return state;
    }

    public List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledge() {
        return asteroidKnowledge;
    }

    public List<AsteroidSatelliteScanSnapshot> asteroidScans() {
        return asteroidScans;
    }

    public List<AsteroidSatelliteScanCompletionSnapshot> asteroidScanCompletions() {
        return asteroidScanCompletions;
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
            PacketUtil.writeCelestialObjectKey(buf, body.bodyKey());
            buf.writeLong(body.capacityKbps());
            buf.writeLong(body.usedKbps());
        }
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
        buf.writeInt(asteroidScans.size());
        for (AsteroidSatelliteScanSnapshot snapshot : asteroidScans) {
            PacketUtil.writeId(buf, snapshot.satelliteId());
            PacketUtil.writeEnum(buf, snapshot.beltId());
            PacketUtil.writeCelestialObjectKey(buf, CelestialObjectKey.minorBody(snapshot.asteroidId()));
            PacketUtil.writeEnum(buf, snapshot.pass());
            buf.writeInt(snapshot.elapsedTicks());
        }
        buf.writeInt(asteroidScanCompletions.size());
        for (AsteroidSatelliteScanCompletionSnapshot snapshot : asteroidScanCompletions) {
            PacketUtil.writeEnum(buf, snapshot.beltId());
            PacketUtil.writeCelestialObjectKey(buf, CelestialObjectKey.minorBody(snapshot.anchorAsteroidId()));
            buf.writeInt(snapshot.generationVersion());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        UUID teamId = PacketUtil.readId(buf);
        int revision = buf.readInt();
        int bodyCount = buf.readInt();
        Map<CelestialObjectKey, SatelliteNetworkState.Body> bodies = new HashMap<>();
        for (int i = 0; i < bodyCount; i++) {
            CelestialObjectKey bodyKey = PacketUtil.readCelestialObjectKey(buf);
            long capacityKbps = buf.readLong();
            long usedKbps = buf.readLong();
            bodies.put(bodyKey, new SatelliteNetworkState.Body(bodyKey, capacityKbps, usedKbps));
        }
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
                        PacketUtil.readEnum(buf, DiscoveryState.class),
                        PacketUtil.readEnum(buf, AsteroidOreKnowledgeState.class)));
            }
            snapshots.add(new AsteroidFieldKnowledgeSnapshot(beltId, entries));
        }
        asteroidKnowledge = List.copyOf(snapshots);
        int asteroidScanCount = buf.readInt();
        List<AsteroidSatelliteScanSnapshot> scanSnapshots = new ArrayList<>(asteroidScanCount);
        for (int i = 0; i < asteroidScanCount; i++) {
            var satelliteId = PacketUtil.readAssetId(buf);
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            MinorCelestialBodyId asteroidId = PacketUtil.readCelestialObjectKey(buf)
                .minorBodyId();
            AsteroidFieldScanPass pass = PacketUtil.readEnum(buf, AsteroidFieldScanPass.class);
            int elapsedTicks = buf.readInt();
            scanSnapshots.add(new AsteroidSatelliteScanSnapshot(satelliteId, beltId, asteroidId, pass, elapsedTicks));
        }
        asteroidScans = List.copyOf(scanSnapshots);
        int asteroidScanCompletionCount = buf.readInt();
        List<AsteroidSatelliteScanCompletionSnapshot> completionSnapshots = new ArrayList<>(
            asteroidScanCompletionCount);
        for (int i = 0; i < asteroidScanCompletionCount; i++) {
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            MinorCelestialBodyId anchorAsteroidId = PacketUtil.readCelestialObjectKey(buf)
                .minorBodyId();
            int generationVersion = buf.readInt();
            completionSnapshots
                .add(new AsteroidSatelliteScanCompletionSnapshot(beltId, anchorAsteroidId, generationVersion));
        }
        asteroidScanCompletions = List.copyOf(completionSnapshots);
    }

    public static final class Handler implements IMessageHandler<SatelliteNetworkSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteNetworkSyncPacket message, MessageContext ctx) {
            SatelliteNetworkClientState.update(message.state);
            AsteroidFieldClientKnowledgeState.updateFields(message.asteroidKnowledge);
            AsteroidScanClientState.updateScans(message.asteroidScans, message.asteroidScanCompletions);
            return null;
        }
    }
}
