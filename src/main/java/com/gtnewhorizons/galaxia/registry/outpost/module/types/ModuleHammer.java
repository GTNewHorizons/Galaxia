package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;

public final class ModuleHammer implements IModuleComponent, IParallelModule {

    private static final ModuleTier[] BASE_TIERS = { ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV };
    private static final ModuleTier[] BIG_TIERS = { ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV };

    public final FacilityModuleKind kind;

    private byte parallel = 1;

    private final int maxBatchSize;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private boolean canFire;

    private HammerVariant variant;
    private AllowShootingConfig config;

    public ModuleHammer(@Nonnull FacilityModuleKind kind, @Nonnull AllowShootingConfig config,
        @Nonnull OrbitalTransferPlanner.RoutePriority routePriority, boolean canFire, @Nonnull HammerVariant variant,
        int maxBatchSize) {
        this.kind = kind;
        this.config = config;
        this.routePriority = routePriority;
        this.canFire = canFire;
        this.variant = variant;
        this.maxBatchSize = maxBatchSize;
    }

    public static void prepareToFire(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleHammer hammer = (ModuleHammer) instance.component();
        if (!outpost.tryConsumeEnergy(hammer.variant.shotEnergyEu())) return;
        hammer.canFire = true;
    }

    public static ModuleTier tierForVariantSwitch(@Nonnull HammerVariant targetVariant,
        @Nonnull ModuleTier currentTier) {
        return supportsTier(targetVariant, currentTier) ? currentTier : tiersFor(targetVariant)[0];
    }

    public static boolean supportsTier(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        for (ModuleTier t : tiersFor(variant)) {
            if (t == tier) return true;
        }
        return false;
    }

    public static void requireTier(@Nonnull HammerVariant variant, @Nonnull ModuleTier tier) {
        if (!supportsTier(variant, tier)) throw invalidTier(variant, tier);
    }

    @Override
    public void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        if (!(spec instanceof HammerModuleOperation hammerSpec)) {
            throw new IllegalStateException(
                "HAMMER cannot handle " + spec.getClass()
                    .getSimpleName());
        }
        HammerVariant targetVariant = HammerVariant.valueOf(hammerSpec.targetVariantKey());
        ModuleTier targetTier = hammerSpec.targetTier();
        requireTier(targetVariant, targetTier);
        this.variant = targetVariant;
        module.setTier(targetTier);
    }

    public AllowShootingConfig config() {
        return config;
    }

    public void setConfig(@Nonnull AllowShootingConfig newConfig) {
        this.config = newConfig;
    }

    public OrbitalTransferPlanner.RoutePriority routePriority() {
        return routePriority;
    }

    public boolean canFire() {
        return canFire;
    }

    public void fire() {
        canFire = false;
    }

    public HammerVariant variant() {
        return variant;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public void setRoutePriority(@Nonnull OrbitalTransferPlanner.RoutePriority routePriority) {
        this.routePriority = routePriority;
    }

    public void setVariant(@Nonnull HammerVariant variant) {
        this.variant = variant;
    }

    private static IllegalStateException invalidTier(HammerVariant variant, ModuleTier tier) {
        return new IllegalStateException("Hammer variant " + variant + " does not support tier " + tier);
    }

    private static ModuleTier[] tiersFor(HammerVariant variant) {
        return switch (variant) {
            case BASE -> BASE_TIERS;
            case BIG -> BIG_TIERS;
        };
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
