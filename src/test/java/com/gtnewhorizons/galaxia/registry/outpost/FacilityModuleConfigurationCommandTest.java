package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleConfigurationCommandTest {

    private static final FacilityCommand.Authority CREATIVE = new FacilityCommand.Authority(true, true);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void completeHammerConfigChangesAtomicallyOnceAndRejectsWrongComponent() {
        AutomatedFacility facility = facility();
        ModuleInstance hammer = add(facility, FacilityModuleKind.HAMMER);
        ModuleInstance power = add(facility, FacilityModuleKind.POWER);
        ModuleHammer hammerComponent = (ModuleHammer) hammer.component();
        AllowShootingConfig config = new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 2.5);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.ConfigureHammer(
                    facility.assetId,
                    hammer.id,
                    config,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.UNCHANGED,
            facility.applyCommand(
                new FacilityCommand.ConfigureHammer(
                    facility.assetId,
                    hammer.id,
                    config,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV),
                FacilityCommand.Authority.NONE));
        FacilityCommand.Result wrong = facility.applyCommand(
            new FacilityCommand.ConfigureHammer(
                facility.assetId,
                power.id,
                config,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV),
            FacilityCommand.Authority.NONE);
        AllowShootingConfig rejectedConfig = new AllowShootingConfig(AllowShootingConfig.Mode.ALWAYS, 0);
        FacilityCommand.Result invalid = facility.applyCommand(
            new FacilityCommand.ConfigureHammer(facility.assetId, hammer.id, rejectedConfig, null),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_COMPONENT, wrong.rejection());
        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_CONFIG, invalid.rejection());
        assertEquals(config, hammerComponent.config());
        assertEquals(OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV, hammerComponent.routePriority());
    }

    @Test
    void minerFocusOreChangeResetsAlignmentAndClearUsesNullIntent() {
        AutomatedFacility facility = facility();
        ModuleInstance module = add(facility, FacilityModuleKind.MINER);
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 15);

        FacilityCommand.Result changed = facility.applyCommand(
            new FacilityCommand.SetMinerFocusOre(facility.assetId, module.id, "ore:copper"),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result unchanged = facility.applyCommand(
            new FacilityCommand.SetMinerFocusOre(facility.assetId, module.id, "ore:copper"),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result blankRejected = facility.applyCommand(
            new FacilityCommand.SetMinerFocusOre(facility.assetId, module.id, " "),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result cleared = facility.applyCommand(
            new FacilityCommand.SetMinerFocusOre(facility.assetId, module.id, null),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, changed);
        assertSame(FacilityCommand.Result.UNCHANGED, unchanged);
        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_CONFIG, blankRejected.rejection());
        assertSame(FacilityCommand.Result.CHANGED, cleared);
        assertNull(miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void debugConfigRequiresAuthorizationAndChangesOnce() {
        AutomatedFacility facility = facility();
        ModuleInstance module = add(facility, FacilityModuleKind.DEBUG_DATA_GENERATOR);
        ModuleDebugDataGenerator.Config config = ModuleDebugDataGenerator.Config
            .produce(SatelliteDataType.PROSPECTING, 50L, 10);

        FacilityCommand.Result rejected = facility.applyCommand(
            new FacilityCommand.ConfigureDebugDataGenerator(facility.assetId, module.id, config),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result changed = facility.applyCommand(
            new FacilityCommand.ConfigureDebugDataGenerator(facility.assetId, module.id, config),
            CREATIVE);
        FacilityCommand.Result unchanged = facility.applyCommand(
            new FacilityCommand.ConfigureDebugDataGenerator(facility.assetId, module.id, config),
            CREATIVE);

        assertEquals(FacilityCommand.Rejection.DEBUG_AUTHORIZATION_REQUIRED, rejected.rejection());
        assertSame(FacilityCommand.Result.CHANGED, changed);
        assertSame(FacilityCommand.Result.UNCHANGED, unchanged);
    }

    @Test
    void creativeHammerBatchCommitsAfterFullPreflight() {
        AutomatedFacility facility = facility();
        ModuleInstance first = add(facility, FacilityModuleKind.HAMMER);
        ModuleInstance second = add(facility, FacilityModuleKind.HAMMER);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.PlanHammerUpgrade(
                facility.assetId,
                List.of(first.id, second.id),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                false,
                false),
            CREATIVE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertTrue(
            facility.modules()
                .stream()
                .allMatch(m -> m.tier() == ModuleTier.ZPM));
        assertTrue(
            facility.modules()
                .stream()
                .allMatch(m -> ((ModuleHammer) m.component()).variant() == HammerVariant.BIG));
    }

    @Test
    void creativeUpgradeRejectsAnyActiveOperationBeforeMutatingTheBatch() {
        AutomatedFacility facility = facility();
        ModuleInstance first = add(facility, FacilityModuleKind.HAMMER);
        ModuleInstance active = add(facility, FacilityModuleKind.HAMMER);
        ModuleOperationState activeOperation = waitingOperation(active);
        active.setOperation(activeOperation);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.PlanHammerUpgrade(
                facility.assetId,
                List.of(first.id, active.id),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                false,
                false),
            CREATIVE);

        assertEquals(FacilityCommand.Rejection.MODULE_OPERATION_ACTIVE, result.rejection());
        assertNull(first.operationOrNull());
        assertSame(activeOperation, active.operationOrNull());
    }

    @Test
    void tierUpgradeRejectsTargetsWithDifferentModuleKinds() {
        AutomatedFacility facility = facility();
        ModuleInstance storage = add(facility, FacilityModuleKind.STORAGE);
        ModuleInstance tank = add(facility, FacilityModuleKind.TANK);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.PlanTierUpgrade(facility.assetId, List.of(storage.id, tank.id), ModuleTier.EV, false),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_TARGETS, result.rejection());
        assertNull(storage.operationOrNull());
        assertNull(tank.operationOrNull());
    }

    @Test
    void laterActiveHammerTargetAndDuplicateIdsRejectWholeBatch() {
        AutomatedFacility facility = facility();
        ModuleInstance first = add(facility, FacilityModuleKind.HAMMER);
        ModuleInstance active = add(facility, FacilityModuleKind.HAMMER);
        active.setOperation(waitingOperation(active));

        FacilityCommand.Result activeResult = facility.applyCommand(
            new FacilityCommand.PlanHammerUpgrade(
                facility.assetId,
                List.of(first.id, active.id),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                false,
                false),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result duplicate = facility.applyCommand(
            new FacilityCommand.PlanHammerUpgrade(
                facility.assetId,
                List.of(first.id, first.id),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                false,
                false),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Rejection.MODULE_OPERATION_ACTIVE, activeResult.rejection());
        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_TARGETS, duplicate.rejection());
        assertNull(first.operationOrNull());
    }

    @Test
    void batchReservationIsAtomicForSuccessAndInsufficientMaterials() {
        AutomatedFacility success = facility();
        ModuleInstance first = add(success, FacilityModuleKind.HAMMER);
        ModuleInstance second = add(success, FacilityModuleKind.HAMMER);
        Map<ItemStackWrapper, Long> aggregate = aggregateTargetCost(first, 2, ModuleTier.IV);
        ensureItemCapacity(success, aggregate);
        aggregate.forEach(success::insert);
        FacilityCommand command = new FacilityCommand.PlanHammerUpgrade(
            success.assetId,
            List.of(first.id, second.id),
            HammerVariant.BASE,
            ModuleTier.IV,
            true,
            false);

        FacilityCommand.Result changed = success.applyCommand(command, FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, changed);
        assertTrue(
            success.itemSnapshot()
                .isEmpty());
        assertTrue(first.operationOrNull() != null && second.operationOrNull() != null);

        AutomatedFacility insufficient = facility();
        ModuleInstance insufficientFirst = add(insufficient, FacilityModuleKind.HAMMER);
        ModuleInstance insufficientSecond = add(insufficient, FacilityModuleKind.HAMMER);
        Map<ItemStackWrapper, Long> insufficientItems = aggregateTargetCost(insufficientFirst, 2, ModuleTier.IV);
        ensureItemCapacity(insufficient, insufficientItems);
        ItemStackWrapper oneKey = insufficientItems.keySet()
            .iterator()
            .next();
        insufficientItems.put(oneKey, insufficientItems.get(oneKey) - 1L);
        insufficientItems.forEach(insufficient::insert);
        Map<ItemStackWrapper, Long> before = insufficient.itemSnapshot();

        FacilityCommand.Result rejected = insufficient.applyCommand(
            new FacilityCommand.PlanHammerUpgrade(
                insufficient.assetId,
                List.of(insufficientFirst.id, insufficientSecond.id),
                HammerVariant.BASE,
                ModuleTier.IV,
                true,
                false),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Rejection.INSUFFICIENT_MODULE_MATERIALS, rejected.rejection());
        assertEquals(before, insufficient.itemSnapshot());
        assertNull(insufficientFirst.operationOrNull());
        assertNull(insufficientSecond.operationOrNull());
    }

    @Test
    void genericTierAndMinerFocusPlansUseStableDomainOperations() {
        AutomatedFacility facility = facility();
        ModuleInstance storage = add(facility, FacilityModuleKind.STORAGE);
        ModuleInstance miner = add(facility, FacilityModuleKind.MINER);
        ModuleTier storageTarget = storage.nextTier();

        FacilityCommand.Result tier = facility.applyCommand(
            new FacilityCommand.PlanTierUpgrade(facility.assetId, List.of(storage.id), storageTarget, false),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result focus = facility.applyCommand(
            new FacilityCommand.PlanMinerFocusUpgrade(facility.assetId, miner.id, miner.tier(), MinerFocusTier.I),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, tier);
        assertSame(FacilityCommand.Result.CHANGED, focus);
        assertSame(
            ModuleOperationPhase.WAITING_FOR_MATERIALS,
            storage.operationOrNull()
                .phase());
        assertSame(
            ModuleOperationPhase.WAITING_FOR_MATERIALS,
            miner.operationOrNull()
                .phase());
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance add(AutomatedFacility facility, FacilityModuleKind kind) {
        int index = facility.modules()
            .size();
        byte x = (byte) (-28 + (index % 15) * 4);
        byte y = (byte) (-28 + (index / 15) * 4);
        ModuleInstance module = kind.create(new StationTileCoord(x, y), kind.defaultShape(), kind.defaultTier());
        module.completeConstruction();
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }

    private static void ensureItemCapacity(AutomatedFacility facility, Map<ItemStackWrapper, Long> items) {
        long required = items.values()
            .stream()
            .mapToLong(Long::longValue)
            .sum();
        while (facility.itemCapacity() < required) {
            add(facility, FacilityModuleKind.STORAGE);
        }
    }

    private static ModuleOperationState waitingOperation(ModuleInstance module) {
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new IModuleOperation.Tier(module.nextTier()),
            20,
            Map.of(),
            false);
        return ModuleOperationState.waiting(plan);
    }

    private static Map<ItemStackWrapper, Long> aggregateTargetCost(ModuleInstance module, int count,
        ModuleTier targetTier) {
        ModuleTierData target = FacilityModuleRegistry.get(module.kind())
            .getTierData(targetTier);
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(target.constructionCost());
        Map<ItemStackWrapper, Long> aggregate = new LinkedHashMap<>();
        cost.forEach((item, amount) -> aggregate.put(item, Math.multiplyExact(amount, count)));
        return aggregate;
    }
}
