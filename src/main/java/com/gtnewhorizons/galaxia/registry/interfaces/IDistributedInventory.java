package com.gtnewhorizons.galaxia.registry.interfaces;

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
 * A virtual, distributed inventory that aggregates multiple {@link IInventory}
 * and {@link IFluidTank} sources behind a unified query and mutation API.
 *
 * <p>
 * Implementors own the concrete aggregation, slot resolution, and mutation
 * logic. Default methods here provide derived query patterns; they are
 * intentionally thin wrappers over the core API so implementors can override
 * them with optimised versions when needed.
 *
 * <p>
 * <b>Implementation contract:</b>
 * <ul>
 * <li>{@link #getInventories()} and {@link #getFluidTanks()} may return lists
 * containing {@code null} entries (e.g. for unloaded chunks); all default
 * methods guard against this.</li>
 * <li>{@link #aggregatedItems()} and {@link #aggregatedFluids()} return
 * <em>snapshots</em>; callers should not assume they reflect concurrent
 * mutations. Callers performing multiple filter passes should capture the
 * snapshot once rather than calling these methods repeatedly.</li>
 * </ul>
 */
public interface IDistributedInventory {

    /**
     * Returns the ordered list of backing item inventories.
     * Indices here correspond to the {@code idx} parameter of
     * {@link #getItemFilter(int)}
     */
    List<IInventory> getInventories();

    /**
     * Returns the ordered list of backing fluid tanks.
     * Indices here correspond to the {@code idx} parameter of
     * {@link #getFluidFilter(int)}
     */
    List<IFluidTank> getFluidTanks();

    /**
     * Returns the item predicate governing what may enter the inventory at
     * {@code idx}. Implementations should return a constant or cached value
     * rather than allocating on every call.
     */
    default ResourceFilter<ItemStackWrapper> getItemFilter(int idx) {
        return ResourceFilter.forItems();
    }

    /**
     * Returns the fluid predicate governing what may enter the tank at
     * {@code idx}.
     */
    default ResourceFilter<FluidKey> getFluidFilter(int idx) {
        return ResourceFilter.forFluids();
    }

    /**
     * Returns a snapshot mapping each distinct item identity to its total
     * stored count across all backing inventories.
     */
    default Map<ItemStackWrapper, Long> aggregatedItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null) {
                    ItemStackWrapper key = ItemStackWrapper.of(stack);
                    if (key != null) {
                        result.merge(key, (long) stack.stackSize, Long::sum);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns a snapshot mapping each distinct fluid identity to its total
     * stored volume (in mB) across all backing tanks.
     *
     * <p>
     * Uses {@link FluidKey} rather than {@link FluidStack} to ensure a
     * stable, amount-independent key.
     */
    default Map<FluidKey, Long> aggregatedFluids() {
        Map<FluidKey, Long> result = new LinkedHashMap<>();
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null) continue;
            FluidStack fluid = tank.getFluid();
            if (fluid != null) {
                result.merge(FluidKey.of(fluid), (long) fluid.amount, Long::sum);
            }
        }
        return result;
    }

    /** Total stored count of the given item across all inventories. */
    default long getItemAmount(ItemStackWrapper item) {
        long total = 0;
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null && item.equals(ItemStackWrapper.of(stack))) {
                    total += stack.stackSize;
                }
            }
        }
        return total;
    }

    /** Total stored volume (mB) of the given fluid across all tanks. */
    default long getFluidAmount(FluidKey fluid) {
        long total = 0;
        for (IFluidTank tank : getFluidTanks()) {
            if (tank == null) continue;
            FluidStack contents = tank.getFluid();
            if (contents != null && fluid.equals(FluidKey.of(contents))) {
                total += contents.amount;
            }
        }
        return total;
    }

    /** Total number of item slots across all backing inventories. */
    default long totalItemSlots() {
        long total = 0;
        for (IInventory inv : getInventories()) {
            if (inv != null) total += inv.getSizeInventory();
        }
        return total;
    }

    /**
     * Total number of items stored across all backing inventories.
     * This is the sum of stack sizes, not the number of occupied slots.
     */
    default long totalItemsStored() {
        long total = 0;
        for (IInventory inv : getInventories()) {
            if (inv == null) continue;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null) total += stack.stackSize;
            }
        }
        return total;
    }

    /** Total fluid volume (mB) stored across all backing tanks. */
    default long totalFluidStored() {
        long total = 0;
        for (IFluidTank tank : getFluidTanks()) {
            if (tank != null) total += tank.getFluidAmount();
        }
        return total;
    }

    /**
     * Total item slot capacity across all inventories (same as {@link #totalItemSlots()} for most implementations, but
     * may differ when slots have per-slot stack-size limits).
     */
    default long totalItemCapacity() {
        long total = 0;
        for (IInventory inv : getInventories()) {
            if (inv != null) {
                total += (long) inv.getSizeInventory() * inv.getInventoryStackLimit();
            }
        }
        return total;
    }

    /** Total fluid capacity (mB) across all tanks. */
    default long totalFluidCapacity() {
        long total = 0;
        for (IFluidTank tank : getFluidTanks()) {
            if (tank != null) total += tank.getCapacity();
        }
        return total;
    }

    /**
     * Returns the number of additional units of {@code item} that could be
     * inserted across all inventories right now.
     */
    default long getFreeItemSpace(ItemStackWrapper item) {
        long space = 0;
        List<IInventory> inventories = getInventories();
        ItemStack template = item.toStack(1);
        for (int i = 0; i < inventories.size(); i++) {
            IInventory inv = inventories.get(i);
            if (inv == null || !getItemFilter(i).test(item)) continue;
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
     * Returns the number of additional mB of {@code fluid} that could be
     * inserted across all tanks right now.
     */
    default long getFreeFluidSpace(FluidKey fluid) {
        long space = 0;
        List<IFluidTank> tanks = getFluidTanks();
        for (int i = 0; i < tanks.size(); i++) {
            IFluidTank tank = tanks.get(i);
            if (tank == null || !getFluidFilter(i).test(fluid)) continue;
            FluidStack contents = tank.getFluid();
            if (contents == null || FluidKey.of(contents)
                .equals(fluid)) {
                space += tank.getCapacity() - tank.getFluidAmount();
            }
        }
        return space;
    }

    default <T extends InventoryKey> long udpateContents(T key, int delta) {
        return key instanceof ItemStackWrapper ? updateItems((ItemStackWrapper) key, delta)
            : updateFluids((FluidKey) key, delta);
    }

    /**
     * Inserts (positive {@code delta}) or extracts (negative {@code delta})
     * the given item across the distributed inventory.
     *
     * @return the amount actually transferred, always in {@code [0, |delta|]}.
     *         A return value less than {@code |delta|} means the operation was
     *         partially fulfilled due to capacity or stock constraints.
     */
    default long updateItems(ItemStackWrapper item, int delta) {
        if (item == null || delta == 0) return 0L;
        if (delta > 0) {
            return insertItems(item, delta);
        } else {
            return extractItems(item, -delta);
        }
    }

    private long insertItems(ItemStackWrapper item, int target) {
        long transferred = 0;
        List<IInventory> inventories = getInventories();
        ItemStack template = item.toStack(1);
        for (int i = 0; i < inventories.size() && transferred < target; i++) {
            IInventory inv = inventories.get(i);
            if (inv == null || !getItemFilter(i).test(item)) continue;
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null) continue;
                if (stack.getItem() == item.item() && stack.getItemDamage() == item.meta()
                    && ItemStack.areItemStackTagsEqual(stack, template)) {
                    int limit = Math.min(stack.getMaxStackSize(), inv.getInventoryStackLimit());
                    int space = limit - stack.stackSize;
                    if (space > 0) {
                        int toAdd = (int) Math.min(target - transferred, space);
                        stack.stackSize += toAdd;
                        transferred += toAdd;
                    }
                }
            }
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack != null) continue;
                int maxSize = Math.min(template.getMaxStackSize(), inv.getInventoryStackLimit());
                int toAdd = (int) Math.min(target - transferred, maxSize);
                ItemStack newStack = item.toStack(toAdd);
                inv.setInventorySlotContents(s, newStack);
                transferred += toAdd;
            }
            if (transferred > 0) inv.markDirty();
        }
        return transferred;
    }

    private long extractItems(ItemStackWrapper item, int target) {
        long transferred = 0;
        for (IInventory inv : getInventories()) {
            if (inv == null || transferred >= target) continue;
            boolean dirty = false;
            for (int s = 0; s < inv.getSizeInventory() && transferred < target; s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null) continue;
                ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
                if (item.equals(wrapper)) {
                    long toRemove = Math.min(target - transferred, stack.stackSize);
                    stack.stackSize -= (int) toRemove;
                    transferred += toRemove;
                    dirty = true;
                    if (stack.stackSize <= 0) {
                        inv.setInventorySlotContents(s, null);
                    }
                }
            }
            if (dirty) inv.markDirty();
        }
        return transferred;
    }

    /**
     * Inserts or extracts the given fluid across the distributed inventory.
     *
     * @return the volume actually transferred, in mB.
     * @see #updateItems(ItemStackWrapper, int) for delta semantics
     */
    default long updateFluids(FluidKey fluid, int delta) {
        if (fluid == null || delta == 0) return 0L;
        if (delta > 0) {
            return insertFluids(fluid, delta);
        } else {
            return extractFluids(fluid, -delta);
        }
    }

    private long insertFluids(FluidKey fluid, int target) {
        long transferred = 0;
        List<IFluidTank> tanks = getFluidTanks();
        for (int i = 0; i < tanks.size() && transferred < target; i++) {
            IFluidTank tank = tanks.get(i);
            if (tank == null || !getFluidFilter(i).test(fluid)) continue;
            int amount = (int) Math.min(target - transferred, Integer.MAX_VALUE);
            FluidStack fs = fluid.toStack(amount);
            int filled = tank.fill(fs, true);
            transferred += filled;
        }
        return transferred;
    }

    private long extractFluids(FluidKey fluid, int target) {
        long transferred = 0;
        List<IFluidTank> tanks = getFluidTanks();
        for (int i = 0; i < tanks.size() && transferred < target; i++) {
            IFluidTank tank = tanks.get(i);
            if (tank == null) continue;
            FluidStack contents = tank.getFluid();
            if (contents == null) continue;
            if (!fluid.equals(FluidKey.of(contents))) continue;
            int toDrain = (int) Math.min(target - transferred, contents.amount);
            FluidStack drained = tank.drain(toDrain, true);
            if (drained != null) transferred += drained.amount;
        }
        return transferred;
    }

    /**
     * Marks the entire distributed inventory dirty, scheduling a persistence
     * or network sync pass on all backing stores.
     */
    default void markDirty() {
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.markDirty();
        }
    }

    /**
     * Marks a specific sub-store dirty without touching the rest.
     *
     * @param idx index into {@link #getInventories()}
     */
    default void markDirty(int idx) {
        List<IInventory> invs = getInventories();
        if (idx >= 0 && idx < invs.size()) {
            IInventory inv = invs.get(idx);
            if (inv != null) inv.markDirty();
        }
    }

    /**
     * Returns all non-null backing inventories satisfying {@code condition}.
     * Override with a more efficient implementation if inventories are indexed.
     */
    default List<IInventory> filterInventories(ResourceFilter<IInventory> condition) {
        return getInventories().stream()
            .filter(Objects::nonNull)
            .filter(condition)
            .collect(Collectors.toList());
    }

    /**
     * Returns all non-null backing tanks satisfying {@code condition}.
     */
    default List<IFluidTank> filterTanks(ResourceFilter<IFluidTank> condition) {
        return getFluidTanks().stream()
            .filter(Objects::nonNull)
            .filter(condition)
            .collect(Collectors.toList());
    }

    /**
     * Returns a filtered view of the aggregated item snapshot.
     *
     * <p>
     * <b>Performance note:</b> captures the aggregated snapshot once; prefer
     * calling {@link #aggregatedItems()} yourself if you intend to apply
     * multiple predicates to avoid redundant snapshots.
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
     * <em>not</em> included in the result — "no threshold" means "no alarm".
     */
    default Map<ItemStackWrapper, Long> getItemsBelowThreshold(Map<ItemStackWrapper, Long> thresholds) {
        Map<ItemStackWrapper, Long> snapshot = aggregatedItems();
        return thresholds.entrySet()
            .stream()
            .filter(e -> snapshot.getOrDefault(e.getKey(), 0L) < e.getValue())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> snapshot.getOrDefault(e.getKey(), 0L)));
    }

    /**
     * Returns the fluid fill ratio in {@code [0.0, 1.0]}.
     * Returns {@code 0.0} if total fluid capacity is zero.
     */
    default double fluidFillFactor() {
        long capacity = totalFluidCapacity();
        return capacity == 0L ? 0.0 : (double) totalFluidStored() / capacity;
    }

    /**
     * Returns the item slot fill ratio in {@code [0.0, 1.0]}.
     * Returns {@code 0.0} if total item slot capacity is zero.
     */
    default double itemSlotFillFactor() {
        long capacity = totalItemCapacity();
        return capacity == 0L ? 0.0 : (double) totalItemSlots() / capacity;
    }
}
