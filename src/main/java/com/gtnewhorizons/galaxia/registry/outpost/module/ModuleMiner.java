package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public final class ModuleMiner implements ModuleComponent {

    public final FacilityModuleKind kind;

    public static final FacilityModuleKind KIND = FacilityModuleKind.MINER;
    private final List<String> blacklistedItemKeys;
    private boolean copySettingsToOtherMiners;

    private static final Random RANDOM = new java.util.Random();

    public ModuleMiner(FacilityModuleKind kind, List<String> blacklistedItemKeys, boolean copySettingsToOtherMiners) {
        this.kind = kind;
        this.blacklistedItemKeys = blacklistedItemKeys;
        this.copySettingsToOtherMiners = copySettingsToOtherMiners;
    }

    public static void generateOre(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleMiner miner = (ModuleMiner) instance.component();
        GalaxiaCelestialAPI.get(outpost.celestialObjectId)
            .ifPresent(registration -> {
                var ores = registration.properties()
                    .ores();
                if (ores.isEmpty()) return;
                ItemStack chosen = ores.get(RANDOM.nextInt(ores.size()));
                if (miner.isBlacklisted(
                    ItemStackWrapper.of(chosen)
                        .toKey()))
                    return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    public void setBlacklist(List<String> itemKeys) {
        blacklistedItemKeys.clear();
        blacklistedItemKeys.addAll(itemKeys);
    }

    public void addToBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || blacklistedItemKeys.contains(itemKey)) return;
        blacklistedItemKeys.add(itemKey);
    }

    public void removeFromBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || !blacklistedItemKeys.contains(itemKey)) return;
        blacklistedItemKeys.remove(itemKey);
    }

    public boolean isBlacklisted(String item) {
        return blacklistedItemKeys.contains(item);
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
}
