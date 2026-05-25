package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;

final class UpkeepSettlementTest {

    private static final ItemStackWrapper GOLD = new ItemStackWrapper(new Item(), 0, null);

    @Test
    void paysFractionalDemandFromWholeItemThenUsesStationCredit() {
        AutomatedFacility facility = facilityWithInventory(1);
        UpkeepSettlement.Credits credits = UpkeepSettlement.Credits.empty();
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(GOLD, UpkeepAmount.parse("0.1"))
            .build();

        UpkeepSettlement.Result first = UpkeepSettlement
            .settle(List.of(module(ModulePriority.NORMAL, demand)), credits, facility);
        UpkeepSettlement.Result second = UpkeepSettlement
            .settle(List.of(module(ModulePriority.NORMAL, demand)), first.credits(), facility);

        assertTrue(
            first.paidModuleIds()
                .contains(
                    first.moduleResults()
                        .get(0)
                        .moduleId()));
        assertEquals(0L, facility.getItemAmount(GOLD));
        assertTrue(
            second.unpaidModuleIds()
                .isEmpty());
        assertEquals(0L, facility.getItemAmount(GOLD));
        assertEquals(
            "0.8",
            second.credits()
                .itemCredit(GOLD)
                .toDisplayString());
    }

    @Test
    void shortageOnlyDisablesModulesThatCannotBePaidWithoutDebt() {
        AutomatedFacility facility = facilityWithInventory(1);
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(GOLD, UpkeepAmount.parse("0.6"))
            .build();
        UpkeepLedger.ModuleDemand high = module(ModulePriority.HIGH, demand);
        UpkeepLedger.ModuleDemand normal = module(ModulePriority.NORMAL, demand);
        UpkeepLedger.ModuleDemand low = module(ModulePriority.LOW, demand);

        UpkeepSettlement.Result result = UpkeepSettlement
            .settle(List.of(low, normal, high), UpkeepSettlement.Credits.empty(), facility);

        assertTrue(
            result.paidModuleIds()
                .contains(high.moduleId()));
        assertFalse(
            result.paidModuleIds()
                .contains(normal.moduleId()));
        assertFalse(
            result.paidModuleIds()
                .contains(low.moduleId()));
        assertEquals(List.of(normal.moduleId(), low.moduleId()), result.unpaidModuleIds());
        assertEquals(0L, facility.getItemAmount(GOLD));
        assertEquals(
            "0.4",
            result.credits()
                .itemCredit(GOLD)
                .toDisplayString());
    }

    private static UpkeepLedger.ModuleDemand module(ModulePriority priority, UpkeepDemand demand) {
        return new UpkeepLedger.ModuleDemand(
            new ModuleInstance.ID(UUID.randomUUID()),
            FacilityModuleKind.HAMMER,
            priority,
            demand);
    }

    private static AutomatedFacility facilityWithInventory(int available) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.updateItems(GOLD, available);
        return facility;
    }
}
