package com.gtnewhorizons.galaxia.compat.teams;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.teams.TeamManagerClient;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class GTTeamsCompat {

    private GTTeamsCompat() {}

    @SideOnly(Side.CLIENT)
    public static UUID getTeam() {
        Team team = TeamManagerClient.GetTeam();
        return team != null ? team.getTeamId() : null;
    }

    /**
     * Team of a player, null when the player has none
     */
    public static UUID getTeam(@Nonnull EntityPlayer player) {
        return getTeam(player.getUniqueID());
    }

    /**
     * Team of a player by id, also works while the player is offline
     *
     * @param playerId The player's UUID
     *
     * @return The team id, or null when the player has no team
     */
    public static UUID getTeam(UUID playerId) {
        if (playerId == null) return null;
        Team team = TeamManager.getTeamByPlayer(playerId);
        return team != null ? team.getTeamId() : null;
    }

    @SideOnly(Side.CLIENT)
    public static Optional<String> getTeamName() {
        return Optional.ofNullable(TeamManagerClient.GetTeam())
            .map(Team::getTeamName);
    }

    public static boolean isOwner(EntityPlayer player) {
        if (player == null) return false;
        Team team = TeamManager.getTeamByPlayer(player.getUniqueID());
        return team != null && team.isOwner(player.getUniqueID());
    }

    public static Optional<Team> getTeamData(EntityPlayer player) {
        if (player == null) return Optional.empty();
        return Optional.ofNullable(TeamManager.getTeamByPlayer(player.getUniqueID()));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<Team> getTeamData() {
        return Optional.ofNullable(TeamManagerClient.GetTeam());
    }

    @SideOnly(Side.CLIENT)
    public static Optional<GalaxiaTeamData> getGalaxiaTeamData() {
        return getTeamData().map(t -> (GalaxiaTeamData) t.getData(GalaxiaTeamData.ID));
    }

    public static boolean hasPermission(EntityPlayer player, TeamAction action) {
        if (player == null) return false;
        Team team = TeamManager.getTeamByPlayer(player.getUniqueID());
        if (team == null) return false;
        return hasPermission(team, player, action);
    }

    public static boolean hasPermission(UUID teamId, EntityPlayer player, TeamAction action) {
        if (teamId == null || player == null) return false;
        Team team = TeamManager.getTeamById(teamId);
        return hasPermission(team, player, action);
    }

    public static boolean hasPermission(Team team, EntityPlayer player, TeamAction action) {
        if (team == null || player == null) return false;
        TeamRole required = Optional.ofNullable((GalaxiaTeamData) team.getData(GalaxiaTeamData.ID))
            .map(d -> d.getRequiredRole(action))
            .orElse(action.getDefaultRole());
        UUID id = player.getUniqueID();
        TeamRole actual = team.isOwner(id) ? TeamRole.OWNER
            : team.isOfficer(id) ? TeamRole.OFFICER : team.isMember(id) ? TeamRole.MEMBER : null;
        return actual != null && actual.atLeast(required);
    }
}
