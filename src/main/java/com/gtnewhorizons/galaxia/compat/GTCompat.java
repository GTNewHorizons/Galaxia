package com.gtnewhorizons.galaxia.compat;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OreMixes;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.SmallOres;
import gregtech.api.enums.StoneCategory;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.OreMixBuilder;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;

public final class GTCompat {

    private static final Map<String, ItemStack> GT_ORE_CACHE = new HashMap<>();
    private static final Set<String> GT_ORE_FAILURES = new HashSet<>();

    private GTCompat() {}

    public static List<ItemStack> getGtOreDepositStacks(@Nonnull String... depositIds) {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return List.of();

        List<ItemStack> ores = new ArrayList<>();
        for (String depositId : depositIds) {
            if (depositId == null || depositId.isEmpty()) continue;
            if (depositId.startsWith("ore.small.")) {
                ItemStack stack = getSmallOreStack(depositId);
                if (stack != null) ores.add(stack);
                continue;
            }
            for (OreMixes mix : OreMixes.values()) {
                OreMixBuilder builder = mix.oreMixBuilder;
                if (builder == null || !depositId.equals(builder.oreMixName)) continue;
                OrePrefixes prefix = builder.stoneCategories.contains(StoneCategory.Ice) ? OrePrefixes.orePackedIce
                    : OrePrefixes.ore;
                for (var material : new IOreMaterial[] { builder.primary, builder.secondary, builder.between,
                    builder.sporadic }) {
                    if (material == null) continue;
                    ItemStack stack = material.getPart(prefix, 1);
                    if (stack != null) ores.add(stack);
                }
                break;
            }
        }
        return ores;
    }

    private static ItemStack getSmallOreStack(String depositId) {
        for (SmallOres ore : SmallOres.values()) {
            var builder = ore.smallOreBuilder;
            if (!depositId.equals(builder.smallOreName)) continue;
            try (OreInfo<IOreMaterial> info = OreInfo.getNewInfo()) {
                info.material = builder.ore;
                info.stoneType = StoneType.Stone;
                info.isSmall = true;
                return OreManager.getStack(info, 1);
            }
        }
        return null;
    }

    public static List<ItemStack> getGtOreStacks(@Nonnull String... materialNames) {
        List<ItemStack> pool = new ArrayList<>(materialNames.length);
        for (String materialName : materialNames) {
            ItemStack stack;
            try {
                stack = getGtOreStack(materialName);
            } catch (ClassCastException ignored) {
                stack = null;
            }
            if (stack == null) continue;
            stack.stackSize = 1;
            pool.add(stack);
        }
        return pool;
    }

    public static ItemStack getGtOreStack(String materialName) {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return null;
        if (materialName == null || materialName.isEmpty()) return null;

        ItemStack cached = GT_ORE_CACHE.get(materialName);
        if (cached != null) return cached.copy();
        if (GT_ORE_FAILURES.contains(materialName)) return null;

        ItemStack unified = getUnifiedGtStack(materialName);
        if (unified != null) {
            return cacheResolvedGtOre(materialName, unified, "GT_OreDictUnificator prefix ore");
        }

        GT_ORE_FAILURES.add(materialName);
        Galaxia.LOG.warn("Failed to resolve GT ore stack for material {}", materialName);
        return null;
    }

    private static ItemStack cacheResolvedGtOre(String materialName, ItemStack stack, String resolutionPath) {
        ItemStack cached = stack.copy();
        GT_ORE_CACHE.put(materialName, cached);
        GT_ORE_FAILURES.remove(materialName);
        Galaxia.LOG.info("Resolved GT ore material {} via {}", materialName, resolutionPath);
        return cached.copy();
    }

    private static ItemStack getUnifiedGtStack(String materialName) {
        return GTOreDictUnificator.get(OrePrefixes.ore, Materials.get(materialName), 1);
    }
}
