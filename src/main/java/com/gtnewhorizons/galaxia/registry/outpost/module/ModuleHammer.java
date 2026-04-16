package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;

public class ModuleHammer extends AutomatedOutpostModule implements IHammer {

    public static final OutpostModuleKind KIND = OutpostModuleKind.HAMMER;

    // spotless:off
    public final static Map<ItemStack, Long> constructionCost = new HashMap<ItemStack, Long>() {{
        put(new ItemStack(Items.iron_ingot), 8L);
        put(new ItemStack(Items.gold_ingot), 64L);
    }};
    // spotless:on

    public static final long BASE_ENERGY_CAPACITY = 1000L;
    public static final int MAX_BATCH_SIZE = 64;
    public static final int POWER_DRAW_EU_PER_TICK = 10;
    public static final int COOLDOWN_TICKS = 20;

    private AllowShootingConfig config;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private boolean canFire = false;

    public ModuleHammer(@Nonnull AllowShootingConfig config,
        @Nonnull OrbitalTransferPlanner.RoutePriority routePriority) {
        super(BASE_ENERGY_CAPACITY, POWER_DRAW_EU_PER_TICK, COOLDOWN_TICKS);
        this.config = config;
        this.routePriority = routePriority;
    }

    public static ModuleHammer getDefault() {
        return new ModuleHammer(AllowShootingConfig.ALWAYS, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    @Override
    public Map<ItemStack, Long> getConstructionCost() {
        return constructionCost;
    }

    @Override
    public OutpostModuleKind getKind() {
        return KIND;
    }

    @Override
    protected void apply(AutomatedOutpost outpost) {
        this.canFire = true;
    }

    @Override
    public AllowShootingConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(AllowShootingConfig cfg) {
        this.config = cfg;
    }

    @Override
    public OrbitalTransferPlanner.RoutePriority getRoutePriority() {
        return routePriority;
    }

    @Override
    public void setPriority(OrbitalTransferPlanner.RoutePriority priority) {
        this.routePriority = priority;
    }

    @Override
    public boolean canFire() {
        return this.canFire;
    }

    @Override
    public void fire() {
        this.canFire = false;
    }

    @Override
    public int maxBatchSize() {
        return MAX_BATCH_SIZE;
    }

    @Override
    public boolean getPlanetaryHandling() {
        return true;
    }

    @Override
    public boolean getCrossPlanetaryCapability() {
        return false;
    }
}
