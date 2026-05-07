package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public final class ModuleMiner implements ModuleComponent, IParallelModule {

    public final FacilityModuleKind kind;

    public static final FacilityModuleKind KIND = FacilityModuleKind.MINER;
    private byte parallel = 1;

    private static final Random RANDOM = new java.util.Random();

    public ModuleMiner(FacilityModuleKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
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

    public static boolean shouldVoidOre(@Nonnull AutomatedFacility outpost, String oreKey, int rollPercent) {
        return rollPercent < outpost.minerVoidChancePercent(oreKey);
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
