package com.gtnewhorizons.galaxia.core.network;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapId;
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
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.gtnewhorizons.galaxia.testing.TestGTRecipes;

import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

final class FacilityCommandGatewayTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();
    private static GTRecipe catalogRecipe;
    private static int catalogIndex;

    private RecordingTransport transport;
    private FacilityCommandGateway gateway;

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        // Harness bootstrap for GT's recipe builder outside the Minecraft launcher.
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
            Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        }
        var mapId = GTRecipeMapId.MACERATOR;
        var map = GTRecipeMapId.findRecipeMap(mapId);
        if (map == null) {
            map = RecipeMapBuilder.of(mapId.getRecipeMapUnlocalizedName())
                .maxIO(1, 1, 0, 0)
                .build();
        }
        catalogRecipe = TestGTRecipes.recipe(
            new ItemStack[] { new ItemStack(Items.stick) },
            new ItemStack[] { new ItemStack(Items.coal) },
            new FluidStack[0],
            new FluidStack[0],
            null,
            20,
            1);
        map.addRecipe(catalogRecipe);
        catalogIndex = Arrays.asList(GTRecipeMapId.getRecipes(mapId))
            .indexOf(catalogRecipe);
        assertTrue(catalogIndex >= 0);
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

    // Security regression: configuration permission does not authorize client-authored production recipes.
    @Test
    void rejectsClientAuthoredRecipeDespiteConsistentContentHash() {
        AutomatedFacility facility = facility();
        ModuleInstance module = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        addModule(facility, module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        var forged = RecipeSnapshot.resolved(
            (byte) GTRecipeMapId.MACERATOR.ordinal(),
            catalogIndex,
            new ItemStack[] { new ItemStack(Items.stick) },
            new ItemStack[] { new ItemStack(Items.nether_star, 64) },
            null,
            null,
            20,
            1);
        RecipeBook book = new RecipeBook(
            List.of(new SavedRecipe(forged, true, 0, (byte) 0, (byte) 1)),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP);

        FacilityCommand.Result result = gateway.execute(
            actor(TEAM, TeamAction.MODIFY_MODULE),
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, module.id, book));

        assertEquals(FacilityCommand.Rejection.INVALID_RECIPE_BOOK, result.rejection());
        assertEquals(RecipeBook.empty(), facility.recipeBook(module));
        assertEquals(0, transport.deliveryCount);
    }

    // Integration contract: catalog validation accepts a visible recipe and rejects changed content or map identity.
    @Test
    void recipeSelectionMustMatchTheModulesVisibleServerCatalog() {
        AutomatedFacility facility = facility();
        ModuleInstance module = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.HV);
        addModule(facility, module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        var mapId = GTRecipeMapId.MACERATOR;
        var canonical = mapId.snapshot(catalogIndex, catalogRecipe);
        RecipeBook book = new RecipeBook(
            List.of(new SavedRecipe(canonical, true, 12, (byte) 0, (byte) 1)),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP);
        var command = new FacilityCommand.ReplaceRecipeBook(facility.assetId, module.id, book);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway.execute(actor(TEAM, TeamAction.MODIFY_MODULE), command)
                .status());
        assertEquals(book, facility.recipeBook(module));
        try {
            catalogRecipe.mHidden = true;
            assertEquals(
                FacilityCommand.Rejection.INVALID_RECIPE_BOOK,
                gateway.execute(actor(TEAM, TeamAction.MODIFY_MODULE), command)
                    .rejection());
        } finally {
            catalogRecipe.mHidden = false;
        }
        var otherMap = GTRecipeMapId.CENTRIFUGE.snapshot(catalogIndex, catalogRecipe);
        var wrongBook = new RecipeBook(
            List.of(new SavedRecipe(otherMap, true, 0, (byte) 0, (byte) 1)),
            book.mode(),
            book.notDoablePolicy());
        assertEquals(
            FacilityCommand.Rejection.INVALID_RECIPE_BOOK,
            gateway
                .execute(
                    actor(TEAM, TeamAction.MODIFY_MODULE),
                    new FacilityCommand.ReplaceRecipeBook(facility.assetId, module.id, wrongBook))
                .rejection());
        assertEquals(book, facility.recipeBook(module));
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
    void independentFilterEditsComposeWithoutClientRefresh() {
        AutomatedFacility facility = facility();
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        FacilityCommandGateway.Actor actor = actor(TEAM, TeamAction.CONFIGURE_LOGISTICS);
        FacilityCommand first = new FacilityCommand.SetFilter(
            facility.assetId,
            FacilityCommand.FilterKind.ITEM,
            "stick",
            true);
        FacilityCommand second = new FacilityCommand.SetFilter(
            facility.assetId,
            FacilityCommand.FilterKind.ITEM,
            "diamond",
            true);

        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway.execute(actor, first)
                .status());
        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway.execute(actor, second)
                .status());
        assertEquals(List.of("stick", "diamond"), facility.filtersSnapshot(true));
        assertEquals(
            FacilityCommand.Status.UNCHANGED,
            gateway.execute(actor, second)
                .status());
        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway
                .execute(
                    actor,
                    new FacilityCommand.SetFilter(facility.assetId, FacilityCommand.FilterKind.ITEM, "stick", false))
                .status());
        assertEquals(List.of("diamond"), facility.filtersSnapshot(true));
    }

    @Test
    void independentBlacklistEditsComposeWithoutClientRefresh() {
        AutomatedFacility facility = facility();
        ModuleInstance miner = addMiner(facility, 1);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        FacilityCommandGateway.Actor actor = actor(TEAM, TeamAction.MODIFY_MODULE);
        FacilityCommand first = new FacilityCommand.SetMinerOreBlacklisted(
            facility.assetId,
            miner.id,
            "ore:iron",
            true);
        FacilityCommand second = new FacilityCommand.SetMinerOreBlacklisted(
            facility.assetId,
            miner.id,
            "ore:gold",
            true);

        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway.execute(actor, first)
                .status());
        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway.execute(actor, second)
                .status());
        assertEquals(
            Set.of("ore:iron", "ore:gold"),
            facility.minerSettings(miner)
                .blacklistedOreKeys());
        assertEquals(
            FacilityCommand.Status.UNCHANGED,
            gateway.execute(actor, second)
                .status());
        assertEquals(
            FacilityCommand.Status.CHANGED,
            gateway
                .execute(
                    actor,
                    new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, miner.id, "ore:iron", false))
                .status());
        assertEquals(
            Set.of("ore:gold"),
            facility.minerSettings(miner)
                .blacklistedOreKeys());
    }

    @Test
    void acceptedMinerBlacklistCommandsApplyInServerOrderAndIdenticalStateIsUnchanged() {
        AutomatedFacility facility = facility();
        ModuleInstance miner = addMiner(facility, 1);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        FacilityCommandGateway.Actor actor = actor(TEAM, TeamAction.MODIFY_MODULE);

        FacilityCommand.Result blacklisted = gateway.execute(
            actor,
            new FacilityCommand.ReplaceMinerSettings(
                facility.assetId,
                miner.id,
                new MinerSettings(Set.of("ore:iron"))));
        FacilityCommand.ReplaceMinerSettings allowAll = new FacilityCommand.ReplaceMinerSettings(
            facility.assetId,
            miner.id,
            new MinerSettings());
        FacilityCommand.Result allowed = gateway.execute(actor, allowAll);
        FacilityCommand.Result repeated = gateway.execute(actor, allowAll);

        assertEquals(FacilityCommand.Status.CHANGED, blacklisted.status());
        assertEquals(FacilityCommand.Status.CHANGED, allowed.status());
        assertEquals(FacilityCommand.Status.UNCHANGED, repeated.status());
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertEquals(2, transport.deliveryCount);
    }

    @Test
    void minerBlacklistCommandPropagatesThroughSharedSettings() {
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
        SettingsGroup.ID groupId = ((ModuleInstance.SettingsBinding.Shared) first.settingsBinding()).groupId();
        assertNotNull(groupId);
        assertEquals(
            FacilityCommand.Status.CHANGED,
            facility
                .applyCommand(
                    new FacilityCommand.SetSettingsGroup(facility.assetId, second.id, groupId),
                    FacilityCommand.Authority.NONE)
                .status());
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);

        FacilityCommand.Result result = gateway.execute(
            actor(TEAM, TeamAction.MODIFY_MODULE),
            new FacilityCommand.ReplaceMinerSettings(
                facility.assetId,
                first.id,
                new MinerSettings(Set.of("ore:copper"))));

        assertEquals(FacilityCommand.Status.CHANGED, result.status());
        assertTrue(facility.isMinerOreBlacklisted(first, "ore:copper"));
        assertTrue(facility.isMinerOreBlacklisted(second, "ore:copper"));
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
            new FacilityCommand.SetSettingsGroup(facility.assetId, missingModule, new SettingsGroup.ID(1)),
            new FacilityCommand.CopyModuleSettings(facility.assetId, missingModule, List.of(missingModule)),
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, missingModule, RecipeBook.empty()),
            new FacilityCommand.ReplaceMinerSettings(facility.assetId, missingModule, new MinerSettings()),
            new FacilityCommand.ConfigureHammer(facility.assetId, missingModule, null, null),
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
            new FacilityCommand.SetFilter(facility.assetId, FacilityCommand.FilterKind.ITEM, "stick", true));
        assertMapped(
            facility,
            TeamAction.MODIFY_MODULE,
            new FacilityCommand.SetMinerOreBlacklisted(facility.assetId, missingModule, "ore:iron", true));
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
        addModule(facility, miner);
        return miner;
    }

    private static final class RecordingTransport implements AssetStateSync.ServerTransport {

        private int deliveryCount;

        @Override
        public Collection<UUID> eligibleRecipients(UUID teamId) {
            return List.of(RECIPIENT);
        }

        @Override
        public void send(UUID recipientId, List<? extends cpw.mods.fml.common.network.simpleimpl.IMessage> frames) {
            deliveryCount += frames.size();
        }
    }
}
