package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

/**
 * A virtual, hierarchical distributed inventory aggregating child nodes and leaf
 * stores (inventories/tanks) behind a unified query and mutation API.
 *
 * <p>
 * <b>Filtering:</b> Filters define what a subtree can store, pruning both
 * insertion and extraction paths on mismatch.
 *
 * <p>
 * <b>Priority:</b> Sibling-relative. Higher values are preferred for insertion
 * (fill first) and deferred for extraction (drain last).
 *
 * <p>
 * <b>Implementation Contract:</b>
 * <ul>
 * <li>Stores may contain nulls (e.g., unloaded chunks); default methods handle them.</li>
 * <li>Aggregated methods return point-in-time snapshots; cache them if applying multiple predicates.</li>
 * <li>{@link #getChildrenSortedByPriority()} must be cached by implementations to avoid tick-rate allocations.</li>
 * <li>There is no cycle detection/escape, so make sure to keep the graph acyclic
 * <li/>
 * </ul>
 */
public interface IDistributedInventory {

    /**
     * Direct child sub-inventories of this node. Default is empty (leaf node).
     * May contain nulls.
     */
    default List<IDistributedInventory> getChildren() {
        return List.of();
    }

    /**
     * Direct item inventories owned by this node. They inherit this node's filter
     * and priority. May contain nulls.
     */
    default List<IInventory> getInventories() {
        return List.of();
    }

    /**
     * Direct fluid tanks owned by this node. May contain nulls.
     */
    default List<IFluidTank> getFluidTanks() {
        return List.of();
    }

    /**
     * Declares what items this subtree can store. Prunes traversal and guards mutations.
     * Should return a constant or cached value.
     */
    default ResourceFilter<ItemStackWrapper> getItemFilter() {
        return ResourceFilter.forItems();
    }

    /**
     * Declares what fluids this subtree can store.
     *
     * @see #getItemFilter()
     */
    default ResourceFilter<FluidKey> getFluidFilter() {
        return ResourceFilter.forFluids();
    }

