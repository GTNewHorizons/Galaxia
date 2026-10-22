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

/** The team owner changing which role a team action needs */
public final class TeamConfigPacket implements IMessage {

    private TeamAction action;
    private TeamRole role;

    public TeamConfigPacket() {}

    public static TeamConfigPacket permission(TeamAction action, TeamRole role) {
        TeamConfigPacket pkt = new TeamConfigPacket();
        pkt.action = action;
        pkt.role = role;
        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeByte(role.ordinal());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        TeamAction[] actions = TeamAction.values();
        TeamRole[] roles = TeamRole.values();
        int actionId = buf.readByte();
        int roleId = buf.readByte();
        action = actionId >= 0 && actionId < actions.length ? actions[actionId] : null;
        role = roleId >= 0 && roleId < roles.length ? roles[roleId] : null;
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

            if (message.action == null || message.role == null) return null;
            data.setRequiredRole(message.action, message.role);

            TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
            return null;
        }
    }
}
