package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityCommandGatewayTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    private RecordingTransport transport;
    private FacilityCommandGateway gateway;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void setUp() {
        CelestialAssetStore.SERVER.clearInternal();
        transport = new RecordingTransport();
        gateway = new FacilityCommandGateway(new AssetStateSync.Server(transport));
    }

    @AfterEach
    void cleanUp() {
        CelestialAssetStore.SERVER.clearInternal();
    }

    @Test
    void foreignAndMissingFacilitiesReturnTheSameHiddenRejection() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FacilityCommandGateway.Actor actor = actor(
            OTHER_TEAM,
            TeamAction.MANAGE_INVENTORY,
            TeamAction.CONFIGURE_LOGISTICS);

        FacilityCommand.Result foreign = gateway
            .execute(actor, new FacilityCommand.ClearInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, item));
        FacilityCommand.Result missing = gateway.execute(
            actor,
            new FacilityCommand.ClearInventoryBound(CelestialAsset.ID.create(), BoundKind.ITEM_LOWER, item));

        assertEquals(FacilityCommand.Status.REJECTED, foreign.status());
        assertEquals(FacilityCommand.Rejection.FACILITY_NOT_FOUND, foreign.rejection());
        assertEquals(foreign, missing);
        assertEquals(0, transport.deliveryCount);
    }

    @Test
    void missingMappedPermissionRejectsBeforeMutation() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));

        FacilityCommand.Result result = gateway.execute(
            actor(TEAM, TeamAction.CONFIGURE_LOGISTICS),
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, item, 3L));

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.NOT_AUTHORIZED, result.rejection());
        assertEquals(0, facility.getStateRevision());
        assertEquals(0, transport.deliveryCount);
    }

    @Test
    void changedCommandPublishesExactlyOnceWhileNoOpAndRejectionDoNotPublish() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FacilityCommand command = new FacilityCommand.SetInventoryBound(
            facility.assetId,
            BoundKind.ITEM_LOWER,
            item,
            3L);

        FacilityCommand.Result changed = gateway.execute(actor(TEAM, TeamAction.MANAGE_INVENTORY), command);
        FacilityCommand.Result unchanged = gateway.execute(actor(TEAM, TeamAction.MANAGE_INVENTORY), command);
        FacilityCommand.Result rejected = gateway.execute(actor(TEAM, TeamAction.CONFIGURE_LOGISTICS), command);

        assertEquals(FacilityCommand.Status.CHANGED, changed.status());
        assertEquals(FacilityCommand.Status.UNCHANGED, unchanged.status());
        assertEquals(FacilityCommand.Status.REJECTED, rejected.status());
        assertEquals(1, transport.deliveryCount);
    }

    @Test
    void inventoryInsertRequiresServerDerivedCreativeAuthorityAfterPermissionCheck() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FacilityCommand command = new FacilityCommand.AdjustInventory(
            facility.assetId,
            item,
            FacilityCommand.InventoryAdjustment.INSERT,
            3L);

        FacilityCommand.Result unauthorized = gateway.execute(actor(TEAM), command);
        FacilityCommand.Result nonCreative = gateway.execute(actor(TEAM, TeamAction.MANAGE_INVENTORY), command);
        FacilityCommand.Result creative = gateway
            .execute(actor(TEAM, new FacilityCommand.Authority(true, false), TeamAction.MANAGE_INVENTORY), command);

        assertEquals(FacilityCommand.Rejection.NOT_AUTHORIZED, unauthorized.rejection());
        assertEquals(FacilityCommand.Rejection.CREATIVE_MODE_REQUIRED, nonCreative.rejection());
        assertEquals(FacilityCommand.Status.CHANGED, creative.status());
        assertEquals(3L, facility.itemAmount(item));
        assertEquals(1, transport.deliveryCount);
    }

    @Test
    void acceptedMinerBlacklistCommandsApplyInServerOrderAndIdenticalStateIsUnchanged() {
        AutomatedFacility facility = facility();
        ModuleInstance miner = addMiner(facility, 1);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        long initialRevision = facility.getStateRevision();
        FacilityCommandGateway.Actor actor = actor(TEAM, TeamAction.MODIFY_MODULE);

        FacilityCommand.Result blacklisted = gateway
            .execute(actor, new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, miner.id, "ore:iron", true));
        FacilityCommand.Result allowed = gateway
            .execute(actor, new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, miner.id, "ore:iron", false));
        FacilityCommand.Result repeated = gateway
            .execute(actor, new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, miner.id, "ore:iron", false));

        assertEquals(FacilityCommand.Status.CHANGED, blacklisted.status());
        assertEquals(FacilityCommand.Status.CHANGED, allowed.status());
        assertEquals(FacilityCommand.Status.UNCHANGED, repeated.status());
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertEquals(initialRevision + 2, facility.getStateRevision());
        assertEquals(2, transport.deliveryCount);
    }

    @Test
    void minerBlacklistCommandPropagatesThroughSharedSettingsWithOneRevision() {
        AutomatedFacility facility = facility();
        ModuleInstance first = addMiner(facility, 1);
        ModuleInstance second = addMiner(facility, 2);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            facility
                .applyCommand(
                    new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "Shared miners"),
                    FacilityCommand.Authority.NONE)
                .status());
        SettingsGroup.ID groupId = facility.moduleSettingsSnapshot()
            .membership()
            .get(first.id);
        assertNotNull(groupId);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            facility
                .applyCommand(
                    new FacilityCommand.JoinSettingsGroup(facility.assetId, second.id, groupId),
                    FacilityCommand.Authority.NONE)
                .status());
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        long initialRevision = facility.getStateRevision();

        FacilityCommand.Result result = gateway.execute(
            actor(TEAM, TeamAction.MODIFY_MODULE),
            new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, first.id, "ore:copper", true));

        assertEquals(FacilityCommand.Status.CHANGED, result.status());
        assertTrue(facility.isMinerOreBlacklisted(first, "ore:copper"));
        assertTrue(facility.isMinerOreBlacklisted(second, "ore:copper"));
        assertEquals(initialRevision + 1, facility.getStateRevision());
        assertEquals(1, transport.deliveryCount);
    }

    @Test
    void everyCommandCategoryRequiresItsMappedTeamAction() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        ModuleInstance.ID missingModule = ModuleInstance.ID.create();

        assertMapped(
            facility,
            TeamAction.BUILD_MODULE,
            new FacilityCommand.BuildModules(
                facility.assetId,
                FacilityModuleKind.POWER,
                ModuleShape.SINGLE,
                new IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.NONE),
                null,
                false,
                null));
        assertMapped(
            facility,
            TeamAction.BUILD_MODULE,
            new FacilityCommand.CopyBuildModules(facility.assetId, missingModule, false, null));

        List<FacilityCommand> moduleCommands = List.of(
            new FacilityCommand.RequestModuleDeconstruction(facility.assetId, missingModule),
            new FacilityCommand.CancelModuleOperation(facility.assetId, missingModule),
            new FacilityCommand.CreateSettingsGroup(facility.assetId, missingModule, "Shared settings"),
            new FacilityCommand.RenameSettingsGroup(facility.assetId, new SettingsGroup.ID(1), "Priority settings"),
            new FacilityCommand.JoinSettingsGroup(facility.assetId, missingModule, new SettingsGroup.ID(1)),
            new FacilityCommand.LeaveSettingsGroup(facility.assetId, missingModule),
            new FacilityCommand.CopyModuleSettings(facility.assetId, missingModule, List.of(missingModule)),
            new FacilityCommand.ReplaceRecipeBook(
                facility.assetId,
                new RecipeBookOwner.Private(missingModule),
                RecipeBook.empty()),
            new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, missingModule, "ore:iron", true),
            new FacilityCommand.SetHammerShootingConfig(facility.assetId, missingModule, null),
            new FacilityCommand.SetHammerRoutePriority(facility.assetId, missingModule, null),
            new FacilityCommand.SetMinerFocusOre(facility.assetId, missingModule, "ore:iron"),
            new FacilityCommand.ConfigureDebugDataGenerator(facility.assetId, missingModule, null),
            new FacilityCommand.PlanHammerUpgrade(
                facility.assetId,
                List.of(missingModule),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                false,
                false),
            new FacilityCommand.PlanTierUpgrade(facility.assetId, List.of(missingModule), ModuleTier.IV, false),
            new FacilityCommand.PlanMinerFocusUpgrade(
                facility.assetId,
                missingModule,
                ModuleTier.EV,
                MinerFocusTier.I));
        moduleCommands.forEach(command -> assertMapped(facility, TeamAction.MODIFY_MODULE, command));

        assertMapped(
            facility,
            TeamAction.MANAGE_INVENTORY,
            new FacilityCommand.AdjustInventory(
                facility.assetId,
                item,
                FacilityCommand.InventoryAdjustment.EXTRACT,
                1L));
        assertMapped(
            facility,
            TeamAction.MANAGE_INVENTORY,
            new FacilityCommand.ClearInventoryResource(facility.assetId, item));
        assertMapped(
            facility,
            TeamAction.MANAGE_INVENTORY,
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, item, 3L));
        assertMapped(
            facility,
            TeamAction.MANAGE_INVENTORY,
            new FacilityCommand.ClearInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, item));
        assertMapped(
            facility,
            TeamAction.CONFIGURE_LOGISTICS,
            new FacilityCommand.ReplaceFilters(facility.assetId, FacilityCommand.FilterKind.ITEM, List.of("stick")));
        assertMapped(
            facility,
            TeamAction.CONFIGURE_LOGISTICS,
            new FacilityCommand.PutLogisticsConfig(facility.assetId, item, null, null));
        assertMapped(
            facility,
            TeamAction.CONFIGURE_LOGISTICS,
            new FacilityCommand.RemoveLogisticsConfig(facility.assetId, item));
    }

    private void assertMapped(AutomatedFacility facility, TeamAction expectedAction, FacilityCommand command) {
        FacilityCommand.Result denied = gateway.execute(actor(TEAM), command);
        FacilityCommand.Result allowed = gateway.execute(actor(TEAM, expectedAction), command);

        assertEquals(FacilityCommand.Rejection.NOT_AUTHORIZED, denied.rejection());
        assertNotEquals(FacilityCommand.Rejection.NOT_AUTHORIZED, allowed.rejection());
        assertEquals(facility.assetId, command.facilityId());
    }

    private static FacilityCommandGateway.Actor actor(UUID teamId, TeamAction... permissions) {
        return actor(teamId, FacilityCommand.Authority.NONE, permissions);
    }

    private static FacilityCommandGateway.Actor actor(UUID teamId, FacilityCommand.Authority authority,
        TeamAction... permissions) {
        return new FacilityCommandGateway.Actor(teamId, Set.of(permissions), authority);
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addMiner(AutomatedFacility facility, int x) {
        ModuleInstance miner = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.of(x, 0),
            FacilityModuleKind.MINER.defaultShape(),
            ModuleTier.EV);
        facility.addModule(miner);
        return miner;
    }

    private static final class RecordingTransport implements AssetStateSync.ServerTransport {

        private int deliveryCount;

        @Override
        public Collection<UUID> eligibleRecipients(UUID teamId) {
            return List.of(RECIPIENT);
        }

        @Override
        public void send(UUID recipientId, AssetStateFramePacket packet) {
            deliveryCount++;
        }
    }
}
