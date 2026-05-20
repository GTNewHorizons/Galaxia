package com.gtnewhorizons.galaxia.handlers;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamEvents.TeamCreateEvent;
import com.gtnewhorizon.gtnhlib.teams.TeamEvents.TeamMergeEvent;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class TeamEventHandler {

    @SubscribeEvent
    public void onTeamCreate(TeamCreateEvent event) {
        Galaxia.LOG.info("[Teams] Team created: {} ({})", event.team.getTeamName(), event.team.getTeamId());
    }

    @SubscribeEvent
    public void onTeamMerge(TeamMergeEvent event) {
        Team consumed = event.consumed;
        Team surviving = event.surviving;

        Galaxia.LOG.info(
            "[Teams] Merging team {} ({}) into {} ({})",
            consumed.getTeamName(),
            consumed.getTeamId(),
            surviving.getTeamName(),
            surviving.getTeamId());

        CelestialAssetStore.transferTeamAssets(consumed.getTeamId(), surviving.getTeamId());
        CelestialAssetStore.removeTeam(consumed.getTeamId());
    }
}
