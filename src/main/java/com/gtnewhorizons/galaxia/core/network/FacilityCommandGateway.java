package com.gtnewhorizons.galaxia.core.network;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;

public final class FacilityCommandGateway {

    private static final Logger LOG = LogManager.getLogger(FacilityCommandGateway.class);
    private static final String REJECTION_TRANSLATION_KEY = "galaxia.facility.command.rejected";
    private static final Set<TeamAction> FACILITY_ACTIONS = Set.of(
        TeamAction.BUILD_MODULE,
        TeamAction.MODIFY_MODULE,
        TeamAction.MANAGE_INVENTORY,
        TeamAction.CONFIGURE_LOGISTICS);

    private final AssetStateSync.Server stateSync;

    public FacilityCommandGateway() {
        this(AssetStateSync.SERVER);
    }

    FacilityCommandGateway(AssetStateSync.Server stateSync) {
        this.stateSync = stateSync;
    }

    public FacilityCommand.Result execute(EntityPlayerMP player, FacilityCommand command) {
        Actor actor = normalize(player);
        FacilityCommand.Result result = execute(actor, command);
        if (result.status() == FacilityCommand.Status.REJECTED && player != null) {
            player.addChatMessage(new ChatComponentTranslation(REJECTION_TRANSLATION_KEY));
        }
        return result;
    }

    FacilityCommand.Result execute(Actor actor, FacilityCommand command) {
        if (actor == null || command == null || command.facilityId() == null) {
            return rejected(command, FacilityCommand.Rejection.MALFORMED_COMMAND);
        }
        if (!hasPermission(actor, command)) {
            return rejected(command, FacilityCommand.Rejection.NOT_AUTHORIZED);
        }
        AutomatedFacility facility = findOwnedFacility(actor.teamId(), command.facilityId());
        if (facility == null) {
            return rejected(command, FacilityCommand.Rejection.FACILITY_NOT_FOUND);
        }

        FacilityCommand.Result result = facility.applyCommand(command, actor.authority());
        if (result.status() == FacilityCommand.Status.CHANGED) {
            stateSync.publishInteractive(command.facilityId());
        } else if (result.status() == FacilityCommand.Status.REJECTED) {
            logRejection(command, result.rejection());
        }
        return result;
    }

    private static AutomatedFacility findOwnedFacility(UUID teamId, CelestialAsset.ID facilityId) {
        if (teamId == null || !CelestialAssetStore.SERVER.isOwnedByInternal(teamId, facilityId)) return null;
        CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(facilityId);
        return asset instanceof AutomatedFacility facility ? facility : null;
    }

    private static Actor normalize(EntityPlayerMP player) {
        if (player == null) return null;
        UUID teamId = GTTeamsCompat.getTeamData(player)
            .map(team -> team.getTeamId())
            .orElse(null);
        EnumSet<TeamAction> permissions = EnumSet.noneOf(TeamAction.class);
        for (TeamAction action : FACILITY_ACTIONS) {
            if (GTTeamsCompat.hasPermission(player, action)) permissions.add(action);
        }
        return new Actor(
            teamId,
            permissions,
            new FacilityCommand.Authority(
                player.capabilities.isCreativeMode,
                DebugActionAuthorization.isAuthorized(player)));
    }

    private static boolean hasPermission(Actor actor, FacilityCommand command) {
        TeamAction requiredAction = requiredAction(command);
        return requiredAction != null && actor.permissions()
            .contains(requiredAction);
    }

    private static TeamAction requiredAction(FacilityCommand command) {
        if (command instanceof FacilityCommand.BuildModules || command instanceof FacilityCommand.CopyBuildModules) {
            return TeamAction.BUILD_MODULE;
        }
        if (command instanceof FacilityCommand.RequestModuleDeconstruction
            || command instanceof FacilityCommand.CancelModuleOperation
            || command instanceof FacilityCommand.ModuleConfiguration
            || command instanceof FacilityCommand.ModuleSettingsCommand
            || command instanceof FacilityCommand.ModuleOperationRequest) {
            return TeamAction.MODIFY_MODULE;
        }
        if (command instanceof FacilityCommand.AdjustInventory
            || command instanceof FacilityCommand.ClearInventoryResource
            || command instanceof FacilityCommand.SetInventoryBound
            || command instanceof FacilityCommand.ClearInventoryBound) {
            return TeamAction.MANAGE_INVENTORY;
        }
        if (command instanceof FacilityCommand.ReplaceFilters || command instanceof FacilityCommand.PutLogisticsConfig
            || command instanceof FacilityCommand.RemoveLogisticsConfig) {
            return TeamAction.CONFIGURE_LOGISTICS;
        }
        return null;
    }

    private static FacilityCommand.Result rejected(FacilityCommand command, FacilityCommand.Rejection rejection) {
        logRejection(command, rejection);
        return FacilityCommand.Result.rejected(rejection);
    }

    private static void logRejection(FacilityCommand command, FacilityCommand.Rejection rejection) {
        String commandType = command == null ? "unknown"
            : command.getClass()
                .getSimpleName();
        LOG.warn("Facility command {} rejected: {}", commandType, rejection);
    }

    record Actor(UUID teamId, Set<TeamAction> permissions, FacilityCommand.Authority authority) {

        Actor {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            authority = authority == null ? FacilityCommand.Authority.NONE : authority;
        }
    }
}
