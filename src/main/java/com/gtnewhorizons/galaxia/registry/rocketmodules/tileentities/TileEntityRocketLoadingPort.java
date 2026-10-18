package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.ItemAttachmentInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IInventoryStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

/** A visible physical buffer. Endpoints capture this exact port as part of their formed structure. */
public class TileEntityRocketLoadingPort extends TileEntity
    implements IInventory, IFluidHandler, IDistributedInventory, IGuiHolder<PosGuiData> {

    public static final IInventoryStorageHandler<TileEntityRocketLoadingPort> HANDLER = new IInventoryStorageHandler<>() {

        @Override
        public List<IInventory> getInventories(TileEntityRocketLoadingPort port) {
            return List.of(port);
        }

        @Override
        public ResourceFilter<ItemStackWrapper> getItemFilter(TileEntityRocketLoadingPort port) {
            return port.itemFilter;
        }

        @Override
        public BlockPos getPosition(TileEntityRocketLoadingPort port) {
            return new BlockPos(port.xCoord, port.yCoord, port.zCoord);
        }

        @Override
        public void tick(TileEntityRocketLoadingPort port) {}

        @Override
        public boolean isReady(TileEntityRocketLoadingPort port) {
            return port.isAvailable();
        }

        @Override
        public void onAttached(TileEntityRocketLoadingPort port, StationGraph graph) {}

        @Override
        public void onDetached(TileEntityRocketLoadingPort port, StationGraph graph) {}

        @Override
        public void markDirty(TileEntityRocketLoadingPort port) {
            port.markDirty();
        }
    };

    private static final int TANK_COUNT = 2;
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private final ResourceFilter<ItemStackWrapper> itemFilter = ResourceFilter.forItems();
    private final ItemAttachmentInventory<TileEntityRocketLoadingPort> itemAccess = new ItemAttachmentInventory<>(
        HANDLER,
        this);
    private InventoryBasic items = new InventoryBasic(
        "tile.rocket_loading_port.name",
        false,
        ConfigMachines.loadingPort.itemSlots);
    private FluidTank[] tanks = tanks(ConfigMachines.loadingPort.tankCapacity);

    private static FluidTank[] tanks(int capacity) {
        return new FluidTank[] { new FluidTank(capacity), new FluidTank(capacity) };
    }

    public boolean isAvailable() {
        return !isInvalid() && worldObj != null
            && worldObj.blockExists(xCoord, yCoord, zCoord)
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    @Override
    public Map<ItemStackWrapper, Long> getItemAmounts() {
        return itemAccess.getItemAmounts();
    }

    @Override
    public Map<FluidKey, Long> getFluidAmounts() {
        var result = new LinkedHashMap<FluidKey, Long>();
        for (var tank : tanks) if (tank.getFluid() != null)
            result.merge(FluidKey.of(tank.getFluid()), (long) tank.getFluidAmount(), Long::sum);
        return Map.copyOf(result);
    }

    @Override
    public long totalItemSlots() {
        return getSizeInventory();
    }

    @Override
    public long totalItemCapacity() {
        return (long) getSizeInventory() * getInventoryStackLimit();
    }

    @Override
    public long totalFluidCapacity() {
        return (long) TANK_COUNT * tanks[0].getCapacity();
    }

    @Override
    public long getFreeItemSpace(ItemStackWrapper key) {
        return itemAccess.insertIntoOwnStorage(key, Integer.MAX_VALUE, true);
    }

    @Override
    public long getFreeFluidSpace(FluidKey key) {
        return insertIntoOwnFluidStorage(key, Integer.MAX_VALUE, true);
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper key, long amount) {
        return insertIntoOwnStorage(key, amount, false);
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper key, long amount, boolean simulate) {
        return itemAccess.insertIntoOwnStorage(key, amount, simulate);
    }

    @Override
    public long extractFromOwnStorage(ItemStackWrapper key, long amount) {
        return extractFromOwnStorage(key, amount, false);
    }

    @Override
    public long extractFromOwnStorage(ItemStackWrapper key, long amount, boolean simulate) {
        return itemAccess.extractFromOwnStorage(key, amount, simulate);
    }

    @Override
    public long insertIntoOwnFluidStorage(FluidKey key, long amount) {
        return insertIntoOwnFluidStorage(key, amount, false);
    }

    @Override
    public long insertIntoOwnFluidStorage(FluidKey key, long amount, boolean simulate) {
        return fill(ForgeDirection.UNKNOWN, key.toStack((int) Math.min(amount, Integer.MAX_VALUE)), !simulate);
    }

    @Override
    public long extractFromOwnFluidStorage(FluidKey key, long amount) {
        return extractFromOwnFluidStorage(key, amount, false);
    }

    @Override
    public long extractFromOwnFluidStorage(FluidKey key, long amount, boolean simulate) {
        var result = drain(ForgeDirection.UNKNOWN, key.toStack((int) Math.min(amount, Integer.MAX_VALUE)), !simulate);
        return result == null ? 0 : result.amount;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        int moved = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (var tank : tanks) {
                if ((tank.getFluid() == null) != (pass == 1)) continue;
                var remaining = resource.copy();
                remaining.amount = resource.amount - moved;
                moved += tank.fill(remaining, doFill);
                if (moved == resource.amount) break;
            }
            if (moved == resource.amount) break;
        }
        if (doFill && moved > 0) markDirty();
        return moved;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        int moved = 0;
        for (var tank : tanks) {
            if (tank.getFluid() == null || !tank.getFluid()
                .isFluidEqual(resource)) continue;
            var removed = tank.drain(resource.amount - moved, doDrain);
            if (removed != null) moved += removed.amount;
            if (moved == resource.amount) break;
        }
        if (moved == 0) return null;
        if (doDrain) markDirty();
        var result = resource.copy();
        result.amount = moved;
        return result;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        for (var tank : tanks) if (tank.getFluid() != null) {
            var request = tank.getFluid()
                .copy();
            request.amount = maxDrain;
            return drain(from, request, doDrain);
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, net.minecraftforge.fluids.Fluid fluid) {
        return fluid != null && fill(from, new FluidStack(fluid, 1), false) > 0;
    }

    @Override
    public boolean canDrain(ForgeDirection from, net.minecraftforge.fluids.Fluid fluid) {
        return fluid != null && drain(from, new FluidStack(fluid, 1), false) != null;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { tanks[0].getInfo(), tanks[1].getInfo() };
    }

    @Override
    public int getSizeInventory() {
        return items.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        var result = items.decrStackSize(slot, amount);
        if (result != null) markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        var result = items.getStackInSlotOnClosing(slot);
        if (result != null) markDirty();
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        items.setInventorySlotContents(slot, stack);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.rocket_loading_port.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return items.getInventoryStackLimit();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return isAvailable() && player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        int tankY = 28 + ((getSizeInventory() + COLUMNS - 1) / COLUMNS) * SLOT_SIZE;
        var panel = ModularPanel.defaultPanel("galaxia:rocket_loading_port", 196, tankY + 116)
            .bindPlayerInventory()
            .child(
                IKey.lang("tile.rocket_loading_port.name")
                    .asWidget()
                    .pos(8, 7));
        var access = new InvWrapper(this);
        for (int slot = 0; slot < getSizeInventory(); slot++) panel.child(
            new ItemSlot().slot(new ModularSlot(access, slot))
                .pos(8 + slot % COLUMNS * SLOT_SIZE, 24 + slot / COLUMNS * SLOT_SIZE));
        for (int tank = 0; tank < TANK_COUNT; tank++) panel.child(
            new FluidSlot().syncHandler(
                new FluidSlotSyncHandler(tanks[tank]).canDrainSlot(false)
                    .canFillSlot(false))
                .pos(8 + tank * SLOT_SIZE, tankY));
        return panel;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("itemSlots", getSizeInventory());
        tag.setInteger("tankCapacity", tanks[0].getCapacity());
        var encodedItems = new NBTTagList();
        for (int slot = 0; slot < getSizeInventory(); slot++) {
            var stack = getStackInSlot(slot);
            if (stack == null) continue;
            var encoded = stack.writeToNBT(new NBTTagCompound());
            encoded.setInteger("slot", slot);
            encodedItems.appendTag(encoded);
        }
        tag.setTag("items", encodedItems);
        var encodedTanks = new NBTTagList();
        for (var tank : tanks) encodedTanks.appendTag(tank.writeToNBT(new NBTTagCompound()));
        tag.setTag("tanks", encodedTanks);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        var in = new NbtReader(tag, "rocketLoadingPort");
        int count = in.integer("itemSlots");
        int capacity = in.integer("tankCapacity");
        if (count < 1 || count > 81 || capacity < 1)
            throw new IllegalStateException("Invalid physical loading-port capacity");
        items = new InventoryBasic("tile.rocket_loading_port.name", false, count);
        tanks = tanks(capacity);
        var encodedItems = in.compounds("items");
        for (int i = 0; i < encodedItems.tagCount(); i++) {
            var encoded = new NbtReader(encodedItems.getCompoundTagAt(i), "rocketLoadingPort.item");
            int slot = encoded.integer("slot");
            var stack = ItemStack.loadItemStackFromNBT(encoded.tag());
            if (slot < 0 || slot >= count
                || stack == null
                || stack.stackSize <= 0
                || stack.stackSize > Math.min(stack.getMaxStackSize(), getInventoryStackLimit())
                || getStackInSlot(slot) != null) {
                throw new IllegalStateException("Invalid physical loading-port item");
            }
            items.setInventorySlotContents(slot, stack);
        }
        var encodedTanks = in.compounds("tanks");
        if (encodedTanks.tagCount() != TANK_COUNT)
            throw new IllegalStateException("Invalid physical loading-port tank count");
        for (int i = 0; i < TANK_COUNT; i++) {
            tanks[i].readFromNBT(encodedTanks.getCompoundTagAt(i));
            if (tanks[i].getFluidAmount() > capacity)
                throw new IllegalStateException("Physical loading-port tank overfilled");
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        var tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
