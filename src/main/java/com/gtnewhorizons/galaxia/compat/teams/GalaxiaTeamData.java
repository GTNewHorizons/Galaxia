package com.gtnewhorizons.galaxia.compat.teams;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.gtnewhorizon.gtnhlib.teams.INetworkTeamData;
import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;

import lombok.Getter;

public class GalaxiaTeamData implements INetworkTeamData {

    public static final String ID = "GALAXIA";

    @Getter
    private int teamColor = EnumColors.MAP_COLOR_TEAM_ACCENT.getColor();

    private final EnumMap<TeamAction, TeamRole> permissions = new EnumMap<>(TeamAction.class);
    private final EnumSet<Achievement> achievements = EnumSet.noneOf(Achievement.class);
    private final Set<UUID> inheritedTeamIds = new HashSet<>();
    private long openingSurveyTicks;
    private boolean openingSurveyComplete;

    public enum Achievement {

        STAR_MAP_ACTIVATION("star_map_activation", "galaxia.achievement.star_map_activation", true);

        private final String id;
        private final String translationKey;
        private final boolean copyOnMemberChange;

        Achievement(String id, String translationKey, boolean copyOnMemberChange) {
            this.id = id;
            this.translationKey = translationKey;
            this.copyOnMemberChange = copyOnMemberChange;
        }

        @Nonnull
        public String id() {
            return id;
        }

        @Nonnull
        public String translationKey() {
            return translationKey;
        }

        public boolean copyOnMemberChange() {
            return copyOnMemberChange;
        }

        private static Achievement fromId(String id) {
            for (Achievement achievement : values()) {
                if (achievement.id.equals(id)) return achievement;
            }
            throw new IllegalStateException("Unknown Galaxia achievement ID " + id);
        }
    }

    public boolean hasAchievement(@Nonnull Achievement achievement) {
        return achievements.contains(achievement);
    }

    public boolean award(@Nonnull Achievement achievement) {
        return achievements.add(achievement);
    }

    @Nonnull
    public Set<Achievement> achievements() {
        return Set.copyOf(achievements);
    }

    @Nonnull
    public Set<UUID> inheritedTeamIds() {
        return Set.copyOf(inheritedTeamIds);
    }

    public long getOpeningSurveyTicks() {
        return openingSurveyTicks;
    }

    public boolean isOpeningSurveyComplete() {
        return openingSurveyComplete;
    }

    public boolean tickOpeningSurvey(long requiredTicks) {
        if (requiredTicks <= 0) throw new IllegalArgumentException("Opening survey duration must be positive");
        if (openingSurveyComplete || !hasAchievement(Achievement.STAR_MAP_ACTIVATION)) return false;
        if (openingSurveyTicks < requiredTicks) openingSurveyTicks++;
        if (openingSurveyTicks < requiredTicks) return false;
        openingSurveyComplete = true;
        return true;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("teamColor", teamColor);
        var encodedAchievements = new NBTTagList();
        for (Achievement achievement : achievements) encodedAchievements.appendTag(new NBTTagString(achievement.id()));
        tag.setTag("achievements", encodedAchievements);
        tag.setLong("openingSurveyTicks", openingSurveyTicks);
        tag.setBoolean("openingSurveyComplete", openingSurveyComplete);
        var inherited = new NBTTagList();
        inheritedTeamIds.stream()
            .sorted()
            .forEach(id -> inherited.appendTag(new NBTTagString(id.toString())));
        tag.setTag("inheritedTeamIds", inherited);

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
        var in = new NbtReader(tag, "galaxiaTeamData");
        achievements.clear();
        var encodedAchievements = in.strings("achievements");
        for (int i = 0; i < encodedAchievements.tagCount(); i++)
            achievements.add(Achievement.fromId(encodedAchievements.getStringTagAt(i)));
        openingSurveyTicks = in.longValue("openingSurveyTicks");
        if (openingSurveyTicks < 0) throw new IllegalStateException("Opening survey progress must not be negative");
        openingSurveyComplete = in.bool("openingSurveyComplete");
        inheritedTeamIds.clear();
        var inherited = in.strings("inheritedTeamIds");
        for (int i = 0; i < inherited.tagCount(); i++)
            inheritedTeamIds.add(UUID.fromString(inherited.getStringTagAt(i)));
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
        inheritedTeamIds.add(consumed.getTeamId());
        if (oldTeamData instanceof GalaxiaTeamData other) {
            achievements.addAll(other.achievements);
            openingSurveyTicks = Math.max(openingSurveyTicks, other.openingSurveyTicks);
            openingSurveyComplete |= other.openingSurveyComplete;
            inheritedTeamIds.addAll(other.inheritedTeamIds);
            this.teamColor = other.teamColor;
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
            for (Achievement achievement : other.achievements) {
                if (achievement.copyOnMemberChange()) achievements.add(achievement);
            }
            openingSurveyTicks = Math.max(openingSurveyTicks, other.openingSurveyTicks);
            openingSurveyComplete |= other.openingSurveyComplete;
            this.teamColor = other.teamColor;
            this.permissions.clear();
            this.permissions.putAll(other.permissions);

            TeamEventHandler.playersToClear.addAll(prevTeam.getMembers());
            TeamEventHandler.playersToClear.addAll(prevTeam.getOfficers());
            TeamEventHandler.playersToClear.addAll(prevTeam.getOwners());
        }
    }

    @Override
    public void markSyncedToClient() {}

    public void setTeamColor(int color) {
        this.teamColor = color;
    }

    public TeamRole getRequiredRole(TeamAction action) {
        return permissions.getOrDefault(action, action.getDefaultRole());
    }

    public void setRequiredRole(TeamAction action, TeamRole role) {
        permissions.put(action, role);
    }
}
