package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

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

    static ModuleUpgradeSelection defaultSelection(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return ModuleUpgradeSelection.hammer(hammer.variant(), module.tier());
        }
        if (module.component() instanceof ModuleMiner) {
            return ModuleUpgradeSelection.miner(module.tier(), MinerFocusUiModel.defaultUpgradeTarget(module));
        }
        throw new IllegalArgumentException("Unsupported upgrade module: " + module.kind());
    }

    static ModuleUpgradeSelection selectOption(ModuleInstance module, ModuleUpgradeSelection selection, String groupId,
        String optionId) {
        return normalize(module, selection.with(groupId, optionId));
    }

    static ModuleUpgradeSelection normalize(ModuleInstance module, ModuleUpgradeSelection selection) {
        if (module.component() instanceof ModuleHammer) {
            HammerVariant variant = hammerVariant(selection);
            ModuleTier tier = hammerTier(selection);
            return ModuleUpgradeSelection.hammer(variant, normalizeHammerTier(variant, tier));
        }
        if (module.component() instanceof ModuleMiner) {
            return ModuleUpgradeSelection
                .miner(normalizeMinerTier(module, minerTier(selection)), minerFocusTier(selection));
        }
        return selection;
    }

    static List<ModuleUpgradeGroup> groups(ModuleInstance module, ModuleUpgradeSelection selection) {
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
        if (ModuleHammer.supportsTier(variant, tier)) return tier;
        List<ModuleTier> allowed = hammerAllowedTiers(variant);
        if (allowed.isEmpty()) {
            throw new IllegalStateException("Hammer variant has no valid tiers: " + variant);
        }
        return allowed.get(0);
    }

    static ModuleTier normalizeBuildTier(FacilityModuleKind kind, ModuleTier tier, HammerVariant hammerVariant) {
        if (kind == FacilityModuleKind.HAMMER) {
            return normalizeHammerTier(hammerVariant, tier);
        }
        return kind.allowedTiers()
            .contains(tier) ? tier : kind.defaultTier();
    }

    static HammerVariant hammerVariant(ModuleUpgradeSelection selection) {
        String raw = selection.get(GROUP_HAMMER_VARIANT);
        return raw == null ? HammerVariant.BASE : HammerVariant.valueOf(raw);
    }

    static ModuleTier hammerTier(ModuleUpgradeSelection selection) {
        String raw = selection.get(GROUP_HAMMER_TIER);
        return raw == null ? ModuleTier.EV : ModuleTier.valueOf(raw);
    }

    static ModuleTier minerTier(ModuleUpgradeSelection selection) {
        String raw = selection.get(GROUP_MINER_TIER);
        return raw == null ? ModuleTier.EV : ModuleTier.valueOf(raw);
    }

    static MinerFocusTier minerFocusTier(ModuleUpgradeSelection selection) {
        String raw = selection.get(GROUP_MINER_FOCUS_TIER);
        return raw == null ? MinerFocusTier.I : MinerFocusTier.valueOf(raw);
    }

    static boolean hasActiveBuild(@Nullable ModuleInstance module) {
        return module != null && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private static List<ModuleUpgradeGroup> hammerGroups(ModuleUpgradeSelection selection) {
        HammerVariant selectedVariant = hammerVariant(selection);
        ModuleTier selectedTier = hammerTier(selection);
        List<ModuleUpgradeOption> variants = new ArrayList<>();
        for (HammerVariant variant : HammerVariant.values()) {
            variants.add(new ModuleUpgradeOption(variant.name(), variant.name(), variant == selectedVariant, true));
        }
        List<ModuleUpgradeOption> tiers = new ArrayList<>();
        for (ModuleTier tier : ModuleTier.values()) {
            if (tier == ModuleTier.NONE) continue;
            boolean enabled = ModuleHammer.supportsTier(selectedVariant, tier);
            tiers.add(new ModuleUpgradeOption(tier.name(), tier.name(), tier == selectedTier, enabled));
        }
        return List.of(
            new ModuleUpgradeGroup(GROUP_HAMMER_VARIANT, "Variant", variants),
            new ModuleUpgradeGroup(GROUP_HAMMER_TIER, "Tier", tiers));
    }

    private static List<ModuleUpgradeGroup> minerGroups(ModuleInstance module, ModuleUpgradeSelection selection) {
        ModuleTier selectedModuleTier = minerTier(selection);
        MinerFocusTier selectedFocusTier = minerFocusTier(selection);
        List<ModuleUpgradeOption> moduleTiers = new ArrayList<>();
        for (ModuleTier tier : module.kind()
            .allowedTiers()) {
            if (tier == ModuleTier.NONE) continue;
            moduleTiers.add(new ModuleUpgradeOption(tier.name(), tier.name(), tier == selectedModuleTier, true));
        }

        List<ModuleUpgradeOption> focusTiers = new ArrayList<>();
        focusTiers.add(
            new ModuleUpgradeOption(
                MinerFocusTier.NONE.name(),
                "None",
                selectedFocusTier == MinerFocusTier.NONE,
                MinerFocusUiModel.canPlanTier(module, MinerFocusTier.NONE)));
        for (MinerFocusTier tier : new MinerFocusTier[] { MinerFocusTier.I, MinerFocusTier.II, MinerFocusTier.III }) {
            boolean enabled = MinerFocusUiModel.canPlanTier(module, tier);
            focusTiers.add(new ModuleUpgradeOption(tier.name(), tier.name(), tier == selectedFocusTier, enabled));
        }
        return List.of(
            new ModuleUpgradeGroup(GROUP_MINER_TIER, "Tier", moduleTiers),
            new ModuleUpgradeGroup(GROUP_MINER_FOCUS_TIER, "Focus Tier", focusTiers));
    }

    private static ModuleTier normalizeMinerTier(ModuleInstance module, ModuleTier tier) {
        return module.kind()
            .allowedTiers()
            .contains(tier) ? tier : module.tier();
    }
}
