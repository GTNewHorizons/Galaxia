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
 * A virtual, <em>hierarchical</em>, distributed inventory that aggregates child
 * {@link IDistributedInventory} nodes and leaf-level {@link IInventory} /
 * {@link IFluidTank} sources behind a unified query and mutation API.
 *
 * <h2>Tree model</h2>
 * <p>
 * Each node may hold:
 * <ul>
 * <li><b>Children</b> — other {@code IDistributedInventory} nodes, each
 * carrying their own filter and priority (see below).</li>
 * <li><b>Leaf stores</b> — flat {@link IInventory} / {@link IFluidTank} lists
 * directly owned by this node, sharing its filter and priority.</li>
 * </ul>
 * A node with no children and at least one leaf store is a <em>leaf node</em>.
 * A node whose {@link #getChildren()} returns the default empty list is
 * trivially a leaf node — existing flat implementations therefore require no
 * structural changes.
 *
 * <h2>Filter semantics</h2>
 * <p>
 * {@link #getItemFilter()} / {@link #getFluidFilter()} declare what this
 * node's subtree is <em>willing to store</em>. They serve two roles:
 * <ol>
 * <li>The <em>parent</em> checks a child's filter before delegating into
 * it, pruning the entire subtree when the filter rejects the resource.</li>
 * <li>Each node's own mutation methods check the filter at entry,
 * short-circuiting with {@code 0} on mismatch.</li>
 * </ol>
 * <p>
 * Assuming filters were correctly enforced during all prior insertions they
 * may also legitimately prune <em>extraction</em> paths (a node that only
 * stores iron cannot contain gold). Override {@link #updateItems} /
 * {@link #updateFluids} if your implementation needs looser extraction
 * semantics (e.g. the filter changed after items were already stored).
 *
 * <h2>Priority semantics</h2>
 * <p>
 * {@link #getPriority()} is a sibling-relative integer. Higher values are
 * preferred for <em>insertion</em> (fill the most-important node first) and
 * deferred for <em>extraction</em> (drain the least-important node first,
 * keeping high-priority stock intact).
 *
 * <h2>Implementation contract</h2>
 * <ul>
 * <li>{@link #getInventories()} and {@link #getFluidTanks()} may contain
 * {@code null} entries (e.g. unloaded chunks); all default methods guard
 * against this.</li>
 * <li>{@link #aggregatedItems()} and {@link #aggregatedFluids()} return
 * point-in-time snapshots; capture once rather than calling repeatedly
 * when applying multiple predicates.</li>
 * <li>{@link #getChildrenSortedByPriority()} <b>must be cached</b> by
 * implementations; the default re-sorts on every call and is unsuitable
 * for tick-rate code.</li>
 * </ul>
 */
public interface IDistributedInventory {

    // =========================================================================
    // Tree structure
    // =========================================================================

    /**
     * Direct child sub-inventories of this node, in no particular order.
     * <p>
     * The default returns an empty list, making this node a <em>leaf</em>.
     * Override to build composite nodes. {@code null} entries are tolerated.
     */
    default List<IDistributedInventory> getChildren() {
        return List.of();
    }

    /**
     * Direct leaf-level item inventories owned by this node.
     * <p>
     * These are not children — they carry no independent filter or priority;
     * they inherit this node's semantics. May contain {@code null} entries.
     */
    default List<IInventory> getInventories() {
        return List.of();
    }

    /**
     * Direct leaf-level fluid tanks owned by this node.
     * May contain {@code null} entries.
     */
    default List<IFluidTank> getFluidTanks() {
        return List.of();
    }

    // =========================================================================
    // Node-level filter and priority
    // =========================================================================

    /**
     * Item filter for this node: declares what items this subtree is willing to
     * store.
     * <p>
     * Checked by the parent before delegating and as a fast-exit guard at the
     * top of every mutation path. Implementations should return a constant or
     * cached value.
     */
    default ResourceFilter<ItemStackWrapper> getItemFilter() {
        return ResourceFilter.forItems();
    }

    /**
     * Fluid filter for this node.
     *
     * @see #getItemFilter()
     */
    default ResourceFilter<FluidKey> getFluidFilter() {
        return ResourceFilter.forFluids();
    }

    /**
     * Sibling-relative insertion/extraction priority.
     * Higher values are preferred for insertion; lower values are preferred
     * for extraction. Ties are broken by iteration order within
     * {@link #getChildren()}.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Children sorted by <em>descending</em> priority (insertion order).
     * <p>
     * <b>Implementations must cache this list</b> to avoid sorting allocations
     * on every tick. The default implementation re-sorts on every call.
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

    // =========================================================================
    // Aggregation — recursive snapshots
    // =========================================================================

    /**
     * Returns a snapshot mapping each distinct item identity to its total
     * stored count across this node and all descendants.
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
     * Returns a snapshot mapping each distinct fluid identity to its total
     * stored volume (mB) across this node and all descendants.
     * <p>
     * Uses {@link FluidKey} rather than {@link FluidStack} to ensure a
     * stable, amount-independent key.
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

    // =========================================================================
    // Scalar queries — all recursive
    // =========================================================================

    /** Total stored count of {@code item} across this node and all descendants. */
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

    /** Total stored volume (mB) of {@code fluid} across this node and all descendants. */
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

    /** Total item slots across this node and all descendants. */
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

    /**
     * Total items stored (sum of stack sizes, not slot count) across this
     * node and all descendants.
     */
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

    /** Total fluid volume (mB) stored across this node and all descendants. */
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

    /**
     * Total item capacity (slots × stack-limit) across this node and all
     * descendants.
     */
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

    /** Total fluid capacity (mB) across this node and all descendants. */
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
     * Returns how many additional units of {@code item} could be inserted
     * into this node and all descendants right now.
     * <p>
     * Entire subtrees whose {@link #getItemFilter()} rejects {@code item}
     * are skipped without traversal.
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
     * Returns how many additional mB of {@code fluid} could be inserted into
     * this node and all descendants right now.
     * <p>
     * Entire subtrees whose {@link #getFluidFilter()} rejects {@code fluid}
     * are skipped without traversal.
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
                .equals(fluid)) space += tank.getCapacity() - tank.getFluidAmount();
        }
        return space;
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    /**
     * Dispatches to {@link #updateItems} or {@link #updateFluids} based on
     * key type.
     *
     * @param key   the resource to modify
     * @param delta positive to insert, negative to extract
     * @return the amount actually transferred
     */
    default <T extends InventoryKey> long updateContents(T key, int delta) {
        return key instanceof ItemStackWrapper ? updateItems((ItemStackWrapper) key, delta)
            : updateFluids((FluidKey) key, delta);
    }

    /**
     * Inserts ({@code delta > 0}) or extracts ({@code delta < 0}) the given
     * item across this node and its descendants, respecting priority and filter
     * rules.
     *
     * <p>
     * This node's {@link #getItemFilter()} is checked first; a mismatch
     * short-circuits with {@code 0} without traversing the subtree.
     *
     * <p>
     * <b>Insertion order:</b> children visited in descending-priority order,
     * then this node's own leaf inventories (existing stacks topped up before
     * empty slots are filled).
     * <br>
     * <b>Extraction order:</b> children visited in ascending-priority order
     * (drain low-priority first, preserve high-priority stock), then this
     * node's own leaf inventories.
     *
     * @return amount actually transferred, in {@code [0, |delta|]}.
     */
    default long updateItems(ItemStackWrapper item, int delta) {
        if (item == null || delta == 0) return 0L;
        if (!getItemFilter().test(item)) return 0L;
        return delta > 0 ? insertItems(item, delta) : extractItems(item, -delta);
    }

    private long insertItems(ItemStackWrapper item, int target) {
        long transferred = 0;
        ItemStack template = item.toStack(1);

        // 1. Delegate to children in descending-priority order.
        for (IDistributedInventory child : getChildrenSortedByPriority()) {
            if (transferred >= target) break;
            if (child == null || !child.getItemFilter()
                .test(item)) continue;
            transferred += child.updateItems(item, (int) (target - transferred));
        }

        // 2. Fill leaf inventories owned by this node.
        // Pass A: top-up existing matching stacks (avoids fragmentation).
        for (IInventory inv : getInventories()) {
            if (inv == null || transferred >= target) continue;
            boolean dirty = false;
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
            // Pass B: fill empty slots.
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

        // 1. Delegate to children in ascending-priority order (reverse of insertion).
        List<IDistributedInventory> sorted = getChildrenSortedByPriority();
        for (int i = sorted.size() - 1; i >= 0 && transferred < target; i--) {
            IDistributedInventory child = sorted.get(i);
            if (child == null) continue;
            transferred += child.updateItems(item, -(int) (target - transferred));
        }

        // 2. Extract from leaf inventories owned by this node.
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
     * Inserts ({@code delta > 0}) or extracts ({@code delta < 0}) the given
     * fluid across this node and its descendants, respecting priority and
     * filter rules.
     *
     * <p>
     * This node's {@link #getFluidFilter()} is checked first; a mismatch
     * short-circuits with {@code 0} without traversing the subtree.
     *
     * @return volume actually transferred, in mB.
     * @see #updateItems(ItemStackWrapper, int) for delta semantics and
     *      priority/order guarantees
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
            // Skip tanks already holding a different fluid.
            if (contents != null && !FluidKey.of(contents)
                .equals(fluid)) continue;
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

    // =========================================================================
    // Dirty tracking
    // =========================================================================

    /**
     * Marks the entire tree dirty, scheduling persistence / network sync on
     * all backing stores in this node and all descendants.
     */
    default void markDirty() {
        for (IDistributedInventory child : getChildren()) {
            if (child != null) child.markDirty();
        }
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.markDirty();
        }
    }

    // =========================================================================
    // Filter / search helpers
    // =========================================================================

    /**
     * Returns all non-null leaf inventories (across the entire tree) satisfying
     * {@code condition}. Override with an indexed implementation if available.
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
     * Returns all non-null leaf tanks (across the entire tree) satisfying
     * {@code condition}.
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
     *
     * <p>
     * Captures the snapshot once; prefer calling {@link #aggregatedItems()}
     * yourself when applying multiple predicates to avoid redundant snapshots.
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
     * Returns items whose stored amount is strictly below their threshold as
     * defined in {@code thresholds}. Items absent from {@code thresholds} are
     * <em>not</em> included — "no threshold" means "no alarm".
     */
    default Map<ItemStackWrapper, Long> getItemsBelowThreshold(Map<ItemStackWrapper, Long> thresholds) {
        Map<ItemStackWrapper, Long> snapshot = aggregatedItems();
        return thresholds.entrySet()
            .stream()
            .filter(e -> snapshot.getOrDefault(e.getKey(), 0L) < e.getValue())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> snapshot.getOrDefault(e.getKey(), 0L)));
    }

    // =========================================================================
    // Fill-factor utilities
    // =========================================================================

    /**
     * Fluid fill ratio in {@code [0.0, 1.0]}.
     * Returns {@code 0.0} if total fluid capacity is zero.
     */
    default double fluidFillFactor() {
        long capacity = totalFluidCapacity();
        return capacity == 0L ? 0.0 : (double) totalFluidStored() / capacity;
    }

    /**
     * Item fill ratio in {@code [0.0, 1.0]}.
     * Returns {@code 0.0} if total item capacity is zero.
     * <p>
     * Evaluated against absolute slot capacity, not distinct item count.
     */
    default double itemFillFactor() {
        long capacity = totalItemCapacity();
        return capacity == 0L ? 0.0 : (double) totalItemsStored() / capacity;
    }
}
