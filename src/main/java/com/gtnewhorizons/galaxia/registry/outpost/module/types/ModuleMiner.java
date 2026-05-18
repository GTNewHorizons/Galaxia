package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.GTUtility;
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
    private static final int ICE_ROLL_CHANCE_PER_TILE_PERCENT = 20;
    private static final int ICE_ROLL_CHANCE_DENOMINATOR = 100;
    private static final String[] RARE_CRYSTAL_MATERIALS = { "Diamond", "Emerald", "Ruby", "Sapphire" };
    private static final String[] VOLATILE_MATERIALS = { "Sulfur", "Saltpeter", "Naquadah" };

    public ModuleMiner(@Nonnull FacilityModuleKind kind) {
        this.kind = kind;
    }

    public static void generateOre(ModuleInstance instance, AutomatedFacility outpost) {
        if (!(instance.component() instanceof ModuleMiner miner)) {
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
                addFeatureMiningCandidates(instance, outpost, candidates);
                boolean canRollIce = hasCoveredFeature(
                    instance,
                    outpost,
                    PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key());
                if (candidates.isEmpty() && !canRollIce) return;
                miner.advanceFocusAlignment();
                int rolls = 1 + mineralVeinBonusRolls(instance, outpost);
                for (int i = 0; i < rolls; i++) {
                    ItemStack chosen = shouldRollIceInsteadOfOre(instance, outpost, RANDOM) ? icePocketStack()
                        : candidates.isEmpty() ? null : chooseFocusedOre(miner, candidates);
                    if (chosen == null) continue;
                    String oreKey = ItemStackWrapper.of(chosen)
                        .toKey();
                    if (shouldVoidOre(instance, outpost, oreKey)) continue;
                    ItemStack ore = chosen.copy();
                    ore.stackSize = 1;
                    outpost.insertInventory(ItemStackWrapper.of(ore), 1);
                }
            });
    }

    public static int mineralVeinBonusRolls(ModuleInstance module, AutomatedFacility outpost) {
        if (module == null || outpost == null || module.anchorOrNull() == null) return 0;
        return outpost.featureModifiers(module)
            .coveredTiles(PlanetaryFeatureRegistry.MINERAL_VEIN.key());
    }

    public static void addFeatureMiningCandidates(@Nonnull ModuleInstance module, @Nonnull AutomatedFacility outpost,
        @Nonnull List<ItemStack> candidates) {
        addFeaturePool(
            candidates,
            rareCrystalMiningPool(),
            coveredFeatureTiles(module, outpost, PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION.key()));
        addFeaturePool(
            candidates,
            volatileDepositMiningPool(),
            coveredFeatureTiles(module, outpost, PlanetaryFeatureRegistry.VOLATILE_DEPOSIT.key()));
    }

    public static boolean shouldRollIceInsteadOfOre(@Nonnull ModuleInstance module, @Nonnull AutomatedFacility outpost,
        @Nonnull Random random) {
        int chancePercent = iceRollChancePercent(module, outpost);
        return chancePercent > 0 && random.nextInt(ICE_ROLL_CHANCE_DENOMINATOR) < chancePercent;
    }

    public static int iceRollChancePercent(@Nonnull ModuleInstance module, @Nonnull AutomatedFacility outpost) {
        return Math.min(
            coveredFeatureTiles(module, outpost, PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key())
                * ICE_ROLL_CHANCE_PER_TILE_PERCENT,
            ICE_ROLL_CHANCE_DENOMINATOR);
    }

    public static ItemStack icePocketStack() {
        return new ItemStack(Blocks.ice);
    }

    private static boolean hasCoveredFeature(ModuleInstance module, AutomatedFacility outpost,
        PlanetaryFeatureKey key) {
        return coveredFeatureTiles(module, outpost, key) > 0;
    }

    private static int coveredFeatureTiles(ModuleInstance module, AutomatedFacility outpost, PlanetaryFeatureKey key) {
        if (module == null || outpost == null || module.anchorOrNull() == null) return 0;
        return outpost.featureModifiers(module)
            .coveredTiles(key);
    }

    private static void addFeaturePool(List<ItemStack> candidates, List<ItemStack> pool, int repeats) {
        if (repeats <= 0 || pool.isEmpty()) return;
        for (int i = 0; i < repeats; i++) {
            for (ItemStack stack : pool) {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                candidates.add(copy);
            }
        }
    }

    private static List<ItemStack> rareCrystalMiningPool() {
        List<ItemStack> pool = rawOrePool(RARE_CRYSTAL_MATERIALS);
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Items.diamond));
            pool.add(new ItemStack(Items.emerald));
        }
        return pool;
    }

    private static List<ItemStack> volatileDepositMiningPool() {
        List<ItemStack> pool = rawOrePool(VOLATILE_MATERIALS);
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Items.gunpowder));
            pool.add(new ItemStack(Items.coal));
            pool.add(new ItemStack(Items.redstone));
        }
        return pool;
    }

    private static List<ItemStack> rawOrePool(String[] materials) {
        List<ItemStack> pool = new java.util.ArrayList<>(materials.length);
        for (String material : materials) {
            ItemStack stack;
            try {
                stack = GTUtility.getRawOreStack(material);
            } catch (ClassCastException ignored) {
                stack = null;
            }
            if (stack == null) continue;
            stack = stack.copy();
            stack.stackSize = 1;
            pool.add(stack);
        }
        return pool;
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
        if (PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key()
            .equals(feature)) {
            return new FeatureContribution(
                feature,
                (byte) coveredTiles,
                (byte) totalTiles,
                Math.min(coveredTiles * ICE_ROLL_CHANCE_PER_TILE_PERCENT, ICE_ROLL_CHANCE_DENOMINATOR)
                    + "% ice roll chance");
        }
        if (PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION.key()
            .equals(feature)) {
            return new FeatureContribution(feature, (byte) coveredTiles, (byte) totalTiles, "Gem ore pool");
        }
        if (PlanetaryFeatureRegistry.VOLATILE_DEPOSIT.key()
            .equals(feature)) {
            return new FeatureContribution(feature, (byte) coveredTiles, (byte) totalTiles, "Volatile resource pool");
        }
        if (!PlanetaryFeatureRegistry.MINERAL_VEIN.key()
            .equals(feature)) return null;
        return new FeatureContribution(
            feature,
            (byte) coveredTiles,
            (byte) totalTiles,
            "Mining yield +" + coveredTiles + " roll/t");
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
