package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CelestialKnowledgeSyncPacket implements IMessage {

    private static final int MAX_KNOWLEDGE_ENTRIES = 65_536;
    private static final int MAX_SCAN_SNAPSHOTS = 4_096;

    private UUID teamId;
    private Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = Map.of();
    private List<CelestialDiscoveryScanSnapshot> scans = List.of();

    public CelestialKnowledgeSyncPacket() {}

    private CelestialKnowledgeSyncPacket(UUID teamId, CelestialDiscoveryScanService scanService) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        if (scanService == null) throw new IllegalArgumentException("discovery scan service is required");
        this.teamId = teamId;
        facts = CelestialKnowledgeService.snapshot(teamId);
        scans = scanService.snapshots(teamId);
    }

    public static CelestialKnowledgeSyncPacket forTeam(UUID teamId, CelestialDiscoveryScanService scanService) {
        return new CelestialKnowledgeSyncPacket(teamId, scanService);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.validateBoundedCount(facts.size(), "celestial knowledge entry", MAX_KNOWLEDGE_ENTRIES);
        PacketUtil.validateBoundedCount(scans.size(), "celestial discovery snapshot", MAX_SCAN_SNAPSHOTS);
        PacketUtil.writeId(buf, teamId);
        PacketUtil.writeBoundedCount(buf, facts.size(), "celestial knowledge entry", MAX_KNOWLEDGE_ENTRIES);
        facts.forEach((key, knowledge) -> {
            PacketUtil.writeCelestialObjectKey(buf, key);
            PacketUtil.writeEnum(buf, knowledge.discoveryState());
            PacketUtil.writeEnum(buf, knowledge.resourceKnowledgeState());
        });
        PacketUtil.writeBoundedCount(buf, scans.size(), "celestial discovery snapshot", MAX_SCAN_SNAPSHOTS);
        scans.forEach(scan -> writeScan(buf, scan));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        teamId = PacketUtil.readId(buf);
        facts = readFacts(buf);
        scans = readScans(buf, teamId);
    }

    private static Map<CelestialObjectKey, CelestialKnowledgeFacts> readFacts(ByteBuf buf) {
        int count = PacketUtil.readBoundedCount(buf, "celestial knowledge entry", MAX_KNOWLEDGE_ENTRIES);
        Map<CelestialObjectKey, CelestialKnowledgeFacts> decoded = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            CelestialObjectKey key = PacketUtil.readCelestialObjectKey(buf);
            DiscoveryState discoveryState = PacketUtil.readEnum(buf, DiscoveryState.class);
            CelestialResourceKnowledgeState resourceState = PacketUtil
                .readEnum(buf, CelestialResourceKnowledgeState.class);
            decoded.put(key, CelestialKnowledgeFacts.of(discoveryState, resourceState));
        }
        return Map.copyOf(decoded);
    }

    private static List<CelestialDiscoveryScanSnapshot> readScans(ByteBuf buf, UUID expectedTeamId) {
        int count = PacketUtil.readBoundedCount(buf, "celestial discovery snapshot", MAX_SCAN_SNAPSHOTS);
        List<CelestialDiscoveryScanSnapshot> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID scanTeamId = PacketUtil.readId(buf);
            if (!expectedTeamId.equals(scanTeamId)) {
                throw new IllegalStateException("celestial discovery snapshot belongs to another team");
            }
            CelestialObjectKey anchorKey = PacketUtil.readCelestialObjectKey(buf);
            double radius = buf.readDouble();
            long scopeRevision = buf.readLong();
            CelestialDiscoveryCapability capability = PacketUtil.readEnum(buf, CelestialDiscoveryCapability.class);
            CelestialDiscoveryScanSnapshot.Status status = PacketUtil
                .readEnum(buf, CelestialDiscoveryScanSnapshot.Status.class);
            CelestialObjectKey targetKey = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE
                ? PacketUtil.readCelestialObjectKey(buf)
                : null;
            CelestialDiscoveryStep step = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE
                ? PacketUtil.readEnum(buf, CelestialDiscoveryStep.class)
                : null;
            long elapsedTicks = status == CelestialDiscoveryScanSnapshot.Status.ACTIVE ? buf.readLong() : 0;
            decoded.add(
                new CelestialDiscoveryScanSnapshot(
                    scanTeamId,
                    anchorKey,
                    radius,
                    scopeRevision,
                    capability,
                    status,
                    targetKey,
                    step,
                    elapsedTicks));
        }
        return List.copyOf(decoded);
    }

    private static void writeScan(ByteBuf buf, CelestialDiscoveryScanSnapshot scan) {
        PacketUtil.writeId(buf, scan.teamId());
        PacketUtil.writeCelestialObjectKey(buf, scan.anchorKey());
        buf.writeDouble(scan.radius());
        buf.writeLong(scan.scopeRevision());
        PacketUtil.writeEnum(buf, scan.capability());
        PacketUtil.writeEnum(buf, scan.status());
        if (scan.status() == CelestialDiscoveryScanSnapshot.Status.ACTIVE) {
            PacketUtil.writeCelestialObjectKey(buf, scan.targetKey());
            PacketUtil.writeEnum(buf, scan.step());
            buf.writeLong(scan.elapsedTicks());
        }
    }

    public static final class Handler implements IMessageHandler<CelestialKnowledgeSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(CelestialKnowledgeSyncPacket message, MessageContext ctx) {
            CelestialKnowledgeClientState.apply(message.facts);
            CelestialDiscoveryClientState.update(message.scans);
            return null;
        }
    }
}
