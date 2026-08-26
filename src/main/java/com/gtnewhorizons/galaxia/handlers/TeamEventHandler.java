package com.gtnewhorizons.galaxia.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamEvents.TeamCreateEvent;
import com.gtnewhorizon.gtnhlib.teams.TeamEvents.TeamMergeEvent;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class TeamEventHandler {

    public static final Set<UUID> playersToClear = new HashSet<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            AssetStateSync.SERVER.resetRecipient(player.getUniqueID());
        }
    }

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
