package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifiers;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AutomatedFacilityOperationTest {

    private static Item TEST_FILLER_ITEM;
    private static Item TEST_REFUND_ITEM;

    @BeforeAll
    static void initRegistries() throws ReflectiveOperationException {
        GalaxiaTestBootstrap.ensureFacilityModules();
        TEST_FILLER_ITEM = Items.diamond;
        TEST_REFUND_ITEM = Items.iron_ingot;
    }

    @Test
    void reserveOperationMaterialsMovesItemsFromInventoryToDeposit() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.updateItems(key, 8);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertTrue(facility.tryReserveOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(3L, facility.getItemAmount(key));
        assertEquals(
            5L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));
    }

    @Test
    void reserveOperationMaterialsIsAtomicWhenInventoryIsShort() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.updateItems(key, 2);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertFalse(facility.tryReserveOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(2L, facility.getItemAmount(key));
        assertTrue(
            module.operationOrNull()
                .depositedResources()
                .isEmpty());
    }

    @Test
    void reserveAvailableOperationMaterialsCollectsPartialDeposits() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.updateItems(key, 2);
        module.setOperation(ModuleOperationState.waiting(plan()));

        assertFalse(facility.tryReserveAvailableOperationMaterials(module, Map.of(key, 5L)));

        assertEquals(0L, facility.getItemAmount(key));
        assertEquals(
            2L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));

        facility.updateItems(key, 3);

        assertTrue(facility.tryReserveAvailableOperationMaterials(module, Map.of(key, 5L)));
        assertEquals(
            5L,
            module.operationOrNull()
                .depositedResources()
                .get(key.toKey()));
    }

    @Test
    void cancelQueuesFullDepositIntoRefundBuffer() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStack material = material();
        ItemStackWrapper key = ItemStackWrapper.of(material);
        facility.updateItems(key, 8);
        module.setOperation(ModuleOperationState.waiting(plan()));
        facility.tryReserveOperationMaterials(module, Map.of(key, 5L));

        facility.cancelModuleOperation(module);

        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());
        assertEquals(3L, facility.getItemAmount(key));
        assertEquals(
            5L,
            module.operationOrNull()
                .refundBuffer()
                .get(key.toKey()));
    }

    @Test
    void itemInventoryCapacityStartsAtBaseLimit() {
        AutomatedFacility facility = facilityWithHammer();

        assertEquals(1000L, facility.totalItemCapacity());
        assertEquals(1000L, facility.remainingItemInventoryCapacity());
    }

    @Test
    void storageModulesIncreaseItemInventoryCapacity() {
        AutomatedFacility facility = facilityWithStorage();
        ModuleInstance storage = facility.modules()
            .get(0);
        facility.stationLayout()
            .place(storage);

        assertEquals(2024L, facility.totalItemCapacity());
    }

    @Test
    void insertInventoryAcceptsOnlyRemainingCapacity() {
        AutomatedFacility facility = facilityWithHammer();
        ItemStackWrapper key = ItemStackWrapper.of(new ItemStack(Items.diamond));

        assertEquals(1000L, facility.updateItems(key, 1200));

        assertEquals(1000L, facility.getItemAmount(key));
        assertEquals(0L, facility.remainingItemInventoryCapacity());
    }

    @Test
    void refundFlushKeepsRemainderWhenInventoryIsFull() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.updateItems(filler, 998);
        module.setOperation(
            ModuleOperationState
                .restore(plan(), ModuleOperationPhase.REFUNDING, 0, Map.of(), Map.of(refund.toKey(), 5L)));

        assertTrue(facility.flushModuleOperationRefund(module));

        assertEquals(2L, facility.getItemAmount(refund));
        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());
        assertEquals(
            3L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));
    }

    @Test
    void refundingModuleBlocksNextUpgradeUntilBufferEmpties() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.updateItems(filler, 1000);
        module.setOperation(
            ModuleOperationState
                .restore(plan(), ModuleOperationPhase.REFUNDING, 0, Map.of(), Map.of(refund.toKey(), 1L)));

        facility.tick();

        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());

        facility.updateItems(filler, -1);
        facility.tick();

        assertEquals(
            ModuleOperationPhase.CANCELLED,
            module.operationOrNull()
                .phase());
        assertEquals(1L, facility.getItemAmount(refund));
    }

    @Test
    void completedRefundTricklesThroughRefundBufferWhenInventoryHasLimitedSpace() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(TEST_FILLER_ITEM));
        ItemStackWrapper refund = ItemStackWrapper.of(new ItemStack(TEST_REFUND_ITEM));
        facility.updateItems(filler, 999);
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, refund))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertEquals(ModuleTier.LuV, module.tier());
        assertEquals(
            ModuleOperationPhase.REFUNDING,
            module.operationOrNull()
                .phase());

        facility.tick();

        assertEquals(1L, facility.getItemAmount(refund));
        assertEquals(
            5L,
            module.operationOrNull()
                .refundBuffer()
                .get(refund.toKey()));
    }

    @Test
    void flushModuleOperationRefundCrashesOnUnresolvableItemKey() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper key = new ItemStackWrapper(new Item(), 0, null);
        facility.updateItems(key, 8);
        module.setOperation(ModuleOperationState.waiting(plan()));
        facility.tryReserveOperationMaterials(module, Map.of(key, 5L));
        facility.cancelModuleOperation(module);

        assertThrows(IllegalStateException.class, () -> facility.flushModuleOperationRefund(module));
    }

    @Test
    void reserveOperationMaterialsRejectsMalformedState() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);

        assertThrows(
            IllegalStateException.class,
            () -> facility.tryReserveOperationMaterials(module, Map.of(ItemStackWrapper.of(material()), 1L)));

        module.setOperation(
            ModuleOperationState.waiting(plan())
                .beginBuilding());

        assertThrows(
            IllegalStateException.class,
            () -> facility.tryReserveOperationMaterials(module, Map.of(ItemStackWrapper.of(material()), 1L)));
    }

    @Test
    void tickCompletesHammerUpgradeAndAppliesTargetSpec() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, true))
                .beginBuilding());

        assertEquals(ModuleTier.EV, module.tier());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.LuV, module.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) module.component()).variant());
    }

    @Test
    void completedHammerUpgradeRefundUsesReplacedTierCost() {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper material = ItemStackWrapper.of(material());
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, material))
                .beginBuilding());

        facility.tick();
        facility.tick();

        ModuleOperationState operation = module.operationOrNull();
        assertNotNull(operation);
        assertEquals(ModuleOperationPhase.REFUNDING, operation.phase());
        assertEquals(
            6L,
            operation.refundBuffer()
                .get(material.toKey()));
    }

    @Test
    void completedHammerUpgradeRefundUsesPlanBuildTicks() throws Exception {
        AutomatedFacility facility = facilityWithHammer();
        ModuleInstance module = facility.modules()
            .get(0);
        ItemStackWrapper material = ItemStackWrapper.of(material());
        module.setOperation(
            ModuleOperationState.waiting(hammerUpgradePlan(2, false, material))
                .beginBuilding());

        facility.tick();
        facility.tick();

        ModuleOperationState operation = module.operationOrNull();
        assertNotNull(operation);
        Method isCompletionRefund = AutomatedFacility.class
            .getDeclaredMethod("isCompletionRefund", ModuleOperationState.class);
        isCompletionRefund.setAccessible(true);
        assertTrue((boolean) isCompletionRefund.invoke(null, operation));
    }

    @Test
    void tickCompletesMinerFocusTierOperationAndResetsAlignment() {
        AutomatedFacility facility = facilityWithMiner();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);
        module.setOperation(
            ModuleOperationState.waiting(minerFocusPlan(2))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertEquals("ore:iron", miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void tickCompletesMinerFocusTierOperationWithoutSelectedOre() {
        AutomatedFacility facility = facilityWithMiner();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        module.setOperation(
            ModuleOperationState
                .waiting(
                    new ModuleOperationPlan(
                        new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), null),
                        2,
                        Map.of(),
                        false,
                        true))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertNull(miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void tickCompletesGenericTierOperation() {
        AutomatedFacility facility = facilityWithStorage();
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(tierOperationPlan(2, ModuleTier.EV))
                .beginBuilding());

        facility.tick();
        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.EV, module.tier());
    }

    @Test
    void regolithFlatsSpeedUpBuildOperations() {
        AutomatedFacility facility = facilityWithStorageOnFeature(
            PlanetaryFeatureRegistry.REGOLITH_FLATS.key(),
            PlanetaryFeatureRegistry.STABLE_BEDROCK.key());
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(tierOperationPlan(6, ModuleTier.EV))
                .beginBuilding());

        tickFacility(facility, 5);

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.EV, module.tier());
        assertTrue(facility.buildSpeedModifierPercent(module) > 0);
    }

    @Test
    void stableBedrockSlowsBuildOperationsAndReducesUpkeep() {
        AutomatedFacility facility = facilityWithStorageOnFeature(
            PlanetaryFeatureRegistry.STABLE_BEDROCK.key(),
            PlanetaryFeatureRegistry.REGOLITH_FLATS.key());
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.waiting(tierOperationPlan(5, ModuleTier.EV))
                .beginBuilding());

        tickFacility(facility, 5);

        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleOperationPhase.BUILDING,
            module.operationOrNull()
                .phase());
        assertTrue(
            module.operationOrNull()
                .elapsedBuildTicks() < 5);
        assertTrue(facility.buildSpeedModifierPercent(module) < 0);
        assertTrue(facility.upkeepReductionPercent(module) > 0);

        facility.tick();

        assertNull(module.operationOrNull());
        assertEquals(ModuleTier.EV, module.tier());
    }

    @Test
    void featureModifiersAreCachedAndInvalidatedWithFeatureSalt() {
        AutomatedFacility facility = facilityWithStorageOnFeature(
            PlanetaryFeatureRegistry.REGOLITH_FLATS.key(),
            PlanetaryFeatureRegistry.STABLE_BEDROCK.key());
        ModuleInstance module = facility.modules()
            .get(0);

        ModuleFeatureModifiers modifiers = facility.featureModifiers(module);

        assertTrue(modifiers.buildSpeedModifierPercent() > 0);

        useNeutralBuildFeatureSalt(facility, module.anchor());

        assertEquals(
            0,
            facility.featureModifiers(module)
                .buildSpeedModifierPercent());
    }

    private static AutomatedFacility facilityWithHammer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        StationTileCoord coord = StationTileCoord.of(1, 0);
        useNeutralBuildFeatureSalt(facility, coord);
        ModuleInstance module = FacilityModuleKind.HAMMER.create(coord, ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        return facility;
    }

    private static AutomatedFacility facilityWithMiner() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        StationTileCoord coord = StationTileCoord.of(1, 0);
        useNeutralBuildFeatureSalt(facility, coord);
        ModuleInstance module = FacilityModuleKind.MINER.create(coord, ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        return facility;
    }

    private static AutomatedFacility facilityWithStorage() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        StationTileCoord coord = StationTileCoord.of(1, 0);
        useNeutralBuildFeatureSalt(facility, coord);
        ModuleInstance module = FacilityModuleKind.STORAGE.create(coord, ModuleShape.SINGLE, ModuleTier.HV);
        facility.addModule(module);
        return facility;
    }

    private static void useNeutralBuildFeatureSalt(AutomatedFacility facility, StationTileCoord coord) {
        for (long salt = 0; salt < 10_000L; salt++) {
            facility.setStationFeatureSalt(salt);
            List<PlanetaryFeatureKey> features = facility.planetaryFeaturesAt(coord);
            if (!features.contains(PlanetaryFeatureRegistry.REGOLITH_FLATS.key())
                && !features.contains(PlanetaryFeatureRegistry.STABLE_BEDROCK.key())) {
                return;
            }
        }
        throw new AssertionError("Could not find neutral station salt");
    }

    private static AutomatedFacility facilityWithStorageOnFeature(PlanetaryFeatureKey required,
        PlanetaryFeatureKey excluded) {
        for (long salt = 0; salt < 10_000L; salt++) {
            AutomatedFacility facility = new AutomatedFacility(
                CelestialAsset.ID.create(),
                CelestialObjectId.MARS,
                CelestialAsset.Kind.AUTOMATED_STATION,
                Buildable.Status.OPERATIONAL);
            facility.setStationFeatureSalt(salt);
            for (int dx = StationTileCoord.MIN; dx <= StationTileCoord.MAX; dx++) {
                for (int dy = StationTileCoord.MIN; dy <= StationTileCoord.MAX; dy++) {
                    StationTileCoord coord = StationTileCoord.of(dx, dy);
                    List<PlanetaryFeatureKey> features = facility.planetaryFeaturesAt(coord);
                    if (!features.contains(required) || features.contains(excluded)) continue;
                    ModuleInstance module = FacilityModuleKind.STORAGE.create(coord, ModuleShape.SINGLE, ModuleTier.HV);
                    facility.addModule(module);
                    return facility;
                }
            }
        }
        throw new AssertionError("Could not find station salt for feature " + required);
    }

    private static void tickFacility(AutomatedFacility facility, int ticks) {
        for (int i = 0; i < ticks; i++) {
            facility.tick();
        }
    }

    private static ModuleOperationPlan plan() {
        return hammerUpgradePlan(200);
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks) {
        return hammerUpgradePlan(buildTicks, false);
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks, boolean voidCompletionRefund) {
        return hammerUpgradePlan(buildTicks, voidCompletionRefund, ItemStackWrapper.of(material()));
    }

    private static ModuleOperationPlan hammerUpgradePlan(int buildTicks, boolean voidCompletionRefund,
        ItemStackWrapper material) {
        return new ModuleOperationPlan(
            new HammerModuleOperation(ModuleTier.LuV, HammerVariant.BIG.name()),
            buildTicks,
            Map.of(material, 128L),
            Map.of(material, 8L),
            80,
            false,
            voidCompletionRefund);
    }

    private static ModuleOperationPlan minerFocusPlan(int buildTicks) {
        return new ModuleOperationPlan(
            new MinerFocusOperation(ModuleTier.EV, MinerFocusTier.II.name(), "ore:iron"),
            buildTicks,
            Map.of(),
            false,
            true);
    }

    private static ModuleOperationPlan tierOperationPlan(int buildTicks, ModuleTier targetTier) {
        return new ModuleOperationPlan(new ModuleTierOperation(targetTier), buildTicks, Map.of(), false, true);
    }

    private static ItemStack material() {
        return new ItemStack(Items.diamond);
    }
}
