package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;

public final class UpkeepSettlement {

    private UpkeepSettlement() {}

    public static Result settle(List<UpkeepLedger.ModuleDemand> moduleDemands, Credits credits,
        ResourceInventory inventory) {
        Objects.requireNonNull(moduleDemands, "moduleDemands");
        Objects.requireNonNull(inventory, "inventory");
        Credits currentCredits = credits == null ? Credits.empty() : credits;
        List<UpkeepLedger.ModuleDemand> ordered = new ArrayList<>(moduleDemands);
        ordered.sort((a, b) -> Integer.compare(priorityRank(b.priority()), priorityRank(a.priority())));

        List<ModuleResult> results = new ArrayList<>();
        for (UpkeepLedger.ModuleDemand moduleDemand : ordered) {
            Payment payment = tryPlanPayment(moduleDemand.demand(), currentCredits, inventory);
            if (payment == null) {
                results.add(new ModuleResult(moduleDemand.moduleId(), false));
                continue;
            }
            payment.consume(inventory);
            currentCredits = payment.creditsAfter();
            results.add(new ModuleResult(moduleDemand.moduleId(), true));
        }
        return new Result(results, currentCredits);
    }

    private static int priorityRank(ModulePriority priority) {
        return switch (priority) {
            case CRITICAL -> 3;
            case HIGH -> 2;
            case NORMAL -> 1;
            case LOW -> 0;
        };
    }

    private static Payment tryPlanPayment(UpkeepDemand demand, Credits credits, ResourceInventory inventory) {
        Map<ItemStackWrapper, UpkeepAmount> nextItemCredits = new LinkedHashMap<>(credits.itemCredits());
        Map<ItemStackWrapper, Long> itemConsumes = new LinkedHashMap<>();

        for (Map.Entry<ItemStackWrapper, UpkeepAmount> entry : demand.itemsPerMinute()
            .entrySet()) {
            ItemStackWrapper item = entry.getKey();
            UpkeepAmount demandAmount = entry.getValue();
            UpkeepAmount availableCredit = nextItemCredits.getOrDefault(item, UpkeepAmount.ZERO);
            if (availableCredit.compareTo(demandAmount) >= 0) {
                nextItemCredits.put(item, availableCredit.minus(demandAmount));
                continue;
            }

            UpkeepAmount deficit = demandAmount.minus(availableCredit);
            long toConsume = deficit.wholeUnitsToCoverDeficit();
            long alreadyPlanned = itemConsumes.getOrDefault(item, 0L);
            if (inventory.available(item) < alreadyPlanned + toConsume) return null;

            itemConsumes.merge(item, toConsume, Long::sum);
            UpkeepAmount newCredit = availableCredit.plus(UpkeepAmount.wholeUnitsCredit(toConsume))
                .minus(demandAmount);
            nextItemCredits.put(item, newCredit);
        }

        Map<FluidKey, UpkeepAmount> nextFluidCredits = new LinkedHashMap<>(credits.fluidCredits());
        Map<FluidKey, Long> fluidConsumes = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, UpkeepAmount> entry : demand.fluidsPerMinute()
            .entrySet()) {
            FluidKey fluid = entry.getKey();
            UpkeepAmount demandAmount = entry.getValue();
            UpkeepAmount availableCredit = nextFluidCredits.getOrDefault(fluid, UpkeepAmount.ZERO);
            if (availableCredit.compareTo(demandAmount) >= 0) {
                nextFluidCredits.put(fluid, availableCredit.minus(demandAmount));
                continue;
            }

            UpkeepAmount deficit = demandAmount.minus(availableCredit);
            long toConsume = deficit.wholeUnitsToCoverDeficit();
            long alreadyPlanned = fluidConsumes.getOrDefault(fluid, 0L);
            if (inventory.availableFluid(fluid) < alreadyPlanned + toConsume) return null;

            fluidConsumes.merge(fluid, toConsume, Long::sum);
            UpkeepAmount newCredit = availableCredit.plus(UpkeepAmount.wholeUnitsCredit(toConsume))
                .minus(demandAmount);
            nextFluidCredits.put(fluid, newCredit);
        }

