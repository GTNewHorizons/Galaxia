package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public class ModuleMiner extends AutomatedOutpostModule {

    public static final OutpostModuleKind KIND = OutpostModuleKind.MINER;

    // spotless:off
    public final static Map<ItemStack, Long> constructionCost = new HashMap<>() {{
        put(new ItemStack(Items.diamond), 8L);
        put(new ItemStack(Items.gold_ingot), 64L);
    }};
    // spotless:on

    // TODO: Maybe not string but itemstackwrapper?
    public List<String> blacklistedItemKeys;

    public static final long BASE_ENERGY_CAPACITY = 2000L;
    public static final int POWER_DRAW_EU_PER_TICK = 128;
    public static final int COOLDOWN_TICKS = 20;

    private boolean copySettingsToOtherMiners = false;

    @Override
    public void apply(AutomatedOutpost outpost) {
        GalaxiaCelestialAPI.get(outpost.celestialObjectId)
            .ifPresent(registration -> {
                List<ItemStack> ores = registration.properties()
                    .ores();
                if (ores.isEmpty()) return;
                ItemStack chosen = ores.get(RANDOM.nextInt(ores.size()));
                if (isBlacklisted(ItemStackWrapper.of(chosen))) return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    public ModuleMiner(List<String> blacklistedItemKeys) {
        super(BASE_ENERGY_CAPACITY, POWER_DRAW_EU_PER_TICK, COOLDOWN_TICKS);
        this.blacklistedItemKeys = blacklistedItemKeys;
    }

    public static ModuleMiner getDefault() {
        return new ModuleMiner(List.of());
    }

    public boolean isBlacklisted(ItemStackWrapper item) {
        return blacklistedItemKeys.contains(item.toKey());
    }

    public boolean isBlacklisted(@Nonnull String item) {
        return blacklistedItemKeys.contains(item);
    }

    public ModuleMiner withAddedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || blacklistedItemKeys.contains(itemKey)) return this;
        blacklistedItemKeys.add(itemKey);
        return this;
    }

    public ModuleMiner withRemovedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || !blacklistedItemKeys.contains(itemKey)) return this;
        blacklistedItemKeys.remove(itemKey);
        return this;
    }

    public ModuleMiner withCopySettingsToOtherMiners(boolean enabled) {
        this.copySettingsToOtherMiners = enabled;
        return this;
    }

    public boolean getCopySettingsToOtherMiners() {
        return this.copySettingsToOtherMiners;
    }

    @Override
    public Map<ItemStack, Long> getConstructionCost() {
        return constructionCost;
    }

    @Override
    public OutpostModuleKind getKind() {
        return KIND;
    }
}
