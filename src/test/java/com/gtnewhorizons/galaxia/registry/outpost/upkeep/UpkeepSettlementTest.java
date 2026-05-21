package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;

final class UpkeepSettlementTest {

    private static final ItemStackWrapper GOLD = new ItemStackWrapper(new Item(), 0, null);

    @Test
    void paysFractionalDemandFromWholeItemThenUsesStationCredit() {
        TestInventory inventory = new TestInventory(1);
        UpkeepSettlement.Credits credits = UpkeepSettlement.Credits.empty();
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(GOLD, UpkeepAmount.parse("0.1"))
            .build();

        UpkeepSettlement.Result first = UpkeepSettlement
            .settle(List.of(module(ModulePriority.NORMAL, demand)), credits, inventory);
        UpkeepSettlement.Result second = UpkeepSettlement
            .settle(List.of(module(ModulePriority.NORMAL, demand)), first.credits(), inventory);

        assertTrue(
            first.paidModuleIds()
                .contains(
                    first.moduleResults()
                        .get(0)
                        .moduleId()));
        assertEquals(1, inventory.consumedCalls.size());
        assertTrue(
            second.unpaidModuleIds()
                .isEmpty());
        assertEquals(1, inventory.consumedCalls.size());
        assertEquals(
            "0.8",
            second.credits()
                .itemCredit(GOLD)
                .toDisplayString());
    }

    @Test
    void shortageOnlyDisablesModulesThatCannotBePaidWithoutDebt() {
        TestInventory inventory = new TestInventory(1);
        UpkeepDemand demand = UpkeepDemand.builder()
            .item(GOLD, UpkeepAmount.parse("0.6"))
            .build();
        UpkeepLedger.ModuleDemand high = module(ModulePriority.HIGH, demand);
        UpkeepLedger.ModuleDemand normal = module(ModulePriority.NORMAL, demand);
        UpkeepLedger.ModuleDemand low = module(ModulePriority.LOW, demand);

        UpkeepSettlement.Result result = UpkeepSettlement
            .settle(List.of(low, normal, high), UpkeepSettlement.Credits.empty(), inventory);

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
        assertEquals(1, inventory.consumedCalls.size());
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

    private static final class TestInventory implements UpkeepSettlement.ResourceInventory {

        private long available;
        private final List<Long> consumedCalls = new ArrayList<>();

        private TestInventory(long available) {
            this.available = available;
        }

        @Override
        public long available(ItemStackWrapper item) {
            return available;
        }

        @Override
        public boolean tryConsume(ItemStackWrapper item, long amount) {
            if (available < amount) return false;
            available -= amount;
            consumedCalls.add(amount);
            return true;
        }
    }
}
