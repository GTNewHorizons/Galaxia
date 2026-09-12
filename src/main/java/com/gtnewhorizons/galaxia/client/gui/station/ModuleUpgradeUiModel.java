package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleUpgradeUiModel {

    static final String GROUP_HAMMER_VARIANT = "hammer.variant";
    static final String GROUP_HAMMER_TIER = "hammer.tier";
    static final String GROUP_MINER_TIER = "miner.tier";
    static final String GROUP_MINER_FOCUS_TIER = "miner.focusTier";

    private ModuleUpgradeUiModel() {}

    static boolean supports(@Nullable ModuleInstance module) {
        return module != null
            && (module.component() instanceof ModuleHammer || module.component() instanceof ModuleMiner);
    }

    static Selection defaultSelection(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return Selection.hammer(hammer.variant(), module.tier());
        }
        if (module.component() instanceof ModuleMiner) {
            return Selection.miner(module.tier(), MinerFocusUiModel.defaultUpgradeTarget(module));
        }
        throw new IllegalArgumentException("Unsupported upgrade module: " + module.kind());
    }

    static Selection selectOption(ModuleInstance module, Selection selection, String groupId, String optionId) {
        return normalize(module, selection.with(groupId, optionId));
    }

    static Selection normalize(ModuleInstance module, Selection selection) {
        if (module.component() instanceof ModuleHammer) {
            HammerVariant variant = hammerVariant(selection);
            ModuleTier tier = hammerTier(selection);
            return Selection.hammer(variant, normalizeHammerTier(variant, tier));
        }
        if (module.component() instanceof ModuleMiner) {
            return Selection.miner(normalizeMinerTier(module, minerTier(selection)), minerFocusTier(selection));
        }
        return selection;
    }

    static List<Group> groups(ModuleInstance module, Selection selection) {
        if (module.component() instanceof ModuleHammer) {
            return hammerGroups(selection);
        }
        if (module.component() instanceof ModuleMiner) {
            return minerGroups(module, selection);
        }
        return List.of();
    }

    static List<ModuleTier> hammerAllowedTiers(HammerVariant variant) {
        List<ModuleTier> tiers = new ArrayList<>();
        for (ModuleTier tier : ModuleTier.values()) {
            if (ModuleHammer.supportsTier(variant, tier)) tiers.add(tier);
        }
        return List.copyOf(tiers);
    }

    static ModuleTier normalizeHammerTier(HammerVariant variant, ModuleTier tier) {
        return ModuleHammer.tierForVariantSwitch(variant, tier);
    }

    static Map<ItemStackWrapper, Long> upgradeMaterials(ModuleInstance module, Selection selection) {
        return module.constructionMaterials(
            module.component() instanceof ModuleHammer ? hammerTier(selection) : minerTier(selection));
    }

    static ModuleTier normalizeBuildTier(FacilityModuleKind kind, ModuleTier tier, HammerVariant hammerVariant) {
        if (kind == FacilityModuleKind.HAMMER) {
            return normalizeHammerTier(hammerVariant, tier);
        }
        return kind.allowedTiers()
            .contains(tier) ? tier : kind.defaultTier();
    }

    static HammerVariant hammerVariant(Selection selection) {
        String raw = selection.get(GROUP_HAMMER_VARIANT);
        return raw == null ? HammerVariant.BASE : HammerVariant.valueOf(raw);
    }

    static ModuleTier hammerTier(Selection selection) {
        String raw = selection.get(GROUP_HAMMER_TIER);
        return raw == null ? ModuleTier.EV : ModuleTier.valueOf(raw);
    }

    static ModuleTier minerTier(Selection selection) {
        String raw = selection.get(GROUP_MINER_TIER);
        return raw == null ? ModuleTier.EV : ModuleTier.valueOf(raw);
    }

    static MinerFocusTier minerFocusTier(Selection selection) {
        String raw = selection.get(GROUP_MINER_FOCUS_TIER);
        return raw == null ? MinerFocusTier.I : MinerFocusTier.valueOf(raw);
    }

    static boolean hasActiveBuild(@Nullable ModuleInstance module) {
        return module != null && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, ModuleInstance source, ModuleTier targetTier,
        @Nullable HammerVariant targetHammerVariant, StationTileCoord coord) {
        if (facility == null || source == null || targetTier == null || coord == null) return false;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return false;
        ModuleInstance target = layout.moduleAt(coord);
        return target != null && source.kind() == target.kind()
            && target.canStartOperation()
            && target.component() != null
            && target.component()
                .canUpgradeTo(target, targetTier, targetHammerVariant);
    }

    static List<ModuleInstance.ID> confirmedTargets(AutomatedFacility facility, ModuleInstance source,
        ModuleTier targetTier, @Nullable HammerVariant targetHammerVariant, List<StationTileCoord> selectedCoords) {
        List<ModuleInstance.ID> targets = new ArrayList<>();
        if (facility == null || source == null || selectedCoords == null || selectedCoords.isEmpty()) return targets;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return targets;

        Set<ModuleInstance.ID> seenModules = new HashSet<>();
        for (StationTileCoord coord : selectedCoords) {
            if (coord == null) continue;
            ModuleInstance target = layout.moduleAt(coord);
            if (target == null || !seenModules.add(target.id)) continue;
            if (!isCompatibleTarget(facility, source, targetTier, targetHammerVariant, coord)) continue;
            targets.add(target.id);
        }
        return targets;
    }

    private static List<Group> hammerGroups(Selection selection) {
        HammerVariant selectedVariant = hammerVariant(selection);
        ModuleTier selectedTier = hammerTier(selection);
        List<Option> variants = new ArrayList<>();
        for (HammerVariant variant : HammerVariant.values()) {
            variants.add(new Option(variant.name(), variant.name(), variant == selectedVariant, true));
        }
        List<Option> tiers = new ArrayList<>();
        for (ModuleTier tier : hammerAllowedTiers(selectedVariant)) {
            tiers.add(new Option(tier.name(), tier.name(), tier == selectedTier, true));
        }
        return List
            .of(new Group(GROUP_HAMMER_VARIANT, "Variant", variants), new Group(GROUP_HAMMER_TIER, "Tier", tiers));
    }

    private static List<Group> minerGroups(ModuleInstance module, Selection selection) {
        ModuleTier selectedModuleTier = minerTier(selection);
        MinerFocusTier selectedFocusTier = minerFocusTier(selection);
        List<Option> moduleTiers = new ArrayList<>();
        for (ModuleTier tier : module.kind()
            .allowedTiers()) {
            if (tier == ModuleTier.NONE) continue;
            moduleTiers.add(new Option(tier.name(), tier.name(), tier == selectedModuleTier, true));
        }

        List<Option> focusTiers = new ArrayList<>();
        focusTiers.add(
            new Option(
                MinerFocusTier.NONE.name(),
                "None",
                selectedFocusTier == MinerFocusTier.NONE,
                MinerFocusUiModel.canPlanTier(module, MinerFocusTier.NONE)));
        for (MinerFocusTier tier : new MinerFocusTier[] { MinerFocusTier.I, MinerFocusTier.II, MinerFocusTier.III }) {
            boolean enabled = MinerFocusUiModel.canPlanTier(module, tier);
            focusTiers.add(new Option(tier.name(), tier.name(), tier == selectedFocusTier, enabled));
        }
        return List.of(
            new Group(GROUP_MINER_TIER, "Tier", moduleTiers),
            new Group(GROUP_MINER_FOCUS_TIER, "Focus Tier", focusTiers));
    }

    private static ModuleTier normalizeMinerTier(ModuleInstance module, ModuleTier tier) {
        return module.kind()
            .allowedTiers()
            .contains(tier) ? tier : module.tier();
    }

    record Group(String id, String title, List<Option> options) {

        Group {
            options = List.copyOf(options);
        }
    }

    record Option(String id, String label, boolean selected, boolean enabled) {}

    record Selection(Map<String, String> values) {

        Selection {
            values = Map.copyOf(values);
        }

        static Selection hammer(HammerVariant variant, ModuleTier tier) {
            return new Selection(
                Map.of(
                    ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT,
                    variant.name(),
                    ModuleUpgradeUiModel.GROUP_HAMMER_TIER,
                    tier.name()));
        }

        static Selection minerFocus(MinerFocusTier tier) {
            return new Selection(Map.of(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER, tier.name()));
        }

        static Selection miner(ModuleTier tier, MinerFocusTier focusTier) {
            return new Selection(
                Map.of(
                    ModuleUpgradeUiModel.GROUP_MINER_TIER,
                    tier.name(),
                    ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER,
                    focusTier.name()));
        }

        @Nullable
        String get(String groupId) {
            return values.get(groupId);
        }

        Selection with(String groupId, String optionId) {
            Map<String, String> copy = new LinkedHashMap<>(values);
            copy.put(groupId, optionId);
            return new Selection(copy);
        }
    }
}
