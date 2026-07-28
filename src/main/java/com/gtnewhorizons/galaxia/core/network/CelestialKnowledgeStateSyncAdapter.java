package com.gtnewhorizons.galaxia.core.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;

import io.netty.buffer.ByteBuf;

/**
 * Wire section {@code galaxia:celestial_knowledge}: one team's facts keyed by Key.
 * <p>
 * TLDR: carries registered and minor facts in a single Key-indexed section that
 * replaces the removed {@code galaxia:asteroid_fields} facts payload. Node content
 * travels separately in {@link AsteroidFieldCatalogSyncAdapter}.
 */
public final class CelestialKnowledgeStateSyncAdapter implements CelestialKnowledgeSyncAdapter {

    private static final int MAX_KNOWLEDGE_ENTRIES = 65_536;
    private static final CelestialKnowledgeSyncType TYPE = new CelestialKnowledgeSyncType(
        "galaxia:celestial_knowledge");

    @Override
    public CelestialKnowledgeSyncType type() {
        return TYPE;
    }

    @Override
    public void write(ByteBuf buf, UUID teamId) {
        Map<CelestialObjectKey, CelestialKnowledgeFacts> snapshot = CelestialKnowledgeService.snapshot(teamId);
        PacketUtil.writeBoundedCount(buf, snapshot.size(), "celestial knowledge entry", MAX_KNOWLEDGE_ENTRIES);
        snapshot.forEach((key, facts) -> {
            PacketUtil.writeCelestialObjectKey(buf, key);
            PacketUtil.writeEnum(buf, facts.discoveryState());
            PacketUtil.writeEnum(buf, facts.resourceKnowledgeState());
        });
    }

    @Override
    public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
        int count = PacketUtil.readBoundedCount(buf, "celestial knowledge entry", MAX_KNOWLEDGE_ENTRIES);
        Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            CelestialObjectKey key = PacketUtil.readCelestialObjectKey(buf);
            DiscoveryState discoveryState = PacketUtil.readEnum(buf, DiscoveryState.class);
            CelestialResourceKnowledgeState resourceState = PacketUtil
                .readEnum(buf, CelestialResourceKnowledgeState.class);
            // CelestialKnowledgeFacts.of rejects malformed HIDDEN + resource combinations.
            facts.put(key, CelestialKnowledgeFacts.of(discoveryState, resourceState));
        }
        Map<CelestialObjectKey, CelestialKnowledgeFacts> decoded = Map.copyOf(facts);
        return () -> CelestialKnowledgeClientState.apply(decoded);
    }
}
