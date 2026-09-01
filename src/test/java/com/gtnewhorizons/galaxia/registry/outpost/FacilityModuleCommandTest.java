package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleCommandTest {

    private static final FacilityCommand.Authority DEBUG_AUTHORITY = new FacilityCommand.Authority(true, true);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void multiBuildCommitsChainedPlacementsWithOneRevision() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        int revisionBefore = facility.getStateRevision();
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord chained = StationTileCoord.of(2, 0);

        FacilityCommand.Result result = facility.applyCommand(
            build(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleTier.HV,
                List.of(ModulePlacement.at(first), ModulePlacement.at(chained))),
            DEBUG_AUTHORITY);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals(revisionBefore + 1, facility.getStateRevision());
        assertEquals(
            List.of(first, chained),
            facility.modules()
                .stream()
                .map(ModuleInstance::anchor)
                .toList());
        assertTrue(
            facility.modules()
                .stream()
                .allMatch(ModuleInstance::isOperational));
    }

    @Test
    void lateInvalidBuildTargetRejectsWithoutAnyAggregateMutation() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_OUTPOST);
        int revisionBefore = facility.getStateRevision();
        boolean dirtyBefore = facility.isDirty();
        Map<?, ?> layoutBefore = Map.copyOf(
            facility.stationLayout()
                .snapshot());
        Map<?, ?> settingsBefore = Map.copyOf(
            facility.settingsGroups()
                .groups());
        Map<?, ?> inventoryBefore = facility.itemSnapshot();

        FacilityCommand.Result result = facility.applyCommand(
            build(
                facility,
                FacilityModuleKind.MINER,
                FacilityModuleKind.MINER.defaultTier(),
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)), ModulePlacement.at(StationTileCoord.of(5, 5)))),
            DEBUG_AUTHORITY);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_PLACEMENT, result.rejection());
        assertTrue(
            facility.modules()
                .isEmpty());
        assertEquals(
            layoutBefore,
            facility.stationLayout()
                .snapshot());
        assertEquals(
            settingsBefore,
            facility.settingsGroups()
                .groups());
        assertEquals(inventoryBefore, facility.itemSnapshot());
        assertEquals(revisionBefore, facility.getStateRevision());
        assertEquals(dirtyBefore, facility.isDirty());
        assertEquals(
            0,
            facility.layoutCache()
                .duplicateCount(FacilityModuleKind.MINER));
    }

    @Test
    void instantBuildWithoutDebugAuthorizationRejectsWithoutAggregateMutation() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        int revisionBefore = facility.getStateRevision();
        boolean dirtyBefore = facility.isDirty();
        Map<?, ?> layoutBefore = Map.copyOf(
            facility.stationLayout()
                .snapshot());
        Map<?, ?> settingsBefore = Map.copyOf(
            facility.settingsGroups()
                .groups());

        FacilityCommand.Result result = facility.applyCommand(
            build(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleTier.HV,
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.DEBUG_AUTHORIZATION_REQUIRED, result.rejection());
        assertTrue(
            facility.modules()
                .isEmpty());
        assertEquals(
            layoutBefore,
            facility.stationLayout()
                .snapshot());
        assertEquals(
            settingsBefore,
            facility.settingsGroups()
                .groups());
        assertEquals(revisionBefore, facility.getStateRevision());
        assertEquals(dirtyBefore, facility.isDirty());
    }

    @Test
    void nullMinerFocusTierIsAnInvalidModuleSpec() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        int revisionBefore = facility.getStateRevision();
        FacilityCommand command = new FacilityCommand.BuildModules(
            facility.assetId,
            FacilityModuleKind.STORAGE,
            FacilityModuleKind.STORAGE.defaultShape(),
            new IModuleComponent.BuildPhysicalSpec.Miner(ModuleTier.HV, null),
            (short) 0,
            false,
            List.of(ModulePlacement.at(StationTileCoord.of(1, 0))));

        FacilityCommand.Result result = facility.applyCommand(command, FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.INVALID_MODULE_SPEC, result.rejection());
        assertTrue(
            facility.modules()
                .isEmpty());
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void copyBuildCopiesPhysicalAndRuntimeSettingsWithoutProgressAndBumpsOnce() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_OUTPOST);
        ModuleInstance source = FacilityModuleKind.MINER
            .create(StationTileCoord.of(5, 5), FacilityModuleKind.MINER.defaultShape(), ModuleTier.LuV);
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        sourceMiner.setFocus(MinerFocusTier.II, "ore:iron", 123);
        source.setTicks(17);
        source.setOperation(waitingOperation());
        facility.addModule(source);
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyBuildModules(
                facility.assetId,
                source.id,
                true,
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
            DEBUG_AUTHORITY);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals(revisionBefore + 1, facility.getStateRevision());
        ModuleInstance copied = facility.modules()
            .get(1);
        assertEquals(source.kind(), copied.kind());
        assertEquals(source.shape(), copied.shape());
        assertEquals(source.tier(), copied.tier());
        assertTrue(facility.isMinerOreBlacklisted(copied, "ore:iron"));
        ModuleMiner copiedMiner = (ModuleMiner) copied.component();
        assertEquals(MinerFocusTier.II, copiedMiner.focusTier());
        assertEquals("ore:iron", copiedMiner.focusOreKeyOrNull());
        assertEquals(0, copiedMiner.focusAlignmentProgress());
        assertEquals(0, copied.ticks());
        assertNull(copied.operationOrNull());
    }

    @Test
    void normalBuildPreservesRequestedInitialSettingsGroupAndPhysicalSpec() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_OUTPOST);
        ModuleInstance source = FacilityModuleKind.MINER
            .create(StationTileCoord.of(5, 5), FacilityModuleKind.MINER.defaultShape(), ModuleTier.EV);
        facility.addModule(source);
        SettingsGroup group = facility.createSettingsGroupForModule(source, "Shared miners");
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.BuildModules(
                facility.assetId,
                FacilityModuleKind.MINER,
                FacilityModuleKind.MINER.defaultShape(),
                new IModuleComponent.BuildPhysicalSpec.Miner(ModuleTier.LuV, MinerFocusTier.II),
                group.id(),
                true,
                List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
            DEBUG_AUTHORITY);

        assertSame(FacilityCommand.Result.CHANGED, result);
        ModuleInstance built = facility.modules()
            .get(1);
        assertEquals(group.id(), built.groupId());
        assertEquals(ModuleTier.LuV, built.tier());
        assertEquals(MinerFocusTier.II, ((ModuleMiner) built.component()).focusTier());
        assertEquals(revisionBefore + 1, facility.getStateRevision());
    }

    @Test
    void deconstructionMapsResultAndAdvancesRevisionOnce() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        ModuleInstance module = addPlaced(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0), true);
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result changed = facility.applyCommand(
            new FacilityCommand.RequestModuleDeconstruction(facility.assetId, module.id),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result missing = facility.applyCommand(
            new FacilityCommand.RequestModuleDeconstruction(facility.assetId, ModuleInstance.ID.create()),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, changed);
        assertEquals(FacilityCommand.Rejection.MODULE_NOT_FOUND, missing.rejection());
        assertFalse(
            facility.modules()
                .contains(module));
        assertEquals(revisionBefore + 1, facility.getStateRevision());
    }

    @Test
    void deconstructionMapsActiveOperationAndCapacityRejectionsWithoutMutation() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        ModuleInstance active = addPlaced(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0), true);
        active.setOperation(waitingOperation());
        ModuleInstance storage = addPlaced(facility, FacilityModuleKind.STORAGE, StationTileCoord.of(2, 0), true);
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(Items.diamond));
        long stored = AutomatedFacility.BASE_ITEM_CAPACITY + 1L;
        assertEquals(stored, facility.insert(filler, stored));
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result activeResult = facility.applyCommand(
            new FacilityCommand.RequestModuleDeconstruction(facility.assetId, active.id),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result capacityResult = facility.applyCommand(
            new FacilityCommand.RequestModuleDeconstruction(facility.assetId, storage.id),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Rejection.MODULE_OPERATION_ACTIVE, activeResult.rejection());
        assertEquals(FacilityCommand.Rejection.CAPACITY_EXCEEDED, capacityResult.rejection());
        assertTrue(
            facility.modules()
                .containsAll(List.of(active, storage)));
        assertEquals(stored, facility.itemAmount(filler));
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void cancelOperationTargetsStableIdAndAdvancesRevisionExactlyOnce() {
        AutomatedFacility facility = facility(CelestialAsset.Kind.AUTOMATED_STATION);
        ModuleInstance untouched = addPlaced(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0), true);
        ModuleInstance target = addPlaced(facility, FacilityModuleKind.POWER, StationTileCoord.of(2, 0), true);
        ModuleOperationState operation = waitingOperation();
        target.setOperation(operation);
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result changed = facility.applyCommand(
            new FacilityCommand.CancelModuleOperation(facility.assetId, target.id),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result unchanged = facility.applyCommand(
            new FacilityCommand.CancelModuleOperation(facility.assetId, untouched.id),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result notCancellable = facility.applyCommand(
            new FacilityCommand.CancelModuleOperation(facility.assetId, target.id),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, changed);
        assertSame(FacilityCommand.Result.UNCHANGED, unchanged);
        assertEquals(FacilityCommand.Rejection.MODULE_OPERATION_NOT_CANCELLABLE, notCancellable.rejection());
        assertSame(ModuleOperationPhase.CANCELLED, operation.phase());
        assertNull(untouched.operationOrNull());
        assertEquals(revisionBefore + 1, facility.getStateRevision());
    }

    private static FacilityCommand.BuildModules build(AutomatedFacility facility, FacilityModuleKind kind,
        ModuleTier tier, List<ModulePlacement> placements) {
        return new FacilityCommand.BuildModules(
            facility.assetId,
            kind,
            kind.defaultShape(),
            physicalSpec(kind, tier),
            (short) 0,
            true,
            placements);
    }

    private static IModuleComponent.BuildPhysicalSpec physicalSpec(FacilityModuleKind kind, ModuleTier tier) {
        if (kind == FacilityModuleKind.HAMMER) {
            return new IModuleComponent.BuildPhysicalSpec.Hammer(tier, HammerVariant.BASE);
        }
        if (kind == FacilityModuleKind.MINER) {
            return new IModuleComponent.BuildPhysicalSpec.Miner(tier, MinerFocusTier.NONE);
        }
        return new IModuleComponent.BuildPhysicalSpec.Tier(tier);
    }

    private static AutomatedFacility facility(CelestialAsset.Kind kind) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            kind,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addPlaced(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord anchor, boolean operational) {
        ModuleInstance module = kind.create(anchor, kind.defaultShape(), kind.defaultTier());
        if (operational) module.completeConstruction();
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }

    private static ModuleOperationState waitingOperation() {
        ModuleOperationPlan plan = new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.HV), 20, Map.of(), true);
        return ModuleOperationState.restore(plan, ModuleOperationPhase.WAITING_FOR_MATERIALS, 0, Map.of(), Map.of());
    }
}
