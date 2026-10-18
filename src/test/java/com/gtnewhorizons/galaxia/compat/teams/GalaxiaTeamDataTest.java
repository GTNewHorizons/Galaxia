package com.gtnewhorizons.galaxia.compat.teams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;

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
}
