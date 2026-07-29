package com.gtnewhorizons.galaxia.compat;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OreMixes;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.OreMixBuilder;

public final class GTCompat {

    private static final Map<String, ItemStack> GT_ORE_CACHE = new HashMap<>();
    private static final Set<String> GT_ORE_FAILURES = new HashSet<>();

    private GTCompat() {}

    public static List<String> getGtVeinOres(@Nonnull String veinId) {
        if (!isGregTech5UnofficialNewHorizonsLoaded() || veinId.isEmpty()) return List.of();

        OreMixes oreMix = null;
        for (OreMixes mix : OreMixes.values()) {
            OreMixBuilder builder = mix.oreMixBuilder;
            if (builder != null && veinId.equals(builder.oreMixName)) {
                oreMix = mix;
                break;
            }
        }
        if (oreMix == null) return List.of();

        OreMixBuilder builder = oreMix.oreMixBuilder;
        if (builder == null) return List.of();

        List<String> ores = new ArrayList<>();
        ores.add(getMaterialName(builder.primary));
        ores.add(getMaterialName(builder.secondary));
        ores.add(getMaterialName(builder.between));
        ores.add(getMaterialName(builder.sporadic));
        ores.removeIf(s -> s == null || s.isEmpty());
        return Collections.unmodifiableList(ores);
    }

    private static String getMaterialName(Object material) {
        if (material == null) return "";
        try {
            Materials mat = (Materials) material;
            String internalName = mat.getInternalName();
            if (internalName != null && !internalName.isEmpty()) return internalName;
            String localizedName = mat.getLocalizedName();
            if (localizedName != null && !localizedName.isEmpty()) return localizedName;
        } catch (Exception ignored) {}
        return material.toString();
    }

    public static List<ItemStack> getGtVeinOreStacks(@Nonnull String... veinIDs) {
        return Arrays.stream(veinIDs)
            .filter(id -> id != null && !id.isEmpty())
            .map(GTCompat::getGtVeinOres)
            .flatMap(
                ores -> ores.stream()
                    .map(GTCompat::getGtOreStack))
            .filter(stack -> stack != null)
            .collect(Collectors.toList());
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
            stack = stack.copy();
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
