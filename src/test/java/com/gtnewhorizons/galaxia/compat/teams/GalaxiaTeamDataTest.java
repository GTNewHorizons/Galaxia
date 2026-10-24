package com.gtnewhorizons.galaxia.compat.teams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GalaxiaTeamDataTest {

    @Test
    void achievementAwardIsIdempotentAndSurvivesNativeTeamPacketAndSave() {
        var data = new GalaxiaTeamData();
        assertFalse(data.hasAchievement(Achievement.STAR_MAP_ACTIVATION));

        assertTrue(data.award(Achievement.STAR_MAP_ACTIVATION));
        assertFalse(data.award(Achievement.STAR_MAP_ACTIVATION));

        var encoded = new NBTTagCompound();
        data.writeToNBT(encoded);
        var restored = new GalaxiaTeamData();
        restored.readFromNBT(encoded);
        assertTrue(restored.hasAchievement(Achievement.STAR_MAP_ACTIVATION));

        var packet = new NBTTagCompound();
        data.toPacketTag(packet, false);
        var client = new GalaxiaTeamData();
        client.fromPacketTag(packet, false);
        assertTrue(client.hasAchievement(Achievement.STAR_MAP_ACTIVATION));
    }

    @Test
    void surveyStartsAfterActivationResumesAfterReloadAndCompletesOnlyOnce() {
        var data = new GalaxiaTeamData();
        assertFalse(data.tickOpeningSurvey(3));
        assertEquals(0, data.getOpeningSurveyTicks());
        data.award(Achievement.STAR_MAP_ACTIVATION);
        assertFalse(data.tickOpeningSurvey(3));
        assertFalse(data.tickOpeningSurvey(3));
        var encoded = new NBTTagCompound();
        data.writeToNBT(encoded);
        var restored = new GalaxiaTeamData();
        restored.readFromNBT(encoded);

        assertEquals(2, restored.getOpeningSurveyTicks());
        assertTrue(restored.tickOpeningSurvey(3));
        assertTrue(restored.isOpeningSurveyComplete());
        assertEquals(3, restored.getOpeningSurveyTicks());
        assertFalse(restored.tickOpeningSurvey(100));
        assertEquals(3, restored.getOpeningSurveyTicks());
        assertTrue(restored.isOpeningSurveyComplete());
        assertThrows(IllegalArgumentException.class, () -> data.tickOpeningSurvey(0));
    }

    @Test
    void mergeUnionsLedgerAndOwnershipHistoryWhileMemberTransferCopiesOnlySpecialBenefits() {
        var originalTeam = team();
        var absorbedTeam = team();
        var destinationTeam = team();
        var departingTeam = team();
        var original = new GalaxiaTeamData();
        original.award(Achievement.STAR_MAP_ACTIVATION);
        original.tickOpeningSurvey(5);
        original.tickOpeningSurvey(5);
        var absorbed = new GalaxiaTeamData();
        absorbed.mergeData(originalTeam, absorbedTeam, original);
        absorbed.tickOpeningSurvey(5);
        var destination = new GalaxiaTeamData();
        destination.award(Achievement.STAR_MAP_ACTIVATION);
        destination.tickOpeningSurvey(5);

        destination.mergeData(absorbedTeam, destinationTeam, absorbed);

        assertTrue(destination.hasAchievement(Achievement.STAR_MAP_ACTIVATION));
        assertEquals(3, destination.getOpeningSurveyTicks());
        assertEquals(
            java.util.Set.of(originalTeam.getTeamId(), absorbedTeam.getTeamId()),
            destination.inheritedTeamIds());
        var departing = new GalaxiaTeamData();
        departing.transferData(
            destinationTeam,
            departingTeam,
            UUID.randomUUID(),
            destination,
            TeamDataTransferReason.JoinedNewTeam);
        assertTrue(Achievement.STAR_MAP_ACTIVATION.copyOnMemberChange());
        assertTrue(departing.hasAchievement(Achievement.STAR_MAP_ACTIVATION));
        assertEquals(3, departing.getOpeningSurveyTicks());
        assertTrue(
            departing.inheritedTeamIds()
                .isEmpty());
        assertTrue(destination.hasAchievement(Achievement.STAR_MAP_ACTIVATION));
        assertEquals(
            2,
            destination.inheritedTeamIds()
                .size());
    }

    @Test
    void joiningExistingTeamKeepsGreaterProgressAndAnyCompletedSurvey() {
        var source = new GalaxiaTeamData();
        source.award(Achievement.STAR_MAP_ACTIVATION);
        source.tickOpeningSurvey(5);
        var existing = new GalaxiaTeamData();
        existing.award(Achievement.STAR_MAP_ACTIVATION);
        existing.tickOpeningSurvey(3);
        existing.tickOpeningSurvey(3);
        existing.tickOpeningSurvey(3);

        existing.transferData(team(), team(), UUID.randomUUID(), source, TeamDataTransferReason.JoinedExistingTeam);

        assertEquals(3, existing.getOpeningSurveyTicks());
        assertTrue(existing.isOpeningSurveyComplete());
        assertFalse(existing.tickOpeningSurvey(100));
        var joining = new GalaxiaTeamData();
        joining.transferData(team(), team(), UUID.randomUUID(), existing, TeamDataTransferReason.JoinedNewTeam);
        assertTrue(joining.isOpeningSurveyComplete());
        assertEquals(3, joining.getOpeningSurveyTicks());
        var merged = new GalaxiaTeamData();
        merged.mergeData(team(), team(), existing);
        assertTrue(merged.isOpeningSurveyComplete());
        assertEquals(3, merged.getOpeningSurveyTicks());
    }

    @Test
    void savedLedgerUsesStableAchievementIdsAndRejectsUnknownContent() {
        var data = new GalaxiaTeamData();
        data.award(Achievement.STAR_MAP_ACTIVATION);
        var saved = new NBTTagCompound();
        data.writeToNBT(saved);
        assertEquals(
            "star_map_activation",
            saved.getTagList("achievements", NBT.TAG_STRING)
                .getStringTagAt(0));
        assertThrows(
            UnsupportedOperationException.class,
            () -> data.achievements()
                .clear());
        var unknown = new NBTTagList();
        unknown.appendTag(new NBTTagString("unregistered"));
        saved.setTag("achievements", unknown);

        assertThrows(IllegalStateException.class, () -> new GalaxiaTeamData().readFromNBT(saved));
    }

    private static Team team() {
        return new Team("test", UUID.randomUUID(), false);
    }

    @BeforeAll
    static void initTeams() {
        GalaxiaTestBootstrap.ensureMinecraft();
        if (!TeamDataRegistry.getRegisteredKeys()
            .contains(GalaxiaTeamData.ID)) {
            TeamDataRegistry.register(GalaxiaTeamData.ID, GalaxiaTeamData::new);
        }
    }

    @AfterEach
    void clearTeams() {
        TeamManager.clear();
        TeamEventHandler.playersToClear.clear();
    }

    /**
     * Integration contract: merged station ownership persists across reload and later merges, without following
     * players.
     */
    @Test
    void mergedOwnershipSurvivesReloadAndFurtherMergesWithoutFollowingPlayers() {
        Team first = team("first", 1);
        Team second = team("second", 2);
        Team third = team("third", 3);
        Team destination = team("destination", 4);
        Team destinationAncestor = team("destination ancestor", 5);
        GalaxiaTeamData secondData = new GalaxiaTeamData();
        secondData.mergeData(first, second, new GalaxiaTeamData());
        assertTrue(secondData.hasMergedTeamId(first.getTeamId()));
        NBTTagCompound saved = new NBTTagCompound();
        secondData.writeToNBT(saved);
        GalaxiaTeamData reloaded = new GalaxiaTeamData();
        reloaded.readFromNBT(saved);
        assertTrue(reloaded.hasMergedTeamId(first.getTeamId()));

        GalaxiaTeamData thirdData = new GalaxiaTeamData();
        thirdData.mergeData(second, third, reloaded);

        assertTrue(thirdData.hasMergedTeamId(first.getTeamId()));
        assertTrue(thirdData.hasMergedTeamId(second.getTeamId()));
        GalaxiaTeamData destinationData = new GalaxiaTeamData();
        destinationData.mergeData(destinationAncestor, destination, new GalaxiaTeamData());
        destinationData.transferData(
            third,
            destination,
            new UUID(100L, 101L),
            thirdData,
            TeamDataTransferReason.JoinedExistingTeam);
        assertFalse(destinationData.hasMergedTeamId(first.getTeamId()));
        assertFalse(destinationData.hasMergedTeamId(second.getTeamId()));
        assertFalse(destinationData.hasMergedTeamId(third.getTeamId()));
        assertTrue(destinationData.hasMergedTeamId(destinationAncestor.getTeamId()));
    }

    /** Integration contract: adding Android control cannot reinterpret the existing permission ordinal encoding. */
    @Test
    void existingPermissionIdsStillDecodeTheirOriginalActions() {
        List<TeamAction> existingActions = List.of(
            TeamAction.CREATE_ASSET,
            TeamAction.BUILD_MODULE,
            TeamAction.MODIFY_MODULE,
            TeamAction.CONFIGURE_LOGISTICS,
            TeamAction.MANAGE_INVENTORY,
            TeamAction.RENAME_ASSET,
            TeamAction.DESTROY_ASSET,
            TeamAction.DECONSTRUCT_ASSET);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setIntArray("permActions", new int[] { 0, 1, 2, 3, 4, 5, 6, 7 });
        saved.setIntArray("permRoles", new int[existingActions.size()]);
        GalaxiaTeamData data = new GalaxiaTeamData();

        data.readFromNBT(saved);

        for (TeamAction action : existingActions) assertEquals(TeamRole.MEMBER, data.getRequiredRole(action));
        assertEquals(TeamRole.MEMBER, data.getRequiredRole(TeamAction.CONTROL_ANDROID));
    }

    /** Integration contract: GTNH Teams removes consumed IDs, while physical ownership resolves to the survivor. */
    @Test
    void removedTeamIdentityResolvesToItsActiveSurvivor() {
        Team first = TeamManager.createTeam("first", new UUID(200L, 201L));
        Team second = TeamManager.createTeam("second", new UUID(202L, 203L));
        Team third = TeamManager.createTeam("third", new UUID(204L, 205L));
        UUID original = first.getTeamId();

        TeamManager.mergeTeams(second, first);
        TeamManager.mergeTeams(third, second);

        assertNull(TeamManager.getTeamById(original));
        assertEquals(third.getTeamId(), GTTeamsCompat.resolveTeamId(original));
        assertEquals(third.getTeamId(), GTTeamsCompat.resolveTeamId(second.getTeamId()));
        assertEquals(third.getTeamId(), GTTeamsCompat.resolveTeamId(third.getTeamId()));
        assertNull(GTTeamsCompat.resolveTeamId(new UUID(300L, 301L)));
    }

    private static Team team(String name, long id) {
        return new Team(name, new UUID(0L, id), false);
    }
}
