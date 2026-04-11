package com.gtnewhorizons.galaxia.registry;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.core.Galaxia;

public final class GTUtility {

    private static final Map<String, ItemStack> RAW_ORE_CACHE = new HashMap<>();
    private static final Set<String> RAW_ORE_FAILURES = new HashSet<>();

    private GTUtility() {}

    /**
     * Gets a stack of the given ore material.
     * 
     * @param materialName The internal name of the GT material (e.g. "Iron")
     * @return The ore stack, or null if not found or GT not present.
     */
    public static ItemStack getOreStack(String materialName) {
        if (!Galaxia.hasGT5U()) return null;
        if (materialName == null || materialName.isEmpty()) return null;

        try {
            Class<?> materialsClass = Class.forName("gregtech.api.enums.Materials");
            Object material = materialsClass.getField(materialName)
                .get(null);

            Class<?> orePrefixesClass = Class.forName("gregtech.api.enums.OrePrefixes");
            Object orePrefix = orePrefixesClass.getField("ore")
                .get(null);

            Method getMethod = orePrefixesClass.getMethod("get", materialsClass);
            return (ItemStack) getMethod.invoke(orePrefix, material);
        } catch (Exception e) {
            Galaxia.LOG.error("Failed to get GT ore stack for material: " + materialName, e);
            return null;
        }
    }

    public static ItemStack getRawOreStack(String materialName) {
        if (!Galaxia.hasGT5U()) return null;
        if (materialName == null || materialName.isEmpty()) return null;

        ItemStack cached = RAW_ORE_CACHE.get(materialName);
        if (cached != null) return cached.copy();
        if (RAW_ORE_FAILURES.contains(materialName)) return null;

        ItemStack unified = getUnifiedGtStack(materialName);
        if (unified != null) {
            return cacheResolvedRawOre(materialName, unified, "GT_OreDictUnificator prefix rawOre");
        }

        String materialKey = sanitizeMaterialKey(materialName);
        String[] oreDictKeys = new String[] { "rawOre" + materialKey };
        for (String oreDictKey : oreDictKeys) {
            List<ItemStack> matches = OreDictionary.getOres(oreDictKey, false);
            if (matches == null || matches.isEmpty()) continue;
            ItemStack match = matches.get(0);
            if (match != null) {
                return cacheResolvedRawOre(materialName, match, "OreDictionary key " + oreDictKey);
            }
        }

        RAW_ORE_FAILURES.add(materialName);
        Galaxia.LOG.warn("Failed to resolve GT raw ore stack for material {}", materialName);
        return null;
    }

    private static String sanitizeMaterialKey(String materialName) {
        return materialName.replaceAll("[^A-Za-z0-9]", "");
    }

    private static ItemStack cacheResolvedRawOre(String materialName, ItemStack stack, String resolutionPath) {
        ItemStack cached = stack.copy();
        RAW_ORE_CACHE.put(materialName, cached);
        RAW_ORE_FAILURES.remove(materialName);
        Galaxia.LOG.info("Resolved GT raw ore material {} via {}", materialName, resolutionPath);
        return cached.copy();
    }

    private static ItemStack getUnifiedGtStack(String materialName) {
        try {
            Class<?> materialsClass = Class.forName("gregtech.api.enums.Materials");
            Object material = materialsClass.getField(materialName)
                .get(null);

            Class<?> orePrefixesClass = Class.forName("gregtech.api.enums.OrePrefixes");
            Object orePrefix = orePrefixesClass.getField("rawOre")
                .get(null);

            Class<?> oreDictUnificatorClass = Class.forName("gregtech.api.util.GT_OreDictUnificator");
            Method getMethod = oreDictUnificatorClass.getMethod("get", orePrefixesClass, materialsClass, long.class);
            Object stack = getMethod.invoke(null, orePrefix, material, 1L);
            return stack instanceof ItemStack itemStack ? itemStack : null;
        } catch (Exception ignored) {
            return null;
        }
    }

}
