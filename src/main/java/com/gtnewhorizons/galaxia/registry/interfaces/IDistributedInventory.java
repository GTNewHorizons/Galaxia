package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

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

    // =========================================================================
    // Core Data Providers
    // =========================================================================

    /**
     * Returns the ordered list of backing item inventories.
     * Indices here correspond to the {@code idx} parameter of
     * {@link #getItemFilter(int)} and {@link #getItemSlotRange(int)}.
     */
    List<IInventory> getInventories();

    /**
     * Returns the ordered list of backing fluid tanks.
     * Indices here correspond to the {@code idx} parameter of
     * {@link #getFluidFilter(int)} and {@link #getFluidSlotRange(int)}.
     */
    List<IFluidTank> getFluidTanks();

    /**
     * Returns the item predicate governing what may enter the inventory at
     * {@code idx}. Implementations should return a constant or cached value
     * rather than allocating on every call.
     */
    default Predicate<ItemStackWrapper> getItemFilter(int idx) {
        return w -> true;
    }

    /**
     * Returns the fluid predicate governing what may enter the tank at
     * {@code idx}.
     */
    default Predicate<FluidKey> getFluidFilter(int idx) {
        return key -> true;
    }

    /**
     * Returns the half-open slot range {@code [start, end)} that the inventory
     * at {@code idx} occupies in the global flattened slot space.
     */
    default SlotRange getItemSlotRange(int idx) {
        List<IInventory> inventories = getInventories();
        int start = 0;
        for (int i = 0; i < idx && i < inventories.size(); i++) {
            IInventory inv = inventories.get(i);
            if (inv != null) start += inv.getSizeInventory();
        }
        IInventory inv = inventories.get(idx);
        int end = inv != null ? start + inv.getSizeInventory() : start;
        return new SlotRange(start, end);
    }

    /**
     * Returns the half-open slot range {@code [start, end)} that the tank at
     * {@code idx} occupies in the global flattened slot space.
     */
    default SlotRange getFluidSlotRange(int idx) {
        List<IFluidTank> tanks = getFluidTanks();
        int start = 0;
        for (int i = 0; i < idx && i < tanks.size(); i++) {
            IFluidTank tank = tanks.get(i);
            if (tank != null) start++;
        }
        IFluidTank tank = tanks.get(idx);
        int end = tank != null ? start + 1 : start;
        return new SlotRange(start, end);
    }

    // =========================================================================
    // Aggregation API
    // =========================================================================

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

    // =========================================================================
    // Capacity & Space
    // =========================================================================

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

    // =========================================================================
    // Mutation API
    // =========================================================================

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
     * @param idx  index into {@link #getInventories()} or
     *             {@link #getFluidTanks()} depending on {@code type}
     * @param type whether the target is an item inventory or a fluid tank
     */
    default void markDirty(int idx, StorageType type) {
        switch (type) {
            case ITEM: {
                List<IInventory> invs = getInventories();
                if (idx >= 0 && idx < invs.size()) {
                    IInventory inv = invs.get(idx);
                    if (inv != null) inv.markDirty();
                }
                break;
            }
            case FLUID:
                break;
        }
    }

    // =========================================================================
    // Default: Inventory / Tank Filtering
    // =========================================================================

    /**
     * Returns all non-null backing inventories satisfying {@code condition}.
     * Override with a more efficient implementation if inventories are indexed.
     */
    default List<IInventory> filterInventories(Predicate<IInventory> condition) {
        return getInventories().stream()
            .filter(Objects::nonNull)
            .filter(condition)
            .collect(Collectors.toList());
    }

    /**
     * Returns all non-null backing tanks satisfying {@code condition}.
     */
    default List<IFluidTank> filterTanks(Predicate<IFluidTank> condition) {
        return getFluidTanks().stream()
            .filter(Objects::nonNull)
            .filter(condition)
            .collect(Collectors.toList());
    }

    /**
     * Returns all tanks that can accept at least 1 mB of {@code fluid} right
     * now. Uses a simulated fill (no mutation) to determine acceptance.
     */
    default List<IFluidTank> getTanksAccepting(FluidStack fluid) {
        return filterTanks(tank -> tank.fill(fluid, /* doFill= */ false) > 0);
    }

    // =========================================================================
    // Default: Aggregation Queries
    // =========================================================================

    /**
     * Returns a filtered view of the aggregated item snapshot.
     *
     * <p>
     * <b>Performance note:</b> captures the aggregated snapshot once; prefer
     * calling {@link #aggregatedItems()} yourself if you intend to apply
     * multiple predicates to avoid redundant snapshots.
     */
    default Map<ItemStackWrapper, Long> filterItems(Predicate<ItemStackWrapper> predicate) {
        return aggregatedItems().entrySet()
            .stream()
            .filter(e -> predicate.test(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a filtered view of the aggregated fluid snapshot.
     */
    default Map<FluidKey, Long> filterFluids(Predicate<FluidKey> predicate) {
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

    // =========================================================================
    // Default: Metrics
    // =========================================================================

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

    // =========================================================================
    // Supporting Types
    // =========================================================================

    /**
     * Distinguishes between item inventory and fluid tank storage targets,
     * used in {@link #markDirty(int, StorageType)} and related APIs.
     */
    enum StorageType {
        ITEM,
        FLUID
    }

    /**
     * An immutable, half-open slot range {@code [start, end)}.
     *
     * <p>
     * Replaces the raw {@code int[]} previously returned by
     * {@code getBoundsFor*}, removing ambiguity about index semantics and
     * enabling safe equality and range checks.
     */
    record SlotRange(int start, int end) {

        public SlotRange {
            if (start < 0 || end < start)
                throw new IllegalArgumentException("Invalid SlotRange: [" + start + ", " + end + ")");
        }

        /** Number of slots in this range. */
        public int size() {
            return end - start;
        }

        /** Returns {@code true} if {@code slot} falls within this range. */
        public boolean contains(int slot) {
            return slot >= start && slot < end;
        }

        /** Returns the local offset of {@code slot} within this range. */
        public int localOffset(int slot) {
            if (!contains(slot)) throw new IndexOutOfBoundsException("Slot " + slot + " not in range " + this);
            return slot - start;
        }
    }

    /**
     * A stable, amount-independent identity key for a fluid.
     *
     * <p>
     * Replaces direct {@link FluidStack} use as a map/set key. {@link FluidStack}
     * is mutable and its equality semantics include the stored amount, making
     * it unsuitable as a key in aggregation maps.
     */
    record FluidKey(Fluid fluid, @Nullable NBTTagCompound tag) {

        /** Constructs a {@code FluidKey} from an existing {@link FluidStack}. */
        public static FluidKey of(FluidStack stack) {
            return new FluidKey(stack.getFluid(), stack.tag);
        }

        /** Reconstructs a {@link FluidStack} with the given volume. */
        public FluidStack toStack(int amount) {
            return tag == null ? new FluidStack(fluid, amount) : new FluidStack(fluid, amount, tag);
        }

        /** Looks up a Fluid by registry name and wraps it in a tagless FluidKey. */
        public static @Nullable FluidKey fromName(String fluidName) {
            if (fluidName == null || fluidName.isEmpty()) return null;
            try {
                Fluid fluid = FluidRegistry.getFluid(fluidName);
                return fluid != null ? new FluidKey(fluid, null) : null;
            } catch (Throwable e) {
                return null;
            }
        }
    }

    // =========================================================================
    // Filter Factories
    // =========================================================================

    /**
     * Static factory methods for common {@link ItemStackWrapper} predicates.
     *
     * <p>
     * Previously an inner class named {@code Filter}; split from fluid
     * predicates for clarity. Combinators ({@link #anyOf}, {@link #allOf},
     * {@link #noneOf}) are generic and work with any predicate type.
     *
     * <p>
     * Example usage:
     *
     * <pre>
     *
     * {
     *     &#64;code
     *     Predicate<ItemStackWrapper> filter = ItemFilters
     *         .anyOf(ItemFilters.byMod("thermal"), ItemFilters.byItem(Items.diamond));
     *     Map<ItemStackWrapper, Long> result = inventory.filterItems(filter);
     * }
     * </pre>
     */
    interface ItemFilters {

        /** Matches a specific {@link Item} instance. */
        static Predicate<ItemStackWrapper> byItem(Item item) {
            return w -> w.item() == item;
        }

        /**
         * Matches items whose registry name starts with {@code modId + ":"}.
         * Returns {@code false} for items with no registered name.
         */
        static Predicate<ItemStackWrapper> byMod(String modId) {
            String prefix = modId + ":";
            return w -> {
                String name = Item.itemRegistry.getNameForObject(w.item());
                return name != null && name.startsWith(prefix);
            };
        }

        /**
         * Matches items whose unlocalized name matches the given regex.
         * Matching is case-insensitive and requires a full match (use {@code .*}
         * for partial matching, e.g. {@code ".*ore.*"}).
         */
        static Predicate<ItemStackWrapper> byNameRegex(String regex) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return w -> pattern.matcher(
                w.toStack(1)
                    .getUnlocalizedName())
                .matches();
        }

        /**
         * Matches damageable items whose remaining durability ratio is strictly
         * below {@code threshold} (e.g. {@code 0.25} for less than 25% durability
         * remaining). Non-damageable items never match.
         */
        static Predicate<ItemStackWrapper> damagedBelow(double threshold) {
            return w -> {
                ItemStack stack = w.toStack(1);
                if (!stack.isItemStackDamageable()) return false;
                double remaining = 1.0 - (double) stack.getItemDamage() / stack.getMaxDamage();
                return remaining < threshold;
            };
        }

        /** Matches if <em>any</em> of the provided predicates match. */
        @SafeVarargs
        static <T> Predicate<T> anyOf(Predicate<T>... predicates) {
            return t -> {
                for (Predicate<T> p : predicates) if (p.test(t)) return true;
                return false;
            };
        }

        /** Matches if <em>all</em> of the provided predicates match. */
        @SafeVarargs
        static <T> Predicate<T> allOf(Predicate<T>... predicates) {
            return t -> {
                for (Predicate<T> p : predicates) if (!p.test(t)) return false;
                return true;
            };
        }

        /** Matches if <em>none</em> of the provided predicates match. */
        @SafeVarargs
        static <T> Predicate<T> noneOf(Predicate<T>... predicates) {
            return t -> {
                for (Predicate<T> p : predicates) if (p.test(t)) return false;
                return true;
            };
        }
    }

    /**
     * Static factory methods for common {@link FluidKey} predicates.
     */
    interface FluidFilters {

        /** Matches a specific {@link Fluid} instance. */
        static Predicate<FluidKey> byFluid(Fluid fluid) {
            return key -> key.fluid() == fluid;
        }

        /**
         * Matches fluids whose registry name matches the given regex.
         * Matching is case-insensitive and requires a full match.
         */
        static Predicate<FluidKey> byNameRegex(String regex) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return key -> pattern.matcher(
                key.fluid()
                    .getName())
                .matches();
        }

        /** Matches fluids that carry no NBT tag. */
        static Predicate<FluidKey> hasNoTag() {
            return key -> key.tag() == null || key.tag()
                .hasNoTags();
        }
    }
}
