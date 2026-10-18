package com.gtnewhorizons.galaxia.compat.teams;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GTTeamsCompatTest {

    @BeforeAll
    static void registerNativeTeamData() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        if (!TeamDataRegistry.getRegisteredKeys()
            .contains(GalaxiaTeamData.ID)) {
            TeamDataRegistry.register(GalaxiaTeamData.ID, GalaxiaTeamData::new);
        }
    }

    @AfterEach
    void clearTeams() {
        TeamManager.clear();
    }

    @Test
    void nativeMergeRetainsOwnershipOfOriginalAndTransitivelyConsumedTeamMachinesAfterReload() {
        Team first = TeamManager.createTeam("first", UUID.randomUUID());
        Team second = TeamManager.createTeam("second", UUID.randomUUID());
        Team surviving = TeamManager.createTeam("surviving", UUID.randomUUID());
        TeamManager.mergeTeams(second, first);
        TeamManager.mergeTeams(surviving, second);
        var data = (GalaxiaTeamData) surviving.getData(GalaxiaTeamData.ID);
        var saved = new NBTTagCompound();
        data.writeToNBT(saved);
        var empty = new NBTTagCompound();
        new GalaxiaTeamData().writeToNBT(empty);
        data.readFromNBT(empty);
        assertFalse(GTTeamsCompat.teamOwns(surviving.getTeamId(), first.getTeamId()));

        data.readFromNBT(saved);

        assertTrue(GTTeamsCompat.teamOwns(surviving.getTeamId(), surviving.getTeamId()));
        assertTrue(GTTeamsCompat.teamOwns(surviving.getTeamId(), first.getTeamId()));
        assertTrue(GTTeamsCompat.teamOwns(surviving.getTeamId(), second.getTeamId()));
        assertFalse(GTTeamsCompat.teamOwns(surviving.getTeamId(), UUID.randomUUID()));
        Team departing = TeamManager.createTeam("departing", UUID.randomUUID());
        TeamManager.transferTeamData(
            surviving,
            departing,
            departing.getOwners()
                .iterator()
                .next(),
            TeamDataTransferReason.JoinedNewTeam);
        assertFalse(GTTeamsCompat.teamOwns(departing.getTeamId(), first.getTeamId()));
        assertTrue(GTTeamsCompat.teamOwns(surviving.getTeamId(), first.getTeamId()));
    }

}
