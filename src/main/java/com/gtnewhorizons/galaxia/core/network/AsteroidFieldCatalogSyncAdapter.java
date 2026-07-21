package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientCatalogState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

import io.netty.buffer.ByteBuf;

/**
 * Wire section {@code galaxia:asteroid_catalog}: team-filtered asteroid node content.
 * <p>
 * TLDR: sends only node payloads needed for the synced team's facts and active
 * scans plus initial-discovered nodes of touched belts — never another team's
 * knowledge. Applied into content-only {@link AsteroidFieldClientCatalogState}.
 */
public final class AsteroidFieldCatalogSyncAdapter implements CelestialKnowledgeSyncAdapter {

    private static final int MAX_BELTS = 1_024;
    private static final int MAX_NODES_PER_BELT = 65_536;
    private static final CelestialKnowledgeSyncType TYPE = new CelestialKnowledgeSyncType("galaxia:asteroid_catalog");

    private final CelestialDiscoveryScanService scans;

    public AsteroidFieldCatalogSyncAdapter(CelestialDiscoveryScanService scans) {
        this.scans = scans;
    }

    @Override
    public CelestialKnowledgeSyncType type() {
        return TYPE;
    }

    @Override
    public void write(ByteBuf buf, UUID teamId) {
        Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> byBelt = AsteroidFieldNodeCatalog
            .catalogSnapshotsForMinors(teamMinorKeys(teamId));
        PacketUtil.writeBoundedCount(buf, byBelt.size(), "asteroid catalog belt", MAX_BELTS);
        byBelt.forEach((beltId, nodes) -> {
            PacketUtil.writeEnum(buf, beltId);
            writeNodeSnapshots(buf, nodes);
        });
    }

    @Override
    public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
        int beltCount = PacketUtil.readBoundedCount(buf, "asteroid catalog belt", MAX_BELTS);
        Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> byBelt = new LinkedHashMap<>();
        for (int i = 0; i < beltCount; i++) {
            CelestialObjectId beltId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            byBelt.put(beltId, readNodeSnapshots(buf));
        }
        Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> decoded = Map.copyOf(byBelt);
        return () -> AsteroidFieldClientCatalogState.update(decoded);
    }

    private List<CelestialObjectKey> teamMinorKeys(UUID teamId) {
        Set<CelestialObjectKey> minorKeys = new LinkedHashSet<>();
        CelestialKnowledgeService.snapshot(teamId)
            .keySet()
            .forEach(key -> { if (key.isMinorBody()) minorKeys.add(key); });
        for (CelestialDiscoveryScanSnapshot snapshot : scans.snapshots(teamId)) {
            if (snapshot.anchorKey()
                .isMinorBody()) minorKeys.add(snapshot.anchorKey());
            if (snapshot.targetKey() != null && snapshot.targetKey()
                .isMinorBody()) minorKeys.add(snapshot.targetKey());
        }
        return new ArrayList<>(minorKeys);
    }

    private static void writeNodeSnapshots(ByteBuf buf, List<AsteroidFieldNodeSnapshot> snapshots) {
        PacketUtil.writeBoundedCount(buf, snapshots.size(), "asteroid catalog node", MAX_NODES_PER_BELT);
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
        int count = PacketUtil.readBoundedCount(buf, "asteroid catalog node", MAX_NODES_PER_BELT);
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
