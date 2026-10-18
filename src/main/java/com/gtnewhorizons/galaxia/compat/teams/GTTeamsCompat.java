package com.gtnewhorizons.galaxia.compat.teams;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.teams.TeamManagerClient;
import com.gtnewhorizon.gtnhlib.teams.TeamNetwork;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class GTTeamsCompat {

    private GTTeamsCompat() {}

    @SideOnly(Side.CLIENT)
    public static UUID getTeam() {
        Team team = TeamManagerClient.GetTeam();
        return team != null ? team.getTeamId() : null;
    }

    public static UUID getTeam(@Nonnull EntityPlayer player) {
        Team team = TeamManager.getTeamByPlayer(player.getUniqueID());
        return team.getTeamId();
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

    public static boolean hasAchievement(@Nullable EntityPlayer player, @Nonnull Achievement achievement) {
        if (player == null) return false;
        Team team = player.worldObj.isRemote ? TeamManagerClient.GetTeam()
            : TeamManager.getTeamByPlayer(player.getUniqueID());
        return team != null && team.getData(GalaxiaTeamData.ID) instanceof GalaxiaTeamData data
            && data.hasAchievement(achievement);
    }

    /** Awards authoritative server team data and immediately sends the changed ledger to its members. */
    public static boolean awardAchievement(@Nonnull UUID teamId, @Nonnull Achievement achievement) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) return false;
        Team team = TeamManager.getTeamById(teamId);
        if (team == null || team.isClientSide()
            || !(team.getData(GalaxiaTeamData.ID) instanceof GalaxiaTeamData data)
            || !data.award(achievement)) return false;
        team.markDirty();
        TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
        return true;
    }

    public static boolean teamOwns(@Nullable UUID currentTeam, @Nullable UUID originalOwner) {
        if (currentTeam == null || originalOwner == null) return false;
        if (currentTeam.equals(originalOwner)) return true;
        Team team = TeamManager.getTeamById(currentTeam);
        return team != null && team.getData(GalaxiaTeamData.ID) instanceof GalaxiaTeamData data
            && data.inheritedTeamIds()
                .contains(originalOwner);
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