    /**
     * Sibling-relative priority. Higher = inserted first, extracted last.
     * Ties follow iteration order in {@link #getChildren()}.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Children sorted by descending priority. Implementations must cache this
     * list to avoid allocations on every tick.
     */
    default List<IDistributedInventory> getChildrenSortedByPriority() {
        List<IDistributedInventory> children = getChildren();
        if (children.isEmpty()) return children;
        return children.stream()
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingInt(IDistributedInventory::getPriority)
                    .reversed())
            .collect(Collectors.toList());
    }

    /**
     * Returns a snapshot mapping each distinct item to its total count in this subtree.
     */
    default Map<ItemStackWrapper, Long> aggregatedItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (IDistributedInventory child : getChildren()) {
            if (child == null) continue;
            child.aggregatedItems()
                .forEach((k, v) -> result.merge(k, v, Long::sum));
        }
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null) {
                    ItemStackWrapper key = ItemStackWrapper.of(stack);
                    if (key != null) result.merge(key, (long) stack.stackSize, Long::sum);
                }
            }
        }
        return result;
    }

    /**
     * Returns a snapshot mapping each distinct fluid to its total volume (mB) in this subtree.
     */
    default Map<FluidKey, Long> aggregatedFluids() {
        Map<FluidKey, Long> result = new LinkedHashMap<>();
        for (IDistributedInventory child : getChildren()) {
            if (child == null) continue;
            child.aggregatedFluids()
                .forEach((k, v) -> result.merge(k, v, Long::sum));
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null) continue;
            FluidStack fluid = tank.getFluid();
            if (fluid != null) result.merge(FluidKey.of(fluid), (long) fluid.amount, Long::sum);
        }
        return result;
    }

    /** Total count of the item in this subtree. */
    default long getItemAmount(ItemStackWrapper item) {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.getItemAmount(item);
        }
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null && item.equals(ItemStackWrapper.of(stack))) total += stack.stackSize;
            }
        }
        return total;
    }

    /** Total volume (mB) of the fluid in this subtree. */
    default long getFluidAmount(FluidKey fluid) {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.getFluidAmount(fluid);
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null) continue;
            FluidStack contents = tank.getFluid();
            if (contents != null && fluid.equals(FluidKey.of(contents))) total += contents.amount;
        }
        return total;
    }

    /** Total item slots in this subtree. */
    default long totalItemSlots() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemSlots();
        }
        for (IInventory inv : getInventories()) {
            if (inv != null) total += inv.getSizeInventory();
        }
        return total;
    }

    /** Total count of all items stored (sum of stack sizes) in this subtree. */
    default long totalItemsStored() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemsStored();
        }
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null) total += stack.stackSize;
            }
        }
        return total;
    }

    /** Total fluid volume (mB) stored in this subtree. */
    default long totalFluidStored() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalFluidStored();
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank != null) total += tank.getFluidAmount();
        }
        return total;
    }

    /** Total max item capacity (slots * stack limit) in this subtree. */
    default long totalItemCapacity() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemCapacity();
        }
        for (IInventory inv : getInventories()) {
            if (inv != null) total += (long) inv.getSizeInventory() * inv.getInventoryStackLimit();
        }
        return total;
    }

    /** Total fluid capacity (mB) in this subtree. */
    default long totalFluidCapacity() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalFluidCapacity();
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank != null) total += tank.getCapacity();
        }
        return total;
    }

    /**
     * Remaining capacity for the given item in this subtree. Subtrees rejecting
     * the item are skipped.
     */
    default long getFreeItemSpace(ItemStackWrapper item) {
        if (!getItemFilter().test(item)) return 0L;
        long space = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) space += child.getFreeItemSpace(item);
        }
        ItemStack template = item.toStack(1);
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null) {
                    space += Math.min(template.getMaxStackSize(), inv.getInventoryStackLimit());
                } else if (stack.getItem() == item.item() && stack.getItemDamage() == item.meta()
                    && ItemStack.areItemStackTagsEqual(stack, template)) {
                        int limit = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
                        space += Math.max(0, limit - stack.stackSize);
                    }
            }
        }
        return space;
    }

    /**
     * Remaining capacity (mB) for the given fluid in this subtree. Subtrees
     * rejecting the fluid are skipped.
     */
    default long getFreeFluidSpace(FluidKey fluid) {
        if (!getFluidFilter().test(fluid)) return 0L;
        long space = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) space += child.getFreeFluidSpace(fluid);
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null) continue;
            FluidStack contents = tank.getFluid();
            if (contents == null || FluidKey.of(contents)
                .equals(fluid)) {
                space += tank.getCapacity() - tank.getFluidAmount();
            }
        }
        return space;
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    /**
     * Dispatches to item or fluid mutation based on key type.
     *
     * @param delta positive to insert, negative to extract
     * @return amount actually transferred
     */
    default <T extends InventoryKey> long updateContents(T key, int delta) {
        return key.isItem() ? updateItems((ItemStackWrapper) key, delta) : updateFluids((FluidKey) key, delta);
    }

    /**
     * Inserts (delta > 0) or extracts (delta < 0) an item within this subtree.
     * Inserts in descending priority; extracts in ascending priority. Mismatches
     * short-circuit immediately.
     *
     * @return amount transferred, in [0, |delta|]
     */
    default long updateItems(ItemStackWrapper item, int delta) {
        if (item == null || delta == 0) return 0L;
        if (!getItemFilter().test(item)) return 0L;
        return delta > 0 ? insertItems(item, delta) : extractItems(item, -delta);
    }

    private long insertItems(ItemStackWrapper item, int target) {
        long transferred = 0;
        ItemStack template = item.toStack(1);

        // 1. Child insertion (descending priority)
        for (IDistributedInventory child : getChildrenSortedByPriority()) {
            if (transferred >= target) break;
            if (child == null || !child.getItemFilter()
                .test(item)) continue;
            transferred += child.updateItems(item, (int) (target - transferred));
        }

        // 2. Leaf insertion
        for (IInventory inv : getInventories()) {
            if (inv == null || transferred >= target) continue;
            boolean dirty = false;

            // Pass A: Top-up matching stacks to prevent fragmentation
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null || stack.getItem() != item.item()
                    || stack.getItemDamage() != item.meta()
                    || !ItemStack.areItemStackTagsEqual(stack, template)) continue;
                int limit = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
                int space = limit - stack.stackSize;
                if (space > 0) {
                    int toAdd = (int) Math.min(target - transferred, space);
                    stack.stackSize += toAdd;
                    transferred += toAdd;
                    dirty = true;
                }
            }

            // Pass B: Fill empty slots
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                if (inv.getStackInSlot(s) != null) continue;
                int maxSize = Math.min(template.getMaxStackSize(), inv.getInventoryStackLimit());
                int toAdd = (int) Math.min(target - transferred, maxSize);
                inv.setInventorySlotContents(s, item.toStack(toAdd));
                transferred += toAdd;
                dirty = true;
            }
            if (dirty) inv.markDirty();
        }
        return transferred;
    }

    private long extractItems(ItemStackWrapper item, int target) {
        long transferred = 0;

        // 1. Child extraction (ascending priority)
        List<IDistributedInventory> sorted = getChildrenSortedByPriority();
        for (int i = sorted.size() - 1; i >= 0 && transferred < target; i--) {
            IDistributedInventory child = sorted.get(i);
            if (child == null) continue;
            transferred += child.updateItems(item, -(int) (target - transferred));
        }

        // 2. Leaf extraction
        for (IInventory inv : getInventories()) {
            if (inv == null || transferred >= target) continue;
            boolean dirty = false;
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null || !item.equals(ItemStackWrapper.of(stack))) continue;
                int toRemove = (int) Math.min(target - transferred, stack.stackSize);
                stack.stackSize -= toRemove;
                transferred += toRemove;
                dirty = true;
                if (stack.stackSize <= 0) inv.setInventorySlotContents(s, null);
            }
            if (dirty) inv.markDirty();
        }
        return transferred;
    }

    /**
     * Inserts (delta > 0) or extracts (delta < 0) a fluid within this subtree.
     *
     * @return volume transferred (mB)
     * @see #updateItems(ItemStackWrapper, int) for priority and traversal semantics
     */
    default long updateFluids(FluidKey fluid, int delta) {
        if (fluid == null || delta == 0) return 0L;
        if (!getFluidFilter().test(fluid)) return 0L;
        return delta > 0 ? insertFluids(fluid, delta) : extractFluids(fluid, -delta);
    }

    private long insertFluids(FluidKey fluid, int target) {
        long transferred = 0;

        for (IDistributedInventory child : getChildrenSortedByPriority()) {
            if (transferred >= target) break;
            if (child == null || !child.getFluidFilter()
                .test(fluid)) continue;
            transferred += child.updateFluids(fluid, (int) (target - transferred));
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null || transferred >= target) continue;
            FluidStack contents = tank.getFluid();
            if (contents != null && !FluidKey.of(contents)
                .equals(fluid)) continue; // Fluid mismatch
            int amount = (int) Math.min(target - transferred, Integer.MAX_VALUE);
            transferred += tank.fill(fluid.toStack(amount), true);
        }
        return transferred;
    }

    private long extractFluids(FluidKey fluid, int target) {
        long transferred = 0;

        List<IDistributedInventory> sorted = getChildrenSortedByPriority();
        for (int i = sorted.size() - 1; i >= 0 && transferred < target; i--) {
            IDistributedInventory child = sorted.get(i);
            if (child == null) continue;
            transferred += child.updateFluids(fluid, -(int) (target - transferred));
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null || transferred >= target) continue;
            FluidStack contents = tank.getFluid();
            if (contents == null || !fluid.equals(FluidKey.of(contents))) continue;
            int toDrain = (int) Math.min(target - transferred, contents.amount);
            FluidStack drained = tank.drain(toDrain, true);
            if (drained != null) transferred += drained.amount;
        }
        return transferred;
    }

    /**
     * Marks this node and all descendant stores dirty to schedule persistence or network sync.
     */
    default void markDirty() {
        for (IDistributedInventory child : getChildren()) {
            if (child != null) child.markDirty();
        }
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.markDirty();
        }
    }

    /**
     * Finds all non-null leaf inventories matching the condition in this subtree.
     */
    default List<IInventory> filterInventories(ResourceFilter<IInventory> condition) {
        List<IInventory> result = new ArrayList<>();
        for (IDistributedInventory child : getChildren()) {
            if (child != null) result.addAll(child.filterInventories(condition));
        }
        for (IInventory inv : getInventories()) {
            if (inv != null && condition.test(inv)) result.add(inv);
        }
        return result;
    }

    /**
     * Finds all non-null leaf tanks matching the condition in this subtree.
     */
    default List<IFluidTank> filterTanks(ResourceFilter<IFluidTank> condition) {
        List<IFluidTank> result = new ArrayList<>();
        for (IDistributedInventory child : getChildren()) {
            if (child != null) result.addAll(child.filterTanks(condition));
        }
        for (IFluidTank tank : getFluidTanks()) {
            if (tank != null && condition.test(tank)) result.add(tank);
        }
        return result;
    }

    /**
     * Returns a filtered view of the aggregated item snapshot.
     */
    default Map<ItemStackWrapper, Long> filterItems(ResourceFilter<ItemStackWrapper> predicate) {
        return aggregatedItems().entrySet()
            .stream()
            .filter(e -> predicate.test(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a filtered view of the aggregated fluid snapshot.
     */
    default Map<FluidKey, Long> filterFluids(ResourceFilter<FluidKey> predicate) {
        return aggregatedFluids().entrySet()
            .stream()
            .filter(e -> predicate.test(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns items with quantities strictly below their defined threshold.
     * Items missing from the threshold map are omitted.
     */
    default Map<ItemStackWrapper, Long> getItemsBelowThreshold(Map<ItemStackWrapper, Long> thresholds) {
        Map<ItemStackWrapper, Long> snapshot = aggregatedItems();
        return thresholds.entrySet()
            .stream()
            .filter(e -> snapshot.getOrDefault(e.getKey(), 0L) < e.getValue())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> snapshot.getOrDefault(e.getKey(), 0L)));
    }

    /**
     * Fluid fill ratio in [0.0, 1.0]. Returns 0.0 if total capacity is zero.
     */
    default double fluidFillFactor() {
        long capacity = totalFluidCapacity();
        return capacity == 0L ? 0.0 : (double) totalFluidStored() / capacity;
    }

    /**
     * Item fill ratio in [0.0, 1.0] evaluated against absolute slot capacity.
     * Returns 0.0 if total capacity is zero.
     */
    default double itemFillFactor() {
        long capacity = totalItemCapacity();
        return capacity == 0L ? 0.0 : (double) totalItemsStored() / capacity;
    }
}
