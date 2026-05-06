package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;

public final class ModuleMiner implements ModuleComponent, IParallelModule {

    public final FacilityModuleKind kind;

    public static final FacilityModuleKind KIND = FacilityModuleKind.MINER;
    private byte parallel = 1;
    private MinerSettings localSettings = new MinerSettings();

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
                int totalSize = ores.size() + veinOres.size();
                if (totalSize == 0) return;
                int idx = RANDOM.nextInt(totalSize);
                ItemStack chosen = idx < ores.size() ? ores.get(idx) : veinOres.get(idx - ores.size());
                String oreKey = ItemStackWrapper.of(chosen)
                    .toKey();
                if (shouldVoidOre(instance, outpost, oreKey)) return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    public static boolean shouldVoidOre(@Nonnull ModuleInstance instance, @Nonnull AutomatedFacility outpost,
        String oreKey) {
        return outpost.isMinerOreBlacklisted(instance, oreKey);
    }

    public MinerSettings localSettingsOrNull() {
        return localSettings;
    }

    public MinerSettings requireLocalSettings() {
        if (localSettings == null) {
            throw new IllegalStateException(
                "Miner module has no local settings because it belongs to a settings group");
        }
        return localSettings;
    }

    public void setLocalSettings(@Nonnull MinerSettings localSettings) {
        this.localSettings = localSettings.copy();
    }

    public void clearLocalSettings() {
        this.localSettings = null;
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
