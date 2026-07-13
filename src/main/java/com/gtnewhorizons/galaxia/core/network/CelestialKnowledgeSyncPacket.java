package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CelestialKnowledgeSyncPacket implements IMessage {

    private static final int MAX_SYNC_SECTIONS = 64;

    private UUID teamId;
    private List<CelestialKnowledgeSyncPayload> payloads = List.of();

    public CelestialKnowledgeSyncPacket() {}

    private CelestialKnowledgeSyncPacket(UUID teamId) {
        if (teamId == null) throw new IllegalArgumentException("team id is required");
        this.teamId = teamId;
    }

    public static CelestialKnowledgeSyncPacket forTeam(UUID teamId) {
        return new CelestialKnowledgeSyncPacket(teamId);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        List<CelestialKnowledgeSyncAdapter> adapters = CelestialKnowledgeSyncRegistry.adapters();
        PacketUtil.validateBoundedCount(adapters.size(), "celestial knowledge sync section", MAX_SYNC_SECTIONS);
        PacketUtil.writeId(buf, teamId);
        PacketUtil.writeBoundedCount(buf, adapters.size(), "celestial knowledge sync section", MAX_SYNC_SECTIONS);
        for (CelestialKnowledgeSyncAdapter adapter : adapters) {
            PacketUtil.writeString(
                buf,
                adapter.type()
                    .id());
            adapter.write(buf, teamId);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        teamId = PacketUtil.readId(buf);
        int count = PacketUtil.readBoundedCount(buf, "celestial knowledge sync section", MAX_SYNC_SECTIONS);
        List<CelestialKnowledgeSyncPayload> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CelestialKnowledgeSyncType type = new CelestialKnowledgeSyncType(PacketUtil.readString(buf));
            decoded.add(
                CelestialKnowledgeSyncRegistry.require(type)
                    .read(buf));
        }
        payloads = List.copyOf(decoded);
    }

    public static final class Handler implements IMessageHandler<CelestialKnowledgeSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(CelestialKnowledgeSyncPacket message, MessageContext ctx) {
            message.payloads.forEach(CelestialKnowledgeSyncPayload::applyClient);
            return null;
        }
    }
}
