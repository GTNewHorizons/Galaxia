package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AutomatedFacilityDeconstructionTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void acceptedRemovalReturnsFullCurrentTierConstructionCost() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addModule(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        Map<ItemStackWrapper, Long> expected = constructionCost(module);
        int revisionBefore = facility.getStateRevision();

        assertSame(AutomatedFacility.DeconstructionResult.ACCEPTED, facility.requestModuleDeconstruction(module.id));

        assertFalse(
            facility.modules()
                .contains(module));
        assertEquals(revisionBefore + 1, facility.getStateRevision());
        for (Map.Entry<ItemStackWrapper, Long> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), facility.itemAmount(entry.getKey()));
        }
    }

    @Test
    void storageRejectionPreservesIdentityInventoryAndRevision() {
        AutomatedFacility facility = facility();
        ModuleInstance storage = addModule(facility, FacilityModuleKind.STORAGE, StationTileCoord.of(1, 0));
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(Items.diamond));
        long stored = AutomatedFacility.BASE_ITEM_CAPACITY + 1L;
        assertEquals(stored, facility.insert(filler, stored));
        int revisionBefore = facility.getStateRevision();

        assertSame(
            AutomatedFacility.DeconstructionResult.CAPACITY_EXCEEDED,
            facility.requestModuleDeconstruction(storage.id));

        assertTrue(
            facility.modules()
                .contains(storage));
        assertSame(
            storage,
            facility.stationLayout()
                .moduleAt(storage.anchor()));
        assertEquals(stored, facility.itemAmount(filler));
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void activeOperationRejectionPreservesOperationState() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addModule(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        ModuleOperationPlan plan = new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.HV), 20, Map.of(), true);
        ModuleOperationState operation = ModuleOperationState.restore(
            plan,
            com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase.REFUNDING,
            0,
            Map.of("minecraft:iron_ingot:0", 3L),
            Map.of("minecraft:gold_ingot:0", 2L));
        module.setOperation(operation);
        int revisionBefore = facility.getStateRevision();

        assertSame(
            AutomatedFacility.DeconstructionResult.ACTIVE_OPERATION,
            facility.requestModuleDeconstruction(module.id));

        assertSame(operation, module.operationOrNull());
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void pendingRefundCompletesAfterCapacityIsFreed() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addModule(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        long refundAmount = constructionCost(module).values()
            .stream()
            .mapToLong(Long::longValue)
            .sum();
        ItemStackWrapper filler = ItemStackWrapper.of(new ItemStack(Items.diamond));
        assertEquals(
            AutomatedFacility.BASE_ITEM_CAPACITY,
            facility.insert(filler, AutomatedFacility.BASE_ITEM_CAPACITY));

        assertSame(AutomatedFacility.DeconstructionResult.ACCEPTED, facility.requestModuleDeconstruction(module.id));
        assertTrue(
            facility.modules()
                .contains(module));
        assertEquals(Buildable.Status.DECONSTRUCTION, module.status());
        assertTrue(
            module.operationOrNull()
                .refundBuffer()
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum() > 0L);

        int revisionWithoutSpace = facility.getStateRevision();
        facility.tick();
        assertEquals(revisionWithoutSpace, facility.getStateRevision());

        facility.extract(filler, 1L);
        int revisionBeforeProgress = facility.getStateRevision();
        facility.tick();
        assertTrue(
            facility.modules()
                .contains(module));
        assertEquals(revisionBeforeProgress + 1, facility.getStateRevision());

        facility.extract(filler, refundAmount - 1L);
        int revisionBeforeTick = facility.getStateRevision();
        facility.tick();

        assertFalse(
            facility.modules()
                .contains(module));
        assertEquals(revisionBeforeTick + 1, facility.getStateRevision());
        assertEquals(AutomatedFacility.BASE_ITEM_CAPACITY, facility.storedItemAmount());
    }

    @Test
    void returnedItemsBypassInsertionFilter() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addModule(facility, FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        ItemStackWrapper allowed = ItemStackWrapper.of(new ItemStack(Items.diamond));
        facility.setFilters(List.of(allowed.toKey()), true);
        Map<ItemStackWrapper, Long> refund = constructionCost(module);

        assertSame(AutomatedFacility.DeconstructionResult.ACCEPTED, facility.requestModuleDeconstruction(module.id));

        assertFalse(
            facility.modules()
                .contains(module));
        for (Map.Entry<ItemStackWrapper, Long> entry : refund.entrySet()) {
            assertEquals(entry.getValue(), facility.itemAmount(entry.getKey()));
        }
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addModule(AutomatedFacility facility, FacilityModuleKind kind,
        StationTileCoord anchor) {
        ModuleInstance module = kind.create(anchor, ModuleShape.SINGLE, kind.defaultTier());
        module.completeConstruction();
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }

    private static Map<ItemStackWrapper, Long> constructionCost(ModuleInstance module) {
        Map<ItemStackWrapper, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : module.getConstructionCost()
            .entrySet()) {
            cost.merge(ItemStackWrapper.of(entry.getKey()), entry.getValue(), Math::addExact);
        }
        return cost;
    }
}
