package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamNetwork;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.compat.teams.TeamRole;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class TeamConfigPacket implements IMessage {

    public static final byte TYPE_PERMISSION = 0;
    public static final byte TYPE_COLOR = 1;

    private byte type;
    private TeamAction action;
    private TeamRole role;
    private int color;

    public TeamConfigPacket() {}

    public static TeamConfigPacket permission(TeamAction action, TeamRole role) {
        TeamConfigPacket pkt = new TeamConfigPacket();
        pkt.type = TYPE_PERMISSION;
        pkt.action = action;
        pkt.role = role;
        return pkt;
    }

    public static TeamConfigPacket color(int color) {
        TeamConfigPacket pkt = new TeamConfigPacket();
        pkt.type = TYPE_COLOR;
        pkt.color = color;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(type);
        if (type == TYPE_PERMISSION) {
            buf.writeByte(action.ordinal());
            buf.writeByte(role.ordinal());
        } else if (type == TYPE_COLOR) {
            buf.writeInt(color);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = buf.readByte();
        if (type == TYPE_PERMISSION) {
            TeamAction[] actions = TeamAction.values();
            TeamRole[] roles = TeamRole.values();
            int actionId = buf.readByte();
            int roleId = buf.readByte();
            action = actionId >= 0 && actionId < actions.length ? actions[actionId] : null;
            role = roleId >= 0 && roleId < roles.length ? roles[roleId] : null;
        } else if (type == TYPE_COLOR) {
            color = buf.readInt();
        }
    }

    public static class Handler implements IMessageHandler<TeamConfigPacket, IMessage> {

        @Override
        public IMessage onMessage(TeamConfigPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!GTTeamsCompat.isOwner(player)) return null;

            Team team = GTTeamsCompat.getTeamData(player)
                .orElse(null);
            if (team == null) return null;

            GalaxiaTeamData data = (GalaxiaTeamData) team.getData(GalaxiaTeamData.ID);
            if (data == null) return null;

            if (message.type == TYPE_PERMISSION) {
                if (message.action == null || message.role == null) return null;
                data.setRequiredRole(message.action, message.role);
            } else if (message.type == TYPE_COLOR) {
                data.setTeamColor(message.color);
            }

            TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
            return null;
        }
    }
}
