package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public final class ModuleMiner implements ModuleComponent, IParallelModule {

    public final FacilityModuleKind kind;

    public static final FacilityModuleKind KIND = FacilityModuleKind.MINER;
    private final List<String> blacklistedItemKeys;
    private boolean copySettingsToOtherMiners;
    private byte parallel = 1;

    private static final Random RANDOM = new java.util.Random();

    public ModuleMiner(FacilityModuleKind kind, List<String> blacklistedItemKeys, boolean copySettingsToOtherMiners) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.blacklistedItemKeys = new ArrayList<>();
        setBlacklist(blacklistedItemKeys);
        this.copySettingsToOtherMiners = copySettingsToOtherMiners;
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
                int totalSize = ores.size() + veinOres.size();
                if (totalSize == 0) return;
                int idx = RANDOM.nextInt(totalSize);
                ItemStack chosen = idx < ores.size() ? ores.get(idx) : veinOres.get(idx - ores.size());
                String oreKey = ItemStackWrapper.of(chosen)
                    .toKey();
                if (shouldVoidOre(outpost, oreKey, RANDOM.nextInt(100))) return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    public static boolean shouldVoidOre(AutomatedFacility outpost, String oreKey, int rollPercent) {
        Objects.requireNonNull(outpost, "outpost");
        requireRollPercent(rollPercent);
        return rollPercent < outpost.minerVoidChancePercent(oreKey);
    }

    public void setBlacklist(List<String> itemKeys) {
        blacklistedItemKeys.clear();
        for (String itemKey : Objects.requireNonNull(itemKeys, "itemKeys")) {
            requireItemKey(itemKey);
            blacklistedItemKeys.add(itemKey);
        }
    }

    public void addToBlacklist(String itemKey) {
        requireItemKey(itemKey);
        if (blacklistedItemKeys.contains(itemKey)) return;
        blacklistedItemKeys.add(itemKey);
    }

    public void removeFromBlacklist(String itemKey) {
        requireItemKey(itemKey);
        if (!blacklistedItemKeys.contains(itemKey)) return;
        blacklistedItemKeys.remove(itemKey);
    }

    public boolean isBlacklisted(String item) {
        return blacklistedItemKeys.contains(requireItemKey(item));
    }

    public List<String> blacklistedItemKeys() {
        return blacklistedItemKeys;
    }

    public boolean copySettingsToOtherMiners() {
        return copySettingsToOtherMiners;
    }

    public void setCopySettingToOtherMiners(boolean newValue) {
        this.copySettingsToOtherMiners = newValue;
    }

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }

    private static String requireItemKey(String itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        if (itemKey.isEmpty()) throw new IllegalArgumentException("itemKey cannot be empty");
        return itemKey;
    }

    private static void requireRollPercent(int rollPercent) {
        if (rollPercent < 0 || rollPercent >= 100) {
            throw new IllegalArgumentException("rollPercent out of range: " + rollPercent);
        }
    }
}
