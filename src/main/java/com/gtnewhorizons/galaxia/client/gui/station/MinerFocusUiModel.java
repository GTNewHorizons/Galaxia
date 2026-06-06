package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Objects;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class MinerFocusUiModel {

    private MinerFocusUiModel() {}

    static boolean canPlanTier(@Nullable ModuleInstance module, @Nullable MinerFocusTier targetTier) {
        ModuleMiner miner = miner(module);
        if (miner == null || targetTier == null || hasActiveOperation(module)) return false;
        MinerFocusTier currentTier = miner.focusTier();
        return targetTier != currentTier;
    }

    static MinerFocusTier defaultUpgradeTarget(@Nullable ModuleInstance module) {
        ModuleMiner miner = miner(module);
        if (miner == null) return MinerFocusTier.I;
        return switch (miner.focusTier()) {
            case NONE -> MinerFocusTier.I;
            case I -> MinerFocusTier.II;
            case II, III -> MinerFocusTier.III;
        };
    }

    static boolean canSetOre(@Nullable ModuleInstance module, @Nullable String oreKey) {
        ModuleMiner miner = miner(module);
        return miner != null && oreKey != null && !oreKey.isBlank() && miner.focusTier() != MinerFocusTier.NONE;
    }

    static boolean canShowOreFocus(@Nullable ModuleInstance module) {
        ModuleMiner miner = miner(module);
        return miner != null && miner.focusTier() != MinerFocusTier.NONE;
    }

    static @Nullable String oreTargetForClick(@Nullable ModuleInstance module, @Nullable String oreKey) {
        if (!canSetOre(module, oreKey)) return null;
        ModuleMiner miner = miner(module);
        if (miner == null) return null;
        return Objects.equals(miner.focusOreKeyOrNull(), oreKey) ? null : oreKey;
    }

    static boolean isFocusedOre(@Nullable ModuleInstance module, @Nullable String oreKey) {
        ModuleMiner miner = miner(module);
        return miner != null && oreKey != null && oreKey.equals(miner.focusOreKeyOrNull());
    }

    static int alignmentPercent(@Nullable ModuleMiner miner) {
        if (miner == null || miner.focusTier() == MinerFocusTier.NONE || miner.focusOreKeyOrNull() == null) return 0;
        return miner.focusAlignmentProgress() * 100 / MinerFocusTier.ALIGNMENT_REQUIRED_TICKS;
    }

    static boolean hasActiveOperation(@Nullable ModuleInstance module) {
        return module != null && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private static @Nullable ModuleMiner miner(@Nullable ModuleInstance module) {
        return module != null && module.component() instanceof ModuleMiner miner ? miner : null;
    }
}
