package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FacilityFilterState {

    private final ResourceFilter<ItemStackWrapper> itemFilter = ResourceFilter.forItems();
    private final ResourceFilter<FluidKey> fluidFilter = ResourceFilter.forFluids();

    ResourceFilter<ItemStackWrapper> itemFilter() {
        return itemFilter;
    }

    ResourceFilter<FluidKey> fluidFilter() {
        return fluidFilter;
    }

    void add(String key, boolean item, Runnable dirtyMarker) {
        if (key == null) return;
        if (item) itemFilter.add(key);
        else fluidFilter.add(key);
        dirtyMarker.run();
    }

    void remove(String key, boolean item, Runnable dirtyMarker) {
        if (key == null) return;
        if (item) itemFilter.remove(key);
        else fluidFilter.remove(key);
        dirtyMarker.run();
    }

    Map<Boolean, List<String>> snapshot() {
        Map<Boolean, List<String>> result = new LinkedHashMap<>();
        List<String> itemSerialized = itemFilter.serialize();
        if (!itemSerialized.isEmpty()) result.put(true, itemSerialized);
        List<String> fluidSerialized = fluidFilter.serialize();
        if (!fluidSerialized.isEmpty()) result.put(false, fluidSerialized);
        return result;
    }

    void set(List<String> filters, boolean item, Runnable dirtyMarker) {
        if (filters == null) return;
        if (item) itemFilter.load(filters);
        else fluidFilter.load(filters);
        dirtyMarker.run();
    }

    void clear(boolean item, Runnable dirtyMarker) {
        if (item) itemFilter.clear();
        else fluidFilter.clear();
        dirtyMarker.run();
    }
}
