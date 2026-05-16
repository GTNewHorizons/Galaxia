package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;

public final class ModuleMiner extends TieredModuleComponent implements IParallelModule {

    public final FacilityModuleKind kind;

    public static final FacilityModuleKind KIND = FacilityModuleKind.MINER;
    private byte parallel = 1;
    private MinerFocusTier focusTier = MinerFocusTier.NONE;
    private String focusOreKey;
    private int focusAlignmentProgress;

    private static final Random RANDOM = new java.util.Random();

    public ModuleMiner(@Nonnull FacilityModuleKind kind) {
        this.kind = kind;
    }

    public static void generateOre(ModuleInstance instance, AutomatedFacility outpost) {
        if (!(instance.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("miner tick sent to non-miner module " + instance.id);
        }
        GalaxiaCelestialAPI.get(outpost.celestialObjectId)
            .ifPresent(registration -> {
                var properties = registration.properties();
                List<ItemStack> ores = properties.ores();
                List<ItemStack> veinOres = properties.getResolvedGtVeinOreStacks();
                List<ItemStack> candidates = new java.util.ArrayList<>(ores.size() + veinOres.size());
                candidates.addAll(ores);
                candidates.addAll(veinOres);
                if (candidates.isEmpty()) return;
                ItemStack chosen = chooseFocusedOre((ModuleMiner) instance.component(), candidates);
                String oreKey = ItemStackWrapper.of(chosen)
                    .toKey();
                ((ModuleMiner) instance.component()).advanceFocusAlignment();
                if (shouldVoidOre(instance, outpost, oreKey)) return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.insertInventory(ItemStackWrapper.of(ore), 1);
            });
    }

    private static ItemStack chooseFocusedOre(ModuleMiner miner, List<ItemStack> candidates) {
        int totalWeight = 0;
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack stack = candidates.get(i);
            String key = ItemStackWrapper.of(stack)
                .toKey();
            int weight = 100 + miner.effectiveFocusBonusFor(key);
            weights[i] = weight;
            totalWeight += weight;
        }
        int roll = RANDOM.nextInt(totalWeight);
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights[i];
            if (roll < 0) return candidates.get(i);
        }
        throw new IllegalStateException("Failed to choose focused ore from " + candidates.size() + " candidates");
    }

    public static boolean shouldVoidOre(@Nonnull ModuleInstance instance, @Nonnull AutomatedFacility outpost,
        String oreKey) {
        return outpost.isMinerOreBlacklisted(instance, oreKey);
    }

    @Override
    public ModuleSettings createPrivateSettings(ModuleInstance module) {
        return new MinerSettings();
    }

    @Override
    public void applySettings(ModuleInstance module, ModuleSettings settings) {
        if (!(settings instanceof MinerSettings)) {
            throw new IllegalStateException("MINER received non-miner settings for module " + module.id);
        }
    }

    @Override
    public void validateSettingsCopyTarget(ModuleInstance source, ModuleInstance target) {
        if (!(source.component() instanceof ModuleMiner sourceMiner)) {
            throw new IllegalStateException("Miner settings copy source is not a miner: " + source.id);
        }
        if (!(target.component() instanceof ModuleMiner targetMiner)) {
            throw new IllegalStateException("Miner settings copy target is not a miner: " + target.id);
        }
        String sourceFocusOreKey = sourceMiner.focusOreKeyOrNull();
        if (sourceFocusOreKey != null && targetMiner.focusTier() == MinerFocusTier.NONE) {
            throw new IllegalStateException(
                "Miner settings copy target " + target.id + " has no focus tier for ore " + sourceFocusOreKey);
        }
    }

    @Override
    public void afterSettingsCopied(ModuleInstance source, ModuleInstance target) {
        if (!(source.component() instanceof ModuleMiner sourceMiner)) {
            throw new IllegalStateException("Miner settings copy source is not a miner: " + source.id);
        }
        if (!(target.component() instanceof ModuleMiner targetMiner)) {
            throw new IllegalStateException("Miner settings copy target is not a miner: " + target.id);
        }
        targetMiner.setFocusOre(sourceMiner.focusOreKeyOrNull());
    }

    @Override
    public FeatureContribution featureContribution(ModuleInstance module, PlanetaryFeatureKey feature, int coveredTiles,
        int totalTiles) {
        if (!PlanetaryFeatureRegistry.MINERAL_VEIN.key()
            .equals(feature)) return null;
        return new FeatureContribution(
            feature,
            (byte) coveredTiles,
            (byte) totalTiles,
            "Mining yield bonus " + coveredTiles + "/" + totalTiles);
    }

    public MinerFocusTier focusTier() {
        return focusTier;
    }

    public String focusOreKeyOrNull() {
        return focusOreKey;
    }

    public int focusAlignmentProgress() {
        return focusAlignmentProgress;
    }

    @Override
    public void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        if (spec instanceof ModuleTierOperation) {
            super.applyOperationTarget(spec, module);
            return;
        }
        if (!(spec instanceof MinerFocusOperation minerSpec)) {
            throw new IllegalStateException(
                "MINER cannot handle " + spec.getClass()
                    .getSimpleName());
        }
        MinerFocusTier focusTier = MinerFocusTier.valueOf(minerSpec.targetFocusTierKey());
        String focusOreKey = focusTier == MinerFocusTier.NONE ? null : minerSpec.targetFocusOreKey();
        setFocus(focusTier, focusOreKey, 0);
    }

    public void setFocus(MinerFocusTier focusTier, String focusOreKey, int focusAlignmentProgress) {
        if (focusTier == null) {
            throw new IllegalArgumentException("Miner focus tier must not be null");
        }
        String normalizedFocusOreKey = normalizeFocusOreKey(focusOreKey);
        if (focusTier == MinerFocusTier.NONE) {
            if (normalizedFocusOreKey != null) {
                throw new IllegalArgumentException("Miner focus ore must be null when focus tier is NONE");
            }
            this.focusTier = focusTier;
            this.focusOreKey = null;
            this.focusAlignmentProgress = 0;
            return;
        }
        this.focusTier = focusTier;
        this.focusOreKey = normalizedFocusOreKey;
        this.focusAlignmentProgress = normalizedFocusOreKey == null ? 0
            : Math.clamp(focusAlignmentProgress, 0, MinerFocusTier.ALIGNMENT_REQUIRED_TICKS);
    }

    public void setFocusOre(String focusOreKey) {
        String normalized = normalizeFocusOreKey(focusOreKey);
        if (focusTier == MinerFocusTier.NONE && normalized != null) {
            throw new IllegalStateException("Miner focus ore cannot be set while focus tier is NONE");
        }
        if (Objects.equals(this.focusOreKey, normalized)) return;
        this.focusOreKey = normalized;
        resetFocusAlignment();
    }

    public void resetFocusAlignment() {
        focusAlignmentProgress = 0;
    }

    private void advanceFocusAlignment() {
        if (focusTier == MinerFocusTier.NONE || focusOreKey == null) return;
        focusAlignmentProgress = Math.min(MinerFocusTier.ALIGNMENT_REQUIRED_TICKS, focusAlignmentProgress + 1);
    }

    private static String normalizeFocusOreKey(String focusOreKey) {
        return focusOreKey == null || focusOreKey.isBlank() ? null : focusOreKey;
    }

    private int effectiveFocusBonusFor(String oreKey) {
        if (focusTier == MinerFocusTier.NONE || focusOreKey == null || !focusOreKey.equals(oreKey)) return 0;
        return focusTier.bonusPercent() * focusAlignmentProgress / MinerFocusTier.ALIGNMENT_REQUIRED_TICKS;
    }

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }
}
