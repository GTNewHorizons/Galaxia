package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

public interface IFilteredInventory {

    List<ItemStack> getFiltersFor(int i);

    void setFilters(int slot, List<ItemStack> filterList);

    void addFilter(int slot, ItemStack filter);

    void removeFilter(int slot, ItemStack filter);

    void clearFilters(int slot);

    Map<Integer, List<ItemStack>> filtersSnapshot();

    /** True when 'stack' matches at least one filter entry, or the filter list is empty/null. */
    default boolean passesFilter(int inventoryIndex, ItemStack stack) {
        List<ItemStack> filters = getFiltersFor(inventoryIndex);
        if (filters == null || filters.isEmpty()) return true;
        for (ItemStack filter : filters) {
            if (filter != null && filterMatches(filter, stack)) return true;
        }
        return false;
    }

    /** Item + meta match (ignores stack size, but respects NBT if present on the filter). */
    private boolean filterMatches(ItemStack filter, ItemStack stack) {
        if (stack == null || filter == null) return false;
        if (filter.getItem() != stack.getItem()) return false;
        if (filter.getHasSubtypes() && filter.getItemDamage() != stack.getItemDamage()) return false;
        return !filter.hasTagCompound() || ItemStack.areItemStackTagsEqual(filter, stack);
    }

}
