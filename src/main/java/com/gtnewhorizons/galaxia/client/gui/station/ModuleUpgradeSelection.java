package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

record ModuleUpgradeSelection(Map<String, String> values) {

    ModuleUpgradeSelection {
        values = Map.copyOf(values);
    }

    static ModuleUpgradeSelection hammer(HammerVariant variant, ModuleTier tier) {
        return new ModuleUpgradeSelection(
            Map.of(
                ModuleUpgradeUiModel.GROUP_HAMMER_VARIANT,
                variant.name(),
                ModuleUpgradeUiModel.GROUP_HAMMER_TIER,
                tier.name()));
    }

    static ModuleUpgradeSelection minerFocus(MinerFocusTier tier) {
        return new ModuleUpgradeSelection(Map.of(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER, tier.name()));
    }

    static ModuleUpgradeSelection miner(ModuleTier tier, MinerFocusTier focusTier) {
        return new ModuleUpgradeSelection(
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

    ModuleUpgradeSelection with(String groupId, String optionId) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.put(groupId, optionId);
        return new ModuleUpgradeSelection(copy);
    }
}
