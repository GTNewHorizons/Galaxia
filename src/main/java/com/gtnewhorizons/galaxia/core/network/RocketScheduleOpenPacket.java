package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Opens the same schedule editor from a passenger's physically occupied vehicle. */
public final class RocketScheduleOpenPacket implements IMessage, IMessageHandler<RocketScheduleOpenPacket, IMessage> {

    private UUID vehicleId;

    public RocketScheduleOpenPacket() {}

    public RocketScheduleOpenPacket(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        vehicleId = PacketUtil.readId(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, vehicleId);
    }

    @Override
    public IMessage onMessage(RocketScheduleOpenPacket message, MessageContext ctx) {
        var player = ctx.getServerHandler().playerEntity;
        ServerTickTaskQueue.schedule(() -> {
            EntityRocket entity = player.ridingEntity instanceof EntityRocket rocket ? rocket
                : player.ridingEntity instanceof EntityRocketSeat seat ? seat.getRocket() : null;
            if (entity == null || !message.vehicleId.equals(entity.vehicleId()) || !entity.isPlayerAboard(player))
                return;
            var vehicle = entity.rocket();
            if (vehicle == null || !entity.isCurrentProjection(vehicle)
                || !vehicle.teamId()
                    .equals(GTTeamsCompat.getTeam(player)))
                return;
            GuiFactories.entity()
                .open(player, entity);
        });
        return null;
    }
}
