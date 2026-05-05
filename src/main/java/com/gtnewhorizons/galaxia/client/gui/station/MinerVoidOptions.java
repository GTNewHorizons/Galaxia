package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class MinerVoidOptions {

    private MinerVoidOptions() {}

    static List<Entry> forFacility(AutomatedFacility facility) {
        return GalaxiaCelestialAPI.get(facility.celestialObjectId)
            .<List<Entry>>map(body -> {
                Map<String, Entry> options = new LinkedHashMap<>();
                addOptions(
                    options,
                    body.properties()
                        .ores());
                addOptions(
                    options,
                    body.properties()
                        .getResolvedGtVeinOreStacks());
                return new ArrayList<>(options.values());
            })
            .orElse(List.of());
    }

    private static void addOptions(Map<String, Entry> options, List<ItemStack> ores) {
        for (ItemStack ore : ores) {
            if (ore == null) continue;
            ItemStackWrapper wrapper = ItemStackWrapper.of(ore);
            if (wrapper == null || options.containsKey(wrapper.toKey())) continue;
            ItemStack displayStack = ore.copy();
            displayStack.stackSize = 1;
            options.put(wrapper.toKey(), new Entry(wrapper.toKey(), displayStack.getDisplayName(), displayStack));
        }
    }

    record Entry(String key, String displayName, ItemStack displayStack) {}
}
