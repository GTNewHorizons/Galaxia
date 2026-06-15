package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;

public final class UpkeepSettlement {

    private UpkeepSettlement() {}

    public static Result settle(List<UpkeepLedger.ModuleDemand> moduleDemands, Credits credits,
        AutomatedFacility facility) {
        return settle(moduleDemands, credits, facility, true);
    }

    public static Result preview(List<UpkeepLedger.ModuleDemand> moduleDemands, Credits credits,
        AutomatedFacility facility) {
        return settle(moduleDemands, credits, facility, false);
    }

    private static Result settle(List<UpkeepLedger.ModuleDemand> moduleDemands, Credits credits,
        AutomatedFacility facility, boolean consume) {
        Objects.requireNonNull(moduleDemands, "moduleDemands");
        Objects.requireNonNull(facility, "facility");
        Credits currentCredits = credits == null ? Credits.empty() : credits;
        List<UpkeepLedger.ModuleDemand> ordered = new ArrayList<>(moduleDemands);
        ordered.sort((a, b) -> Integer.compare(priorityRank(b.priority()), priorityRank(a.priority())));

        Map<InventoryKey, Long> previewConsumes = consume ? Map.of() : new LinkedHashMap<>();
        List<ModuleResult> results = new ArrayList<>();
        for (UpkeepLedger.ModuleDemand moduleDemand : ordered) {
            Payment payment = tryPlanPayment(moduleDemand.demand(), currentCredits, facility, previewConsumes);
            if (payment == null) {
                results.add(new ModuleResult(moduleDemand.moduleId(), false));
                continue;
            }
            if (consume) {
                payment.consume(facility);
            } else {
                payment.consumes()
                    .forEach((key, amount) -> previewConsumes.merge(key, amount, Long::sum));
            }
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

    private static Payment tryPlanPayment(UpkeepDemand demand, Credits credits, AutomatedFacility facility,
        Map<InventoryKey, Long> previewConsumes) {
        Map<InventoryKey, UpkeepAmount> nextCredits = credits.allCredits();
        Map<InventoryKey, Long> consumes = new LinkedHashMap<>();

        if (!tryPlanResources(demand.itemsPerMinute(), nextCredits, consumes, facility, previewConsumes)) return null;
        if (!tryPlanResources(demand.fluidsPerMinute(), nextCredits, consumes, facility, previewConsumes)) return null;

        return new Payment(Credits.fromInventoryCredits(nextCredits), consumes);
    }

    private static <T extends InventoryKey> boolean tryPlanResources(Map<T, UpkeepAmount> demands,
        Map<InventoryKey, UpkeepAmount> nextCredits, Map<InventoryKey, Long> consumes, AutomatedFacility facility,
        Map<InventoryKey, Long> previewConsumes) {
        for (Map.Entry<T, UpkeepAmount> entry : demands.entrySet()) {
            InventoryKey key = entry.getKey();
            UpkeepAmount demandAmount = entry.getValue();
            UpkeepAmount availableCredit = nextCredits.getOrDefault(key, UpkeepAmount.ZERO);
            if (availableCredit.compareTo(demandAmount) >= 0) {
                nextCredits.put(key, availableCredit.minus(demandAmount));
                continue;
            }

            UpkeepAmount deficit = demandAmount.minus(availableCredit);
            long toConsume = deficit.wholeUnitsToCoverDeficit();
            long alreadyPlanned = consumes.getOrDefault(key, 0L) + previewConsumes.getOrDefault(key, 0L);
            if (available(facility, key) < alreadyPlanned + toConsume) return false;

            consumes.merge(key, toConsume, Long::sum);
            UpkeepAmount newCredit = availableCredit.plus(UpkeepAmount.wholeUnitsCredit(toConsume))
                .minus(demandAmount);
            nextCredits.put(key, newCredit);
        }
        return true;
    }

    private static long available(AutomatedFacility facility, InventoryKey key) {
        if (key instanceof ItemStackWrapper item) return facility.getItemAmount(item);
        return facility.getFluidAmount((FluidKey) key);
    }

    private static boolean consume(AutomatedFacility facility, InventoryKey key, long amount) {
        if (key instanceof ItemStackWrapper item) return facility.tryConsumeInventory(item, amount);
        return facility.tryConsumeFluid((FluidKey) key, amount);
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

        private Map<InventoryKey, UpkeepAmount> allCredits() {
            Map<InventoryKey, UpkeepAmount> result = new LinkedHashMap<>();
            result.putAll(itemCredits);
            result.putAll(fluidCredits);
            return result;
        }

        private static Credits fromInventoryCredits(Map<InventoryKey, UpkeepAmount> credits) {
            Map<ItemStackWrapper, UpkeepAmount> itemCredits = new LinkedHashMap<>();
            Map<FluidKey, UpkeepAmount> fluidCredits = new LinkedHashMap<>();
            for (Map.Entry<InventoryKey, UpkeepAmount> entry : credits.entrySet()) {
                if (entry.getKey() instanceof ItemStackWrapper item) {
                    itemCredits.put(item, entry.getValue());
                } else {
                    fluidCredits.put((FluidKey) entry.getKey(), entry.getValue());
                }
            }
            return new Credits(itemCredits, fluidCredits);
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

    private record Payment(Credits creditsAfter, Map<InventoryKey, Long> consumes) {

        private void consume(AutomatedFacility facility) {
            consumes.forEach((key, amount) -> UpkeepSettlement.consume(facility, key, amount));
        }
    }
}
