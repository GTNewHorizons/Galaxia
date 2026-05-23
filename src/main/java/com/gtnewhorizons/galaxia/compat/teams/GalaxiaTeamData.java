package com.gtnewhorizons.galaxia.compat.teams;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.teams.INetworkTeamData;
import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataCopyReason;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;

import lombok.Getter;

public class GalaxiaTeamData implements INetworkTeamData {

    public static final String ID = "GALAXIA";

    @Getter
    private int teamColor = EnumColors.MAP_COLOR_TEAM_ACCENT.getColor();
    private transient boolean dirty;

    private final EnumMap<TeamAction, TeamRole> permissions = new EnumMap<>(TeamAction.class);

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("teamColor", teamColor);

        int size = permissions.size();
        int[] actionOrdinals = new int[size];
        int[] roleOrdinals = new int[size];
        int i = 0;
        for (Map.Entry<TeamAction, TeamRole> entry : permissions.entrySet()) {
            actionOrdinals[i] = entry.getKey()
                .ordinal();
            roleOrdinals[i] = entry.getValue()
                .ordinal();
            i++;
        }
        tag.setIntArray("permActions", actionOrdinals);
        tag.setIntArray("permRoles", roleOrdinals);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        teamColor = tag.hasKey("teamColor") ? tag.getInteger("teamColor")
            : EnumColors.MAP_COLOR_MODAL_HEADER.getColor();

        permissions.clear();
        if (tag.hasKey("permActions")) {
            int[] actions = tag.getIntArray("permActions");
            int[] roles = tag.getIntArray("permRoles");
            int len = Math.min(actions.length, roles.length);
            TeamAction[] actionValues = TeamAction.values();
            TeamRole[] roleValues = TeamRole.values();
            for (int i = 0; i < len; i++) {
                if (actions[i] < 0 || actions[i] >= actionValues.length) continue;
                if (roles[i] < 0 || roles[i] >= roleValues.length) continue;
                permissions.put(actionValues[actions[i]], roleValues[roles[i]]);
            }
        }
    }

    @Override
    public void mergeData(Team consumed, Team surviving, ITeamData oldTeamData) {
        if (oldTeamData instanceof GalaxiaTeamData other) {
            this.teamColor = other.teamColor;
            this.permissions.clear();
            this.permissions.putAll(other.permissions);

            TeamEventHandler.playersToClear.addAll(consumed.getMembers());
            TeamEventHandler.playersToClear.addAll(consumed.getOfficers());
            TeamEventHandler.playersToClear.addAll(consumed.getOwners());
        }
    }

    @Override
    public void copyData(Team oldTeam, Team newTeam, UUID playerId, ITeamData oldTeamData, TeamDataCopyReason reason) {
        if (oldTeamData instanceof GalaxiaTeamData other) {
            this.teamColor = other.teamColor;
            this.permissions.clear();
            this.permissions.putAll(other.permissions);

            TeamEventHandler.playersToClear.addAll(oldTeam.getMembers());
            TeamEventHandler.playersToClear.addAll(oldTeam.getOfficers());
            TeamEventHandler.playersToClear.addAll(oldTeam.getOwners());
        }
    }

    @Override
    public void markSyncedToClient() {
        dirty = false;
    }

    public void setTeamColor(int color) {
        this.teamColor = color;
        dirty = true;
    }

    public TeamRole getRequiredRole(TeamAction action) {
        return permissions.getOrDefault(action, action.getDefaultRole());
    }

    public void setRequiredRole(TeamAction action, TeamRole role) {
        permissions.put(action, role);
        dirty = true;
    }
}
