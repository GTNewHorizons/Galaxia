package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

import io.netty.buffer.ByteBuf;

public enum AsteroidKnowledgeSyncAdapter implements CelestialKnowledgeSyncAdapter {

    INSTANCE;

    private static final CelestialKnowledgeSyncType TYPE = new CelestialKnowledgeSyncType("galaxia:asteroid_fields");

    @Override
    public CelestialKnowledgeSyncType type() {
        return TYPE;
    }

    @Override
    public void write(ByteBuf buf, UUID teamId) {
        writeKnowledge(buf, SatelliteNetworkService.asteroidKnowledgeSnapshots(teamId));
        writeScans(
            buf,
            SatelliteNetworkService.asteroidScanSnapshots(teamId),
            SatelliteNetworkService.asteroidScanCompletionSnapshots(teamId));
    }

    @Override
    public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
        List<AsteroidFieldKnowledgeSnapshot> knowledge = readKnowledge(buf);
        List<AsteroidSatelliteScanSnapshot> scans = readScans(buf);
        List<AsteroidSatelliteScanCompletionSnapshot> completions = readCompletions(buf);
        return () -> {
            AsteroidFieldClientKnowledgeState.updateFields(knowledge);
            AsteroidScanClientState.updateScans(scans, completions);
        };
    }

    private static void writeKnowledge(ByteBuf buf, List<AsteroidFieldKnowledgeSnapshot> snapshots) {
        buf.writeInt(snapshots.size());
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
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

    private static List<AsteroidFieldKnowledgeSnapshot> readKnowledge(ByteBuf buf) {
        int snapshotCount = buf.readInt();
        List<AsteroidFieldKnowledgeSnapshot> snapshots = new ArrayList<>(snapshotCount);
        for (int i = 0; i < snapshotCount; i++) {
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
        return List.copyOf(snapshots);
    }

    private static void writeScans(ByteBuf buf, List<AsteroidSatelliteScanSnapshot> scans,
        List<AsteroidSatelliteScanCompletionSnapshot> completions) {
        buf.writeInt(scans.size());
        for (AsteroidSatelliteScanSnapshot snapshot : scans) {
            PacketUtil.writeId(buf, snapshot.satelliteId());
            PacketUtil.writeEnum(buf, snapshot.beltId());
            PacketUtil.writeCelestialObjectKey(buf, CelestialObjectKey.minorBody(snapshot.asteroidId()));
            PacketUtil.writeEnum(buf, snapshot.pass());
            buf.writeInt(snapshot.elapsedTicks());
        }
        buf.writeInt(completions.size());
        for (AsteroidSatelliteScanCompletionSnapshot snapshot : completions) {
            PacketUtil.writeEnum(buf, snapshot.beltId());
            PacketUtil.writeCelestialObjectKey(buf, CelestialObjectKey.minorBody(snapshot.anchorAsteroidId()));
            buf.writeInt(snapshot.generationVersion());
        }
    }

    private static List<AsteroidSatelliteScanSnapshot> readScans(ByteBuf buf) {
        int scanCount = buf.readInt();
        List<AsteroidSatelliteScanSnapshot> scanSnapshots = new ArrayList<>(scanCount);
        for (int i = 0; i < scanCount; i++) {
            var satelliteId = PacketUtil.readAssetId(buf);
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            MinorCelestialBodyId asteroidId = PacketUtil.readCelestialObjectKey(buf)
                .minorBodyId();
            AsteroidFieldScanPass pass = PacketUtil.readEnum(buf, AsteroidFieldScanPass.class);
            int elapsedTicks = buf.readInt();
            scanSnapshots.add(new AsteroidSatelliteScanSnapshot(satelliteId, beltId, asteroidId, pass, elapsedTicks));
        }
        return List.copyOf(scanSnapshots);
    }

    private static List<AsteroidSatelliteScanCompletionSnapshot> readCompletions(ByteBuf buf) {
        int completionCount = buf.readInt();
        List<AsteroidSatelliteScanCompletionSnapshot> completionSnapshots = new ArrayList<>(completionCount);
        for (int i = 0; i < completionCount; i++) {
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            MinorCelestialBodyId anchorAsteroidId = PacketUtil.readCelestialObjectKey(buf)
                .minorBodyId();
            int generationVersion = buf.readInt();
            completionSnapshots
                .add(new AsteroidSatelliteScanCompletionSnapshot(beltId, anchorAsteroidId, generationVersion));
        }
        return List.copyOf(completionSnapshots);
    }
}
