package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;

final class StationItemInteractionModel {

    enum Section {
        LOGISTICS,
        MACHINES,
        UPKEEP
    }

    enum Role {
        CORE_IMPORT,
        HAMMER_EXPORT,
        CONSUMES,
        PRODUCES,
        UPKEEP
    }

    record Entry(Section section, Role role, String label, @Nullable FacilityModuleKind kind, int count,
        @Nullable ModuleInstance.ID targetModuleId, short groupId, int reserve, int orderSize,
        @Nullable UpkeepAmount amountPerMinute) {}

    private StationItemInteractionModel() {}

    static List<Entry> forItem(AutomatedFacility facility, ItemStackWrapper item) {
        Objects.requireNonNull(facility, "facility");
        Objects.requireNonNull(item, "item");
        List<Entry> entries = new ArrayList<>();
        addLogistics(entries, facility, item);
        addRecipeMachines(entries, facility, item);
        addUpkeep(entries, facility, item);
        return List.copyOf(entries);
    }

    private static void addLogistics(List<Entry> entries, AutomatedFacility facility, ItemStackWrapper item) {
        LogisticsResourceConfig config = facility.logisticsConfig.get(item);
        if (config.isImportEnabled()) {
            entries.add(
                new Entry(
                    Section.LOGISTICS,
                    Role.CORE_IMPORT,
                    "Core",
                    null,
                    1,
                    null,
                    (short) 0,
                    config.minReserve(),
                    config.orderSize(),
                    null));
        }
        if (config.isSupplyEnabled()) {
            ModuleInstance hammer = firstHammer(facility);
            if (hammer != null) {
                entries.add(
                    new Entry(
                        Section.LOGISTICS,
                        Role.HAMMER_EXPORT,
                        "Hammer",
                        FacilityModuleKind.HAMMER,
                        1,
                        hammer.id,
                        (short) 0,
                        config.minReserve(),
                        config.orderSize(),
                        null));
            }
        }
    }

    private static void addRecipeMachines(List<Entry> entries, AutomatedFacility facility, ItemStackWrapper item) {
        Map<Key, AggregatedEntry> aggregated = new LinkedHashMap<>();
        for (ModuleInstance module : facility.modules()) {
            boolean consumes = false;
            boolean produces = module.component() instanceof ModuleMiner
                && contains(ModuleMiner.possibleOutputs(module, facility), item);
            if (module.component() instanceof IRecipeModule recipeModule) {
                RecipeConfig config = recipeModule.getRecipeConfig();
                if (config != null) {
                    for (SavedRecipe saved : config.savedRecipes()) {
                        if (!saved.enabled()) continue;
                        consumes |= contains(
                            saved.recipe()
                                .inputs(),
                            item);
                        produces |= contains(
                            saved.recipe()
                                .outputs(),
                            item);
                    }
                }
            }
            if (consumes) aggregate(aggregated, facility, Section.MACHINES, Role.CONSUMES, module, null);
            if (produces) aggregate(aggregated, facility, Section.MACHINES, Role.PRODUCES, module, null);
        }
        aggregated.values()
            .forEach(entry -> entries.add(entry.toEntry()));
    }

    private static void addUpkeep(List<Entry> entries, AutomatedFacility facility, ItemStackWrapper item) {
        Map<Key, AggregatedEntry> aggregated = new LinkedHashMap<>();
        for (ModuleInstance module : facility.modules()) {
            UpkeepAmount amount = module.currentTierUpkeepDemand()
                .itemsPerMinute()
                .get(item);
            if (amount == null || amount.isZero()) continue;
            aggregateUpkeep(aggregated, module, amount);
        }
        aggregated.values()
            .forEach(entry -> entries.add(entry.toEntry()));
    }

    private static void aggregateUpkeep(Map<Key, AggregatedEntry> aggregated, ModuleInstance module,
        UpkeepAmount amountPerMinute) {
        Key key = new Key(
            Section.UPKEEP,
            Role.UPKEEP,
            module.kind(),
            (short) 0,
            null,
            amountPerMinute.microUnitsPerMinute());
        AggregatedEntry entry = aggregated.computeIfAbsent(
            key,
            ignored -> new AggregatedEntry(
                Section.UPKEEP,
                Role.UPKEEP,
                module.kind()
                    .getDisplayName(),
                module.kind(),
                module.id,
                (short) 0));
        entry.add(amountPerMinute);
    }

    private static void aggregate(Map<Key, AggregatedEntry> aggregated, AutomatedFacility facility, Section section,
        Role role, ModuleInstance module, @Nullable UpkeepAmount amountPerMinute) {
        SettingsGroup group = sharedGroup(facility, module);
        short groupId = group == null ? 0 : group.id();
        Key key = new Key(section, role, module.kind(), groupId, groupId == 0 ? module.id : null, 0L);
        AggregatedEntry entry = aggregated.computeIfAbsent(
            key,
            ignored -> new AggregatedEntry(
                section,
                role,
                group == null ? module.kind()
                    .getDisplayName() : group.displayName(),
                module.kind(),
                module.id,
                groupId));
        entry.add(amountPerMinute);
    }

    private static @Nullable SettingsGroup sharedGroup(AutomatedFacility facility, ModuleInstance module) {
        if (module.groupId() == 0) return null;
        SettingsGroup group = facility.settingsGroups()
            .groups()
            .get(module.groupId());
        if (group == null || group.members()
            .size() < 2) {
            return null;
        }
        return group;
    }

    private static boolean contains(ItemStack[] stacks, ItemStackWrapper item) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() != null && item.equals(ItemStackWrapper.of(stack))) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(List<ItemStack> stacks, ItemStackWrapper item) {
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() != null && item.equals(ItemStackWrapper.of(stack))) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable ModuleInstance firstHammer(AutomatedFacility facility) {
        for (ModuleInstance module : facility.modules()) {
            if (module.component() instanceof ModuleHammer) return module;
        }
        return null;
    }

    private record Key(Section section, Role role, FacilityModuleKind kind, short groupId,
        @Nullable ModuleInstance.ID moduleId, long upkeepMicroUnitsPerMinute) {}

    private static final class AggregatedEntry {

        private final Section section;
        private final Role role;
        private final String label;
        private final FacilityModuleKind kind;
        private final ModuleInstance.ID targetModuleId;
        private final short groupId;
        private int count;
        private UpkeepAmount amountPerMinute = UpkeepAmount.ZERO;

        private AggregatedEntry(Section section, Role role, String label, FacilityModuleKind kind,
            ModuleInstance.ID targetModuleId, short groupId) {
            this.section = section;
            this.role = role;
            this.label = label;
            this.kind = kind;
            this.targetModuleId = targetModuleId;
            this.groupId = groupId;
        }

        private void add(@Nullable UpkeepAmount amount) {
            count++;
            if (amount != null) {
                amountPerMinute = amountPerMinute.plus(amount);
            }
        }

        private Entry toEntry() {
            return new Entry(
                section,
                role,
                label,
                kind,
                count,
                targetModuleId,
                groupId,
                0,
                0,
                role == Role.UPKEEP ? amountPerMinute : null);
        }
    }
}
