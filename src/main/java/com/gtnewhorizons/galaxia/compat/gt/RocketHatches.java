package com.gtnewhorizons.galaxia.compat.gt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/**
 * GT hatches attached to a rocket multiblock, plus the item and fluid moves both the silo and the assembler need.
 * Positions are stored rather than tile references so a chunk reload can't leave stale hatches behind
 */
public final class RocketHatches {

    private final List<BlockPos> inputBuses = new ArrayList<>();
    private final List<BlockPos> inputHatches = new ArrayList<>();
    private final List<BlockPos> outputBuses = new ArrayList<>();

    /** Called before every structure check, the check adds back what is still there */
    public void clear() {
        inputBuses.clear();
        inputHatches.clear();
        outputBuses.clear();
    }

    boolean addInputBus(IGregTechTileEntity tile, short casingIndex) {
        return add(tile, casingIndex, MTEHatchInputBus.class, inputBuses);
    }

    boolean addInputHatch(IGregTechTileEntity tile, short casingIndex) {
        return add(tile, casingIndex, MTEHatchInput.class, inputHatches);
    }

    boolean addOutputBus(IGregTechTileEntity tile, short casingIndex) {
        return add(tile, casingIndex, MTEHatchOutputBus.class, outputBuses);
    }

    private static boolean add(IGregTechTileEntity tile, short casingIndex, Class<? extends MTEHatch> type,
        List<BlockPos> into) {
        if (tile == null) return false;
        IMetaTileEntity mte = tile.getMetaTileEntity();
        if (!type.isInstance(mte)) return false;
        ((MTEHatch) mte).updateTexture(casingIndex);
        BlockPos pos = new BlockPos(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!into.contains(pos)) into.add(pos);
        return true;
    }

    public int inputBusCount() {
        return inputBuses.size();
    }

    public int inputHatchCount() {
        return inputHatches.size();
    }

    public int outputBusCount() {
        return outputBuses.size();
    }

    public boolean hasAttachments() {
        return !inputBuses.isEmpty() || !inputHatches.isEmpty() || !outputBuses.isEmpty();
    }

    /** View of actual hatch inventories. Inputs supply a mission, outputs receive its unloaded cargo. */
    public IDistributedInventory inventory(World world) {
        return new IDistributedInventory() {

            @Override
            public Map<ItemStackWrapper, Long> getItemAmounts() {
                Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
                for (ItemStack stack : peekItems(world))
                    result.merge(ItemStackWrapper.of(stack), (long) stack.stackSize, Long::sum);
                return result;
            }

            @Override
            public Map<FluidKey, Long> getFluidAmounts() {
                Map<FluidKey, Long> result = new LinkedHashMap<>();
                for (FluidStack stack : storedFluids(world))
                    result.merge(FluidKey.of(stack), (long) stack.amount, Long::sum);
                return result;
            }

            @Override
            public long getFreeItemSpace(ItemStackWrapper item) {
                return insertIntoOwnStorage(item, Integer.MAX_VALUE, true);
            }

            @Override
            public long insertIntoOwnStorage(ItemStackWrapper item, long amount, boolean simulate) {
                if (amount <= 0) return 0;
                var stack = item.toStack(Math.min(amount, Integer.MAX_VALUE));
                int before = stack.stackSize;
                for (IGregTechTileEntity bus : resolve(world, outputBuses)) {
                    if (bus.getMetaTileEntity() instanceof MTEHatchOutputBus hatch) hatch.storePartial(stack, simulate);
                    if (stack.stackSize == 0) break;
                }
                return before - stack.stackSize;
            }

            @Override
            public long insertIntoOwnStorage(ItemStackWrapper item, long amount) {
                return insertIntoOwnStorage(item, amount, false);
            }

            @Override
            public long extractFromOwnStorage(ItemStackWrapper item, long amount, boolean simulate) {
                long available = Math.min(
                    Math.min(amount, Integer.MAX_VALUE),
                    countItems(world, stack -> item.equals(ItemStackWrapper.of(stack))));
                if (simulate) return available;
                return extractItems(
                    world,
                    stack -> item.equals(ItemStackWrapper.of(stack)),
                    (int) Math.min(available, Integer.MAX_VALUE)).stream()
                        .mapToLong(stack -> stack.stackSize)
                        .sum();
            }

            @Override
            public long extractFromOwnStorage(ItemStackWrapper item, long amount) {
                return extractFromOwnStorage(item, amount, false);
            }

            @Override
            public long extractFromOwnFluidStorage(FluidKey fluid, long amount, boolean simulate) {
                long available = Math
                    .min(Math.min(amount, Integer.MAX_VALUE), getFluidAmounts().getOrDefault(fluid, 0L));
                return simulate ? available
                    : drainFluid(world, fluid.toStack((int) Math.min(available, Integer.MAX_VALUE)));
            }

            @Override
            public long extractFromOwnFluidStorage(FluidKey fluid, long amount) {
                return extractFromOwnFluidStorage(fluid, amount, false);
            }
        };
    }

    /** Feed an existing machine buffer using its normal slot admission rules. */
    public void transferInputItemsTo(World world, IInventory target) {
        var destination = new InvWrapper(target);
        for (IGregTechTileEntity bus : resolve(world, inputBuses)) {
            for (int sourceSlot = 0; sourceSlot < itemSlots(bus); sourceSlot++) {
                ItemStack source = bus.getStackInSlot(sourceSlot);
                if (source == null) continue;
                for (int slot = 0; slot < destination.getSlots() && source != null; slot++) {
                    ItemStack remainder = destination.insertItem(slot, source.copy(), true);
                    int moved = source.stackSize - (remainder == null ? 0 : remainder.stackSize);
                    if (moved <= 0) continue;
                    ItemStack removed = bus.decrStackSize(sourceSlot, moved);
                    if (removed == null) continue;
                    ItemStack rejected = destination.insertItem(slot, removed, false);
                    if (rejected != null && rejected.stackSize > 0)
                        throw new IllegalStateException("A machine rejected its simulated item admission");
                    source = bus.getStackInSlot(sourceSlot);
                }
            }
            if (bus.getMetaTileEntity() instanceof MTEHatchInputBus hatch) hatch.updateSlots();
        }
    }

