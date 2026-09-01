package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityCommandTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void boundCommandsApplyChangesAndRejectNoOps() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));

        FacilityCommand.Result changed = facility.applyCommand(
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_UPPER, item, 10L),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result unchanged = facility.applyCommand(
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_UPPER, item, 10L),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result rejected = facility.applyCommand(
            new FacilityCommand.SetInventoryBound(facility.assetId, BoundKind.ITEM_LOWER, item, 20L),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.CHANGED, changed.status());
        assertEquals(FacilityCommand.Status.UNCHANGED, unchanged.status());
        assertEquals(FacilityCommand.Status.REJECTED, rejected.status());
        assertEquals(FacilityCommand.Rejection.INVALID_BOUND, rejected.rejection());
        assertEquals(InventoryBounds.upperBound(10L), facility.getBound(item));
    }

    @Test
    void facilityIdMismatchIsRejectedWithoutMutation() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ClearInventoryBound(CelestialAsset.ID.create(), BoundKind.ITEM_LOWER, item),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.FACILITY_ID_MISMATCH, result.rejection());
        assertTrue(
            facility.getBound(item)
                .isInvalid());
    }

    @Test
    void logisticsCommandChangesTheNextSignalSnapshotAndIdenticalCommandIsANoOp() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        LogisticsResourceConfig config = new LogisticsResourceConfig(8, 4, true, false);
        FacilityCommand command = new FacilityCommand.PutLogisticsConfig(
            facility.assetId,
            item,
            config,
            LogisticsConfigAccessMode.FULL);

        FacilityCommand.Result changed = facility.applyCommand(command, FacilityCommand.Authority.NONE);
        List<LogisticSignal> signals = LogisticStore.collectSignals(List.of(facility));
        FacilityCommand.Result unchanged = facility.applyCommand(command, FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.CHANGED, changed.status());
        assertEquals(FacilityCommand.Status.UNCHANGED, unchanged.status());
        assertEquals(config, facility.logisticsConfig.get(item));
        assertEquals(1, signals.size());
        assertEquals(
            -8L,
            signals.get(0)
                .amount());
    }

    @Test
    void invalidFilterReplacementIsRejectedWithoutChangingSelectedSide() {
        AutomatedFacility facility = facility();
        facility.setFilters(List.of("minecraft:stick:0"), true);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ReplaceFilters(
                facility.assetId,
                FacilityCommand.FilterKind.ITEM,
                List.of("minecraft:diamond:0", "minecraft:diamond:0")),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(FacilityCommand.Rejection.INVALID_FILTERS, result.rejection());
        assertEquals(
            List.of("minecraft:stick:0"),
            facility.filtersSnapshot()
                .get(true));
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
