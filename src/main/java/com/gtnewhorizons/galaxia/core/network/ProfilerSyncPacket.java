package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class ProfilerSyncPacket implements IMessage {

    public enum Metric {
        HAMMER_TRAJECTORY_LOAD
    }

    private Metric metric;
    private double primaryValue;
    private double secondaryValue;

    public ProfilerSyncPacket() {}

    public static ProfilerSyncPacket hammerTrajectoryLoad(double ownMsPerTick, double allMsPerTick) {
        ProfilerSyncPacket packet = new ProfilerSyncPacket();
        packet.metric = Metric.HAMMER_TRAJECTORY_LOAD;
        packet.primaryValue = ownMsPerTick;
        packet.secondaryValue = allMsPerTick;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeEnum(buf, metric);
        buf.writeDouble(primaryValue);
        buf.writeDouble(secondaryValue);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        metric = PacketUtil.readEnum(buf, Metric.class);
        primaryValue = buf.readDouble();
        secondaryValue = buf.readDouble();
    }

    static void applyClient(ProfilerSyncPacket packet) {
        if (packet == null || packet.metric == null) return;
        switch (packet.metric) {
            case HAMMER_TRAJECTORY_LOAD -> CelestialClient
                .updateHammerTrajectoryLoad(new HammerTrajectoryLoadSample(packet.primaryValue, packet.secondaryValue));
        }
    }

    public static final class Handler implements IMessageHandler<ProfilerSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(ProfilerSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> applyClient(packet));
            return null;
        }
    }
}
