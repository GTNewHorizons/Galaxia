package com.gtnewhorizons.galaxia.compat.teams;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizon.gtnhlib.teams.INetworkTeamData;
import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;

public class GalaxiaTeamData implements INetworkTeamData {

    public static final String ID = "GALAXIA";

    private final EnumMap<TeamAction, TeamRole> permissions = new EnumMap<>(TeamAction.class);
    private final Set<UUID> mergedTeamIds = new HashSet<>();

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
        NBTTagList merged = new NBTTagList();
        mergedTeamIds.stream()
            .sorted()
            .forEach(id -> merged.appendTag(new NBTTagString(id.toString())));
        tag.setTag("mergedTeamIds", merged);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        permissions.clear();
        mergedTeamIds.clear();
        NBTTagList merged = tag.getTagList("mergedTeamIds", NBT.TAG_STRING);
        for (int i = 0; i < merged.tagCount(); i++) mergedTeamIds.add(UUID.fromString(merged.getStringTagAt(i)));
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
        mergedTeamIds.add(consumed.getTeamId());
        if (oldTeamData instanceof GalaxiaTeamData other) {
            mergedTeamIds.addAll(other.mergedTeamIds);
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

    public boolean hasMergedTeamId(@Nonnull UUID teamId) {
        return mergedTeamIds.contains(teamId);
    }

    public TeamRole getRequiredRole(TeamAction action) {
        return permissions.getOrDefault(action, action.getDefaultRole());
    }

    public void setRequiredRole(TeamAction action, TeamRole role) {
        permissions.put(action, role);
    }
}
