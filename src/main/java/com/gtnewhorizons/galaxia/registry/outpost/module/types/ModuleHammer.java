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
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;

public final class ModuleHammer implements IModuleComponent, IParallelModule {

    public static final long EU_PER_DV = 10_000L;
    public static final long MIN_SHOT_ENERGY_EU = EU_PER_DV;
    public static final int CHARGE_STEP_TICKS = 20;
    public static final int ROUTE_PROBE_INTERVAL_TICKS = 20;

    private static final ModuleTier[] BASE_TIERS = { ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV };
    private static final ModuleTier[] BIG_TIERS = { ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV };

    public final FacilityModuleKind kind;

    private byte parallel = 1;

    private final int maxBatchSize;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private long energyStored;
    private int shotCooldownTicks;
    private int routeProbeCooldownTicks;

    private HammerVariant variant;
    private AllowShootingConfig config;

    public ModuleHammer(@Nonnull FacilityModuleKind kind, @Nonnull AllowShootingConfig config,
        @Nonnull OrbitalTransferPlanner.RoutePriority routePriority, boolean canFire, @Nonnull HammerVariant variant,
        int maxBatchSize) {
        this(kind, config, routePriority, variant, maxBatchSize, 0L);
    }

    public ModuleHammer(@Nonnull FacilityModuleKind kind, @Nonnull AllowShootingConfig config,
        @Nonnull OrbitalTransferPlanner.RoutePriority routePriority, @Nonnull HammerVariant variant, int maxBatchSize,
        long energyStored) {
        this.kind = kind;
        this.config = config;
        this.routePriority = routePriority;
        this.variant = variant;
        this.maxBatchSize = maxBatchSize;
        setEnergyStored(energyStored);
    }

    public static void charge(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleHammer hammer = (ModuleHammer) instance.component();
        long charge = hammer.chargeRate(instance) * Math.max(1, instance.cooldownTicks());
        if (hammer.chargeFrom(outpost, charge)) {
            outpost.markModuleDirty(instance.id);
        }
    }

    public static long shotEnergyCost(double totalDv) {
        if (!Double.isFinite(totalDv) || totalDv < 0.0) {
            throw new IllegalArgumentException("Invalid hammer shot totalDv: " + totalDv);
        }
        return Math.max(MIN_SHOT_ENERGY_EU, (long) Math.ceil(totalDv * EU_PER_DV));
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

    public static int chargeTicks(@Nonnull HammerVariant variant, @Nonnull ModuleTierData data) {
        if (data.variantChargeTicks() != null) {
            Integer override = data.variantChargeTicks()
                .get(variant.name());
            if (override != null) return override;
        }
        if (data.chargeTicks() != null) return data.chargeTicks();
        return data.cooldownTicks();
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
        setEnergyStored(energyStored);
        module.setTier(targetTier);
    }

    @Override
    public int cooldownTicks(ModuleInstance module, ModuleTierData data) {
        if (data.variantCooldowns() != null) {
            Integer override = data.variantCooldowns()
                .get(variant.name());
            if (override != null) return override;
        }
        return data.cooldownTicks();
    }

    @Override
    public void tickOperational(ModuleInstance module, AutomatedFacility outpost) {
        tickDispatchCooldowns();
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
        return shotCooldownTicks <= 0 && energyStored >= MIN_SHOT_ENERGY_EU;
    }

    public boolean canPlanRoute(ModuleInstance module) {
        return module != null && canFire() && routeProbeCooldownTicks <= 0;
    }

    public void markRouteProbeAttempted() {
        routeProbeCooldownTicks = ROUTE_PROBE_INTERVAL_TICKS;
    }

    public void markShotDispatched(ModuleInstance module) {
        shotCooldownTicks = Math.max(1, chargeTicks(module));
        routeProbeCooldownTicks = 0;
    }

    public void tickDispatchCooldowns() {
        if (shotCooldownTicks > 0) shotCooldownTicks--;
        if (routeProbeCooldownTicks > 0) routeProbeCooldownTicks--;
    }

    public int shotCooldownTicks() {
        return shotCooldownTicks;
    }

    public int routeProbeCooldownTicks() {
        return routeProbeCooldownTicks;
    }

    public void setDispatchCooldowns(int shotCooldownTicks, int routeProbeCooldownTicks) {
        this.shotCooldownTicks = Math.max(0, shotCooldownTicks);
        this.routeProbeCooldownTicks = Math.max(0, routeProbeCooldownTicks);
    }

    public long energyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        this.energyStored = Math.clamp(energyStored, 0L, energyCapacity());
    }

    public long energyCapacity() {
        return variant.shotEnergyEu();
    }

    public int chargeTicks(ModuleInstance module) {
        return chargeTicks(
            variant,
            module.allTierData()
                .get(module.tier()));
    }

    public long chargeRate(ModuleInstance module) {
        return Math.ceilDiv(energyCapacity(), Math.max(1, chargeTicks(module) - CHARGE_STEP_TICKS));
    }

    public boolean canSpendShotEnergy(long amount) {
        return amount >= 0L && amount <= energyCapacity() && energyStored >= amount;
    }

    public boolean trySpendShotEnergy(long amount) {
        if (!canSpendShotEnergy(amount)) return false;
        energyStored -= amount;
        return true;
    }

    public boolean trySpendShotEnergy(ModuleInstance module, AutomatedFacility outpost, long amount) {
        if (!trySpendShotEnergy(amount)) return false;
        outpost.markModuleDirty(module.id);
        return true;
    }

    private boolean chargeFrom(AutomatedFacility outpost, long amount) {
        long missing = energyCapacity() - energyStored;
        if (amount <= 0L || missing <= 0L) return false;
        long drawn = Math.min(Math.min(amount, missing), outpost.getEnergyStored());
        if (drawn <= 0L) return false;
        if (!outpost.tryConsumeEnergy(drawn)) {
            throw new IllegalStateException("HAMMER charge became inconsistent: requested " + drawn + " EU");
        }
        energyStored += drawn;
        return true;
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
        setEnergyStored(energyStored);
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
