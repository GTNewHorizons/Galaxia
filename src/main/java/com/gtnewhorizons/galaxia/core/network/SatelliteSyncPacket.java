package com.gtnewhorizons.galaxia.core.network;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class SatelliteSyncPacket implements IMessage {

    private UUID teamId;
    private Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> counts = new LinkedHashMap<>();

    public SatelliteSyncPacket() {}

    private SatelliteSyncPacket(UUID teamId, Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> counts) {
        this.teamId = teamId;
        this.counts = copyCounts(counts);
    }

    public static SatelliteSyncPacket fullForTeam(UUID teamId,
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> counts) {
        return new SatelliteSyncPacket(teamId, counts);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        teamId = PacketUtil.readId(buf);
        int bodyCount = buf.readInt();
        counts = new LinkedHashMap<>();
        for (int i = 0; i < bodyCount; i++) {
            CelestialObjectId bodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
            int kindCount = buf.readInt();
            EnumMap<SatelliteKind, Integer> bodyCounts = new EnumMap<>(SatelliteKind.class);
            for (int j = 0; j < kindCount; j++) {
                SatelliteKind kind = PacketUtil.readEnum(buf, SatelliteKind.class);
                int count = buf.readInt();
                if (count > 0) bodyCounts.put(kind, count);
            }
            if (!bodyCounts.isEmpty()) counts.put(bodyId, bodyCounts);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, teamId);
        buf.writeInt(counts.size());
        for (Map.Entry<CelestialObjectId, EnumMap<SatelliteKind, Integer>> bodyEntry : counts.entrySet()) {
            PacketUtil.writeEnum(buf, bodyEntry.getKey());
            buf.writeInt(
                bodyEntry.getValue()
                    .size());
            for (Map.Entry<SatelliteKind, Integer> kindEntry : bodyEntry.getValue()
                .entrySet()) {
                PacketUtil.writeEnum(buf, kindEntry.getKey());
                buf.writeInt(kindEntry.getValue());
            }
        }
    }

    public void applyTo(CelestialAssetStore store) {
        store.replaceTeamSatelliteCounts(teamId, counts);
    }

    private static Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> copyCounts(
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> source) {
        Map<CelestialObjectId, EnumMap<SatelliteKind, Integer>> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        for (Map.Entry<CelestialObjectId, EnumMap<SatelliteKind, Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new EnumMap<>(entry.getValue()));
        }
        return copy;
    }

    public static final class Handler implements IMessageHandler<SatelliteSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteSyncPacket packet, MessageContext ctx) {
            packet.applyTo(CelestialAssetStore.CLIENT);
            return null;
        }
    }
}
