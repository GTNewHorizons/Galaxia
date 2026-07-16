package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
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
            snapshots.add(new AsteroidFieldKnowledgeSnapshot(beltId, entries));
        }
        return List.copyOf(snapshots);
    }

}
