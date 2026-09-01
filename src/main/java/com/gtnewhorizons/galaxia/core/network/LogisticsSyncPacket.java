package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class LogisticsSyncPacket implements IMessage {

    private List<LogisticsDelivery> deliveries;
    private List<LogisticSignal> signals;

    public LogisticsSyncPacket() {}

    public static LogisticsSyncPacket from(List<LogisticsDelivery> activeDeliveries, List<LogisticSignal> signals) {
        LogisticsSyncPacket pkt = new LogisticsSyncPacket();

        pkt.deliveries = new java.util.ArrayList<>(activeDeliveries.size());
        for (LogisticsDelivery t : activeDeliveries) {
            if (t.data.resourceId() == null) continue;
            pkt.deliveries.add(t);
        }

        pkt.signals = List.copyOf(signals);
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
            PacketUtil.writeCelestialObjectKey(buf, d.fromBodyKey());
            PacketUtil.writeCelestialObjectKey(buf, d.toBodyKey());
            buf.writeDouble(d.departureOrbitalTime());
            buf.writeDouble(d.tofOrbitalOsu());
            writeTransferRoute(buf, d.transferRoute());
        }

        buf.writeInt(signals.size());
        for (LogisticSignal signal : signals) {
            PacketUtil.writeId(buf, signal.outpostAssetId());
            PacketUtil.writeCelestialObjectKey(buf, signal.systemKey());
            PacketUtil.writeString(
                buf,
                signal.resourceId()
                    .toKey());
            buf.writeLong(signal.amount());
            PacketUtil.writeEnum(buf, signal.scope());
            PacketUtil.writeCelestialObjectKey(buf, signal.bodyKey());
            PacketUtil.writeCelestialObjectKey(buf, signal.planetaryAnchorBodyKey());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int deliveryCount = buf.readInt();
        deliveries = new ArrayList<>(deliveryCount);
        for (int i = 0; i < deliveryCount; i++) {
            LogisticsDelivery.ID deliveryId = PacketUtil.readDeliveryId(buf);
            CelestialAsset.ID fromAssetId = PacketUtil.readAssetId(buf);
            CelestialAsset.ID toAssetId = PacketUtil.readAssetId(buf);
            ItemStackWrapper resourceId = ItemStackWrapper.fromKey(PacketUtil.readString(buf));
            long amount = buf.readLong();
            int remainingTicks = buf.readInt();
            LogisticSignal.Scope scope = PacketUtil.readEnum(buf, LogisticSignal.Scope.class);
            CelestialObjectKey fromBodyKey = PacketUtil.readCelestialObjectKey(buf);
            CelestialObjectKey toBodyKey = PacketUtil.readCelestialObjectKey(buf);
            double departureOrbitalTime = buf.readDouble();
            double tofOrbitalOsu = buf.readDouble();
            OrbitalTransferPlanner.TransferRoute transferRoute = readTransferRoute(buf);
            deliveries.add(
                LogisticsDelivery.createWithTrajectory(
                    deliveryId,
                    fromAssetId,
                    toAssetId,
                    resourceId,
                    amount,
                    remainingTicks,
                    scope,
                    fromBodyKey,
                    toBodyKey,
                    departureOrbitalTime,
                    tofOrbitalOsu,
                    transferRoute));
        }

        int signalCount = buf.readInt();
        signals = new ArrayList<>(signalCount);
        for (int i = 0; i < signalCount; i++) {
            signals.add(
                new LogisticSignal(
                    PacketUtil.readAssetId(buf),
                    PacketUtil.readCelestialObjectKey(buf),
                    ItemStackWrapper.fromKey(PacketUtil.readString(buf)),
                    buf.readLong(),
                    PacketUtil.readEnum(buf, LogisticSignal.Scope.class),
                    PacketUtil.readCelestialObjectKey(buf),
                    PacketUtil.readCelestialObjectKey(buf)));
        }
    }

    public static final class Handler implements IMessageHandler<LogisticsSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LogisticsSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    CelestialClient.updateClientDeliveries(packet.deliveries);
                    CelestialClient.updateClientSignals(packet.signals);
                });
            return null;
        }
    }

    private static void writeTransferRoute(ByteBuf buf, OrbitalTransferPlanner.TransferRoute route) {
        if (route == null || !route.hasTrajectoryGeometry()) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeDouble(route.tofOsu());
        buf.writeDouble(route.totalDv());
        buf.writeDouble(route.departureDv());
        buf.writeDouble(route.captureDv());
        PacketUtil.writeCelestialObjectKey(buf, route.attractorBodyKey());
        buf.writeDouble(route.anchorX());
        buf.writeDouble(route.anchorY());
        buf.writeDouble(route.r1x());
        buf.writeDouble(route.r1y());
        buf.writeDouble(route.departureVelocityX());
        buf.writeDouble(route.departureVelocityY());
        buf.writeBoolean(route.prograde());
    }

    private static OrbitalTransferPlanner.TransferRoute readTransferRoute(ByteBuf buf) {
        if (!buf.readBoolean()) return null;
        double tofOsu = buf.readDouble();
        double totalDv = buf.readDouble();
        double departureDv = buf.readDouble();
        double captureDv = buf.readDouble();
        CelestialObjectKey attractorBodyKey = PacketUtil.readCelestialObjectKey(buf);
        double anchorX = buf.readDouble();
        double anchorY = buf.readDouble();
        double r1x = buf.readDouble();
        double r1y = buf.readDouble();
        double departureVelocityX = buf.readDouble();
        double departureVelocityY = buf.readDouble();
        boolean prograde = buf.readBoolean();
        return new OrbitalTransferPlanner.TransferRoute(
            tofOsu,
            totalDv,
            departureDv,
            captureDv,
            attractorBodyKey,
            anchorX,
            anchorY,
            r1x,
            r1y,
            departureVelocityX,
            departureVelocityY,
            prograde);
    }

}