        return new Payment(new Credits(nextItemCredits, nextFluidCredits), itemConsumes, fluidConsumes);
    }

    public interface ResourceInventory {

        long available(ItemStackWrapper item);

        boolean tryConsume(ItemStackWrapper item, long amount);

        default long availableFluid(FluidKey fluid) {
            return 0L;
        }

        default boolean tryConsumeFluid(FluidKey fluid, long amount) {
            return false;
        }
    }

    public record Credits(Map<ItemStackWrapper, UpkeepAmount> itemCredits, Map<FluidKey, UpkeepAmount> fluidCredits) {

        public Credits {
            itemCredits = normalizeItemCredits(itemCredits);
            fluidCredits = normalizeFluidCredits(fluidCredits);
        }

        public static Credits empty() {
            return new Credits(Map.of(), Map.of());
        }

        public UpkeepAmount itemCredit(ItemStackWrapper item) {
            return itemCredits.getOrDefault(item, UpkeepAmount.ZERO);
        }

        private static Map<ItemStackWrapper, UpkeepAmount> normalizeItemCredits(
            Map<ItemStackWrapper, UpkeepAmount> source) {
            Map<ItemStackWrapper, UpkeepAmount> result = new LinkedHashMap<>();
            for (Map.Entry<ItemStackWrapper, UpkeepAmount> entry : Objects.requireNonNull(source, "itemCredits")
                .entrySet()) {
                UpkeepAmount amount = Objects.requireNonNull(entry.getValue(), "item credit");
                if (!amount.isZero()) {
                    result.put(Objects.requireNonNull(entry.getKey(), "item"), amount);
                }
            }
            return Collections.unmodifiableMap(result);
        }

        private static Map<FluidKey, UpkeepAmount> normalizeFluidCredits(Map<FluidKey, UpkeepAmount> source) {
            Map<FluidKey, UpkeepAmount> result = new LinkedHashMap<>();
            for (Map.Entry<FluidKey, UpkeepAmount> entry : Objects.requireNonNull(source, "fluidCredits")
                .entrySet()) {
                UpkeepAmount amount = Objects.requireNonNull(entry.getValue(), "fluid credit");
                if (!amount.isZero()) {
                    result.put(Objects.requireNonNull(entry.getKey(), "fluid"), amount);
                }
            }
            return Collections.unmodifiableMap(result);
        }
    }

    public record ModuleResult(ModuleInstance.ID moduleId, boolean paid) {

        public ModuleResult {
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }

    public record Result(List<ModuleResult> moduleResults, Credits credits) {

        public Result {
            moduleResults = List.copyOf(moduleResults);
            credits = Objects.requireNonNull(credits, "credits");
        }

        public Set<ModuleInstance.ID> paidModuleIds() {
            Set<ModuleInstance.ID> result = new HashSet<>();
            for (ModuleResult moduleResult : moduleResults) {
                if (moduleResult.paid()) result.add(moduleResult.moduleId());
            }
            return result;
        }

        public List<ModuleInstance.ID> unpaidModuleIds() {
            List<ModuleInstance.ID> result = new ArrayList<>();
            for (ModuleResult moduleResult : moduleResults) {
                if (!moduleResult.paid()) result.add(moduleResult.moduleId());
            }
            return result;
        }
    }

    private record Payment(Credits creditsAfter, Map<ItemStackWrapper, Long> itemConsumes,
        Map<FluidKey, Long> fluidConsumes) {

        private void consume(ResourceInventory inventory) {
            itemConsumes.forEach(inventory::tryConsume);
            fluidConsumes.forEach(inventory::tryConsumeFluid);
        }
    }
}
