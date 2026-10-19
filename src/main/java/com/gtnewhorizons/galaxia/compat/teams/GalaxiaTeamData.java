package com.gtnewhorizons.galaxia.compat.teams;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.teams.INetworkTeamData;
import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;

public class GalaxiaTeamData implements INetworkTeamData {

    public static final String ID = "GALAXIA";

    private final EnumMap<TeamAction, TeamRole> permissions = new EnumMap<>(TeamAction.class);

    @Override
    public void writeToNBT(NBTTagCompound tag) {
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
            this.permissions.clear();
            this.permissions.putAll(other.permissions);

            TeamEventHandler.playersToClear.addAll(consumed.getMembers());
            TeamEventHandler.playersToClear.addAll(consumed.getOfficers());
            TeamEventHandler.playersToClear.addAll(consumed.getOwners());
        }
    }

    @Override
    public void transferData(Team prevTeam, Team newTeam, UUID playerId, ITeamData prevTeamData,
        TeamDataTransferReason reason) {
        if (prevTeamData instanceof GalaxiaTeamData other) {
            this.permissions.clear();
            this.permissions.putAll(other.permissions);

            TeamEventHandler.playersToClear.addAll(prevTeam.getMembers());
            TeamEventHandler.playersToClear.addAll(prevTeam.getOfficers());
            TeamEventHandler.playersToClear.addAll(prevTeam.getOwners());
        }
    }

    @Override
    public void markSyncedToClient() {}

    public TeamRole getRequiredRole(TeamAction action) {
        return permissions.getOrDefault(action, action.getDefaultRole());
    }

    public void setRequiredRole(TeamAction action, TeamRole role) {
        permissions.put(action, role);
    }
}
