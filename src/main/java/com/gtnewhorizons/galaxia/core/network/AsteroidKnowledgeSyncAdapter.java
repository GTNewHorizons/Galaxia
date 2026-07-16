package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

import io.netty.buffer.ByteBuf;

public final class AsteroidKnowledgeSyncAdapter implements CelestialKnowledgeSyncAdapter {

    private static final int MAX_FIELD_SNAPSHOTS = 1_024;
    private static final int MAX_ENTRIES_PER_FIELD = 65_536;
    private static final CelestialKnowledgeSyncType TYPE = new CelestialKnowledgeSyncType("galaxia:asteroid_fields");

    @Override
    public CelestialKnowledgeSyncType type() {
        return TYPE;
    }

    @Override
    public void write(ByteBuf buf, UUID teamId) {
        writeKnowledge(
            buf,
            AsteroidFieldKnowledgeStore.global()
                .snapshots(teamId));
    }

    @Override
    public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
        List<AsteroidFieldKnowledgeSnapshot> knowledge = readKnowledge(buf);
        return () -> AsteroidFieldClientKnowledgeState.updateFields(knowledge);
    }

    private static void writeKnowledge(ByteBuf buf, List<AsteroidFieldKnowledgeSnapshot> snapshots) {
        PacketUtil.writeBoundedCount(buf, snapshots.size(), "asteroid field snapshot", MAX_FIELD_SNAPSHOTS);
        for (AsteroidFieldKnowledgeSnapshot snapshot : snapshots) {
            PacketUtil.writeEnum(buf, snapshot.beltId());
            PacketUtil.writeBoundedCount(
                buf,
                snapshot.entries()
                    .size(),
                "asteroid field entry",
                MAX_ENTRIES_PER_FIELD);
            for (AsteroidFieldKnowledgeSnapshot.Entry entry : snapshot.entries()) {
                buf.writeInt(entry.index());
                PacketUtil.writeEnum(buf, entry.detectionState());
                PacketUtil.writeEnum(buf, entry.oreKnowledgeState());
            }
            writeNodeSnapshots(buf, snapshot.nodeSnapshots());
        }
    }

    private static List<AsteroidFieldKnowledgeSnapshot> readKnowledge(ByteBuf buf) {
        int snapshotCount = PacketUtil.readBoundedCount(buf, "asteroid field snapshot", MAX_FIELD_SNAPSHOTS);
        List<AsteroidFieldKnowledgeSnapshot> snapshots = new ArrayList<>(snapshotCount);
        for (int i = 0; i < snapshotCount; i++) {
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            int entryCount = PacketUtil.readBoundedCount(buf, "asteroid field entry", MAX_ENTRIES_PER_FIELD);
            List<AsteroidFieldKnowledgeSnapshot.Entry> entries = new ArrayList<>(entryCount);
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                entries.add(
                    new AsteroidFieldKnowledgeSnapshot.Entry(
                        buf.readInt(),
                        PacketUtil.readEnum(buf, DiscoveryState.class),
                        PacketUtil.readEnum(buf, CelestialResourceKnowledgeState.class)));
            }
            snapshots.add(new AsteroidFieldKnowledgeSnapshot(beltId, entries, readNodeSnapshots(buf)));
        }
        return List.copyOf(snapshots);
    }

    private static void writeNodeSnapshots(ByteBuf buf, List<AsteroidFieldNodeSnapshot> snapshots) {
        buf.writeInt(snapshots.size());
        for (AsteroidFieldNodeSnapshot snapshot : snapshots) {
            buf.writeInt(snapshot.index());
            PacketUtil.writeString(buf, snapshot.displayName());
            PacketUtil.writeEnum(buf, snapshot.kind());
            PacketUtil.writeEnum(buf, snapshot.sizeClass());
            PacketUtil.writeEnum(buf, snapshot.initialDetectionState());
            PacketUtil.writeEnum(buf, snapshot.initialOreKnowledgeState());
            buf.writeDouble(snapshot.angleOffsetDeg());
            buf.writeDouble(snapshot.orbitalDepth01());
            PacketUtil.writeString(
                buf,
                snapshot.oreProfile()
                    .id());
            buf.writeInt(
                snapshot.oreProfile()
                    .gtOreVeinIds()
                    .size());
            for (String veinId : snapshot.oreProfile()
                .gtOreVeinIds()) PacketUtil.writeString(buf, veinId);
            PacketUtil.writeString(
                buf,
                snapshot.appearance()
                    .iconRecipeId());
            buf.writeLong(
                snapshot.appearance()
                    .variantSeed());
        }
    }

    private static List<AsteroidFieldNodeSnapshot> readNodeSnapshots(ByteBuf buf) {
        int count = buf.readInt();
        List<AsteroidFieldNodeSnapshot> snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = buf.readInt();
            String displayName = PacketUtil.readString(buf);
            AsteroidNodeKind kind = PacketUtil.readEnum(buf, AsteroidNodeKind.class);
            AsteroidSizeClass sizeClass = PacketUtil.readEnum(buf, AsteroidSizeClass.class);
            DiscoveryState detection = PacketUtil.readEnum(buf, DiscoveryState.class);
            CelestialResourceKnowledgeState oreKnowledge = PacketUtil
                .readEnum(buf, CelestialResourceKnowledgeState.class);
            double angle = buf.readDouble();
            double depth = buf.readDouble();
            String profileId = PacketUtil.readString(buf);
            int veinCount = buf.readInt();
            List<String> veins = new ArrayList<>(veinCount);
            for (int vein = 0; vein < veinCount; vein++) veins.add(PacketUtil.readString(buf));
            snapshots.add(
                new AsteroidFieldNodeSnapshot(
                    index,
                    displayName,
                    kind,
                    sizeClass,
                    detection,
                    oreKnowledge,
                    angle,
                    depth,
                    new AsteroidFieldNodeSnapshot.OreProfileSnapshot(profileId, veins),
                    new AsteroidFieldNodeSnapshot.AppearanceSnapshot(PacketUtil.readString(buf), buf.readLong())));
        }
        return List.copyOf(snapshots);
    }

}
