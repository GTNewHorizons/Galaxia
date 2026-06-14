package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.PlanetarySatelliteStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class SatelliteDebugMutationPacket implements IMessage {

    public enum Operation {
        ADD,
        SET,
        DELETE_ALL
    }

    private UUID teamId;
    private CelestialObjectId bodyId;
    private SatelliteKind kind;
    private Operation operation;
    private int amount;

    public SatelliteDebugMutationPacket() {}

    private SatelliteDebugMutationPacket(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind, Operation operation,
        int amount) {
        this.teamId = teamId;
        this.bodyId = bodyId;
        this.kind = kind;
        this.operation = operation;
        this.amount = amount;
    }

    public static SatelliteDebugMutationPacket add(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind,
        int amount) {
        return new SatelliteDebugMutationPacket(teamId, bodyId, kind, Operation.ADD, amount);
    }

    public static SatelliteDebugMutationPacket set(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind,
        int count) {
        return new SatelliteDebugMutationPacket(teamId, bodyId, kind, Operation.SET, count);
    }

    public static SatelliteDebugMutationPacket deleteAll(UUID teamId, CelestialObjectId bodyId, SatelliteKind kind) {
        return new SatelliteDebugMutationPacket(teamId, bodyId, kind, Operation.DELETE_ALL, 0);
    }

    public static boolean isAuthorized(boolean op, boolean creative) {
        return op && creative;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        teamId = PacketUtil.readId(buf);
        bodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
        kind = PacketUtil.readEnum(buf, SatelliteKind.class);
        operation = PacketUtil.readEnum(buf, Operation.class);
        amount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, teamId);
        PacketUtil.writeEnum(buf, bodyId);
        PacketUtil.writeEnum(buf, kind);
        PacketUtil.writeEnum(buf, operation);
        buf.writeInt(amount);
    }

    public void apply(PlanetarySatelliteStore store) {
        switch (operation) {
            case ADD -> {
                if (amount <= 0) throw new IllegalArgumentException("Satellite ADD amount must be positive: " + amount);
                store.add(teamId, bodyId, kind, amount);
            }
            case SET -> store.set(teamId, bodyId, kind, amount);
            case DELETE_ALL -> store.deleteAll(teamId, bodyId, kind);
        }
    }

    private static boolean isPlayerOp(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }

    public static final class Handler implements IMessageHandler<SatelliteDebugMutationPacket, IMessage> {

        @Override
        public IMessage onMessage(SatelliteDebugMutationPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            if (!isAuthorized(isPlayerOp(player), player.capabilities.isCreativeMode)) return null;

            packet.apply(PlanetarySatelliteStore.SERVER);
            return SatelliteSyncPacket
                .fullForTeam(packet.teamId, PlanetarySatelliteStore.SERVER.snapshotTeam(packet.teamId));
        }
    }
}
