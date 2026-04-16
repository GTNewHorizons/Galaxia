package com.gtnewhorizons.galaxia.compat;

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
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OreMixes;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.OreMixBuilder;

public final class GTUtility {

    public static final boolean isGTLoaded = Loader.isModLoaded("gregtech");

    private static final Map<String, ItemStack> RAW_ORE_CACHE = new HashMap<>();
    private static final Set<String> RAW_ORE_FAILURES = new HashSet<>();

    private GTUtility() {}

    public static List<String> getGtVeinOres(@Nonnull String veinId) {
        if (!isGTLoaded || veinId.isEmpty()) return List.of();

        OreMixes oreMix = null;
        for (OreMixes mix : OreMixes.values()) {
            if (mix.name()
                .equals(veinId)) {
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

    public static List<ItemStack> getRawOres(@Nonnull String... veinIDs) {
        return Arrays.stream(veinIDs)
            .filter(id -> id != null && !id.isEmpty())
            .map(GTUtility::getGtVeinOres)
            .flatMap(
                ores -> ores.stream()
                    .map(GTUtility::getRawOreStack))
            .collect(Collectors.toList());
    }

    public static ItemStack getRawOreStack(String materialName) {
        if (!isGTLoaded) return null;
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
        return GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.get(materialName), 1);
    }
}
