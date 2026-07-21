package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryStep;

import io.netty.buffer.ByteBuf;

public final class CelestialDiscoverySyncAdapter implements CelestialKnowledgeSyncAdapter {

    private static final int MAX_SCAN_SNAPSHOTS = 4_096;
    private static final CelestialKnowledgeSyncType TYPE = new CelestialKnowledgeSyncType(
        "galaxia:celestial_discovery");
    private final CelestialDiscoveryScanService scans;

    public CelestialDiscoverySyncAdapter(CelestialDiscoveryScanService scans) {
        this.scans = scans;
    }

    @Override
    public CelestialKnowledgeSyncType type() {
        return TYPE;
    }

    @Override
    public void write(ByteBuf buf, UUID teamId) {
        List<CelestialDiscoveryScanSnapshot> snapshots = scans.snapshots(teamId);
        PacketUtil.writeBoundedCount(buf, snapshots.size(), "celestial discovery snapshot", MAX_SCAN_SNAPSHOTS);
        for (CelestialDiscoveryScanSnapshot snapshot : snapshots) {
            PacketUtil.writeId(buf, snapshot.teamId());
            PacketUtil.writeCelestialObjectKey(buf, snapshot.anchorKey());
            buf.writeDouble(snapshot.radius());
            buf.writeLong(snapshot.scopeRevision());
            PacketUtil.writeEnum(buf, snapshot.capability());
            PacketUtil.writeEnum(buf, snapshot.status());
            if (snapshot.status() == CelestialDiscoveryScanSnapshot.Status.ACTIVE) {
                PacketUtil.writeCelestialObjectKey(buf, snapshot.targetKey());
                PacketUtil.writeEnum(buf, snapshot.step());
                buf.writeLong(snapshot.elapsedTicks());
            }
        }
    }

    @Override
    public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
        int count = PacketUtil.readBoundedCount(buf, "celestial discovery snapshot", MAX_SCAN_SNAPSHOTS);
        List<CelestialDiscoveryScanSnapshot> snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID teamId = PacketUtil.readId(buf);
            var anchorKey = PacketUtil.readCelestialObjectKey(buf);
            double radius = buf.readDouble();
            long revision = buf.readLong();
            CelestialDiscoveryCapability capability = PacketUtil.readEnum(buf, CelestialDiscoveryCapability.class);
            CelestialDiscoveryScanSnapshot.Status status = PacketUtil
                .readEnum(buf, CelestialDiscoveryScanSnapshot.Status.class);
            var targetKey = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE
                ? PacketUtil.readCelestialObjectKey(buf)
                : null;
            var step = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE
                ? PacketUtil.readEnum(buf, CelestialDiscoveryStep.class)
                : null;
            long elapsedTicks = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE ? buf.readLong() : 0;
            snapshots.add(
                new CelestialDiscoveryScanSnapshot(
                    teamId,
                    anchorKey,
                    radius,
                    revision,
                    capability,
                    status,
                    targetKey,
                    step,
                    elapsedTicks));
        }
        List<CelestialDiscoveryScanSnapshot> decoded = List.copyOf(snapshots);
        return () -> CelestialDiscoveryClientState.update(decoded);
    }
}
