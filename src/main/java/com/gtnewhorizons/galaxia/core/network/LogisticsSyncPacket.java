package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.UnknownNullability;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class LogisticsSyncPacket implements IMessage {

    private List<LogisticsDelivery> deliveries;
    private Map<CelestialObjectId, Map<String, Long>> bySystem;
    private Map<CelestialObjectId, Map<String, Long>> byPlanet;

    public LogisticsSyncPacket() {}

    public static LogisticsSyncPacket from(List<LogisticsDelivery> activeDeliveries) {
        LogisticsSyncPacket pkt = new LogisticsSyncPacket();

        pkt.deliveries = new java.util.ArrayList<>(activeDeliveries.size());
        for (LogisticsDelivery t : activeDeliveries) {
            if (t.data.resourceId() == null) continue;
            pkt.deliveries.add(t);
        }

        pkt.bySystem = new LinkedHashMap<>();
        pkt.byPlanet = new LinkedHashMap<>();

        for (Map.Entry<CelestialObjectId, List<LogisticSignal>> entry : LogisticStore
            .allSignalsForScope(LogisticSignal.Scope.SYSTEM)
            .entrySet()) {
            CelestialObjectId systemId = entry.getKey();
            Map<String, Long> systemAgg = new LinkedHashMap<>();
            for (LogisticSignal sig : entry.getValue()) {
                String key = sig.resourceId()
                    .toKey();
                systemAgg.merge(key, sig.amount(), Long::sum);
                CelestialObjectId anchorId = sig.planetaryAnchorBodyId();
                if (anchorId != null) {
                    pkt.byPlanet.computeIfAbsent(anchorId, k -> new LinkedHashMap<>())
                        .merge(key, sig.amount(), Long::sum);
                }
            }
            if (!systemAgg.isEmpty()) pkt.bySystem.put(systemId, systemAgg);
        }

        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deliveries.size());
        for (LogisticsDelivery t : deliveries) {
            LogisticsDelivery.Data d = t.data;
            PacketUtil.writeId(buf, t.deliveryId);
            PacketUtil.writeId(buf, d.fromAssetId());
            PacketUtil.writeId(buf, d.toAssetId());
            PacketUtil.writeString(
                buf,
                d.resourceId()
                    .toKey());
            buf.writeLong(d.amount());
            buf.writeInt(t.getRemainingTicks());
            PacketUtil.writeEnum(buf, d.scope());
            PacketUtil.writeEnum(buf, d.fromBodyId());
            PacketUtil.writeEnum(buf, d.toBodyId());
            buf.writeDouble(d.departureOrbitalTime());
            buf.writeDouble(d.tofOrbitalSeconds());
        }

        writeAggMap(buf, bySystem);
        writeAggMap(buf, byPlanet);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int deliveryCount = buf.readInt();
        deliveries = new ArrayList<>(deliveryCount);
        for (int i = 0; i < deliveryCount; i++) {
            deliveries.add(
                LogisticsDelivery.createWithTrajectory(
                    PacketUtil.readDeliveryId(buf),
                    PacketUtil.readAssetId(buf),
                    PacketUtil.readAssetId(buf),
                    ItemStackWrapper.fromKey(PacketUtil.readString(buf)),
                    buf.readLong(),
                    buf.readInt(),
                    PacketUtil.readEnum(buf, LogisticSignal.Scope.class),
                    PacketUtil.readEnum(buf, CelestialObjectId.class),
                    PacketUtil.readEnum(buf, CelestialObjectId.class),
                    buf.readDouble(),
                    buf.readDouble()));
        }

        bySystem = readAggMap(buf);
        byPlanet = readAggMap(buf);
    }

    public static final class Handler implements IMessageHandler<LogisticsSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LogisticsSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    CelestialClient.updateClientDeliveries(packet.deliveries);
                    CelestialClient.updateClientSignals(packet.bySystem, packet.byPlanet);
                });
            return null;
        }
    }

    private static void writeAggMap(ByteBuf buf, @UnknownNullability Map<CelestialObjectId, Map<String, Long>> map) {
        buf.writeInt(map.size());
        for (Map.Entry<CelestialObjectId, Map<String, Long>> outer : map.entrySet()) {
            PacketUtil.writeEnum(buf, outer.getKey());
            Map<String, Long> inner = outer.getValue();
            buf.writeInt(inner.size());
            for (Map.Entry<String, Long> e : inner.entrySet()) {
                PacketUtil.writeString(buf, e.getKey());
                buf.writeLong(e.getValue());
            }
        }
    }

    private static Map<CelestialObjectId, Map<String, Long>> readAggMap(ByteBuf buf) {
        int outerCount = buf.readInt();
        Map<CelestialObjectId, Map<String, Long>> map = new LinkedHashMap<>(outerCount);
        for (int i = 0; i < outerCount; i++) {
            CelestialObjectId outerKey = PacketUtil.readEnum(buf, CelestialObjectId.class);
            int innerCount = buf.readInt();
            Map<String, Long> inner = new LinkedHashMap<>(innerCount);
            for (int j = 0; j < innerCount; j++) {
                String resourceKey = PacketUtil.readString(buf);
                long net = buf.readLong();
                inner.put(resourceKey, net);
            }
            map.put(outerKey, inner);
        }
        return map;
    }
}