    /** Commit only the fluid amount accepted by the machine's simulation. */
    public void transferInputFluidsTo(World world, IFluidHandler target) {
        for (IGregTechTileEntity tile : resolve(world, inputHatches)) {
            if (!(tile.getMetaTileEntity() instanceof MTEHatchInput hatch) || hatch.getFluid() == null) continue;
            int accepted = target.fill(
                ForgeDirection.UNKNOWN,
                hatch.getFluid()
                    .copy(),
                false);
            if (accepted <= 0) continue;
            FluidStack drained = hatch.drain(accepted, true);
            if (drained != null && target.fill(ForgeDirection.UNKNOWN, drained, true) != drained.amount)
                throw new IllegalStateException("A machine rejected its simulated fluid admission");
        }
    }

    /**
     * Counts matching items across every input bus
     *
     * @param world  The world the hatches are in
     * @param filter Which items to count
     *
     * @return Total stack size of matching items
     */
    public long countItems(World world, Predicate<ItemStack> filter) {
        long total = 0;
        for (IGregTechTileEntity bus : resolve(world, inputBuses)) {
            for (int slot = 0; slot < itemSlots(bus); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && filter.test(stack)) total += stack.stackSize;
            }
        }
        return total;
    }

    /**
     * Copies of every stack in the input buses, for deciding what to take before taking it
     *
     * @param world The world the hatches are in
     *
     * @return Snapshot of the bus contents
     */
    public List<ItemStack> peekItems(World world) {
        List<ItemStack> stacks = new ArrayList<>();
        for (IGregTechTileEntity bus : resolve(world, inputBuses)) {
            for (int slot = 0; slot < itemSlots(bus); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null) stacks.add(stack.copy());
            }
        }
        return stacks;
    }

    /**
     * Removes up to an amount of matching items from the input buses
     *
     * @param world  The world the hatches are in
     * @param filter Which items to take
     * @param amount How many to take
     *
     * @return The taken stacks, split by item type
     */
    public List<ItemStack> extractItems(World world, Predicate<ItemStack> filter, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = amount;
        for (IGregTechTileEntity bus : resolve(world, inputBuses)) {
            for (int slot = 0; slot < itemSlots(bus) && remaining > 0; slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack == null || !filter.test(stack)) continue;
                ItemStack removed = bus.decrStackSize(slot, Math.min(remaining, stack.stackSize));
                if (removed == null) continue;
                remaining -= removed.stackSize;
                taken.add(removed);
            }
            if (bus.getMetaTileEntity() instanceof MTEHatchInputBus hatch) hatch.updateSlots();
            if (remaining <= 0) break;
        }
        return taken;
    }

    /**
     * Pushes a stack into the output buses
     *
     * @param world The world the hatches are in
     * @param stack The stack to store, its size is reduced by what fit
     *
     * @return Boolean : True => everything fit
     */
    public boolean storeItem(World world, ItemStack stack) {
        for (IGregTechTileEntity bus : resolve(world, outputBuses)) {
            if (stack.stackSize <= 0) return true;
            if (bus.getMetaTileEntity() instanceof MTEHatchOutputBus hatch) hatch.storePartial(stack, false);
        }
        return stack.stackSize <= 0;
    }

    /**
     * Drains a fluid from the input hatches
     *
     * @param world The world the hatches are in
     * @param fluid The fluid and maximum amount to drain
     *
     * @return The amount drained in mB
     */
    public int drainFluid(World world, FluidStack fluid) {
        int remaining = fluid.amount;
        for (IGregTechTileEntity hatchTile : resolve(world, inputHatches)) {
            if (!(hatchTile.getMetaTileEntity() instanceof MTEHatchInput hatch)) continue;
            FluidStack stored = hatch.getFluid();
            if (stored == null || !stored.isFluidEqual(fluid)) continue;
            FluidStack drained = hatch.drain(remaining, true);
            if (drained != null) remaining -= drained.amount;
            if (remaining <= 0) break;
        }
        return fluid.amount - remaining;
    }

    /** Every fluid currently sitting in the input hatches */
    public List<FluidStack> storedFluids(World world) {
        List<FluidStack> fluids = new ArrayList<>();
        for (IGregTechTileEntity hatchTile : resolve(world, inputHatches)) {
            if (hatchTile.getMetaTileEntity() instanceof MTEHatchInput hatch && hatch.getFluid() != null) {
                fluids.add(hatch.getFluid());
            }
        }
        return fluids;
    }

    /** Input buses keep their programmed circuit in the last slot, which is never consumed */
    private static int itemSlots(IGregTechTileEntity bus) {
        int size = bus.getSizeInventory();
        return bus.getMetaTileEntity() instanceof MTEHatchInputBus ? size - 1 : size;
    }

    private static List<IGregTechTileEntity> resolve(World world, List<BlockPos> positions) {
        List<IGregTechTileEntity> result = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos.getTE(world) instanceof IGregTechTileEntity tile && !tile.isDead()) result.add(tile);
        }
        return result;
    }
}
