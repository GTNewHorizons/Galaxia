package com.gtnewhorizons.galaxia.compat.teams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizons.galaxia.handlers.TeamEventHandler;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GalaxiaTeamDataTest {

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
