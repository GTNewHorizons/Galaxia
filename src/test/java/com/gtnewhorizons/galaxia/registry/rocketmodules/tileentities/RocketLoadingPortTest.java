package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product and integration contracts for the actual bounded native resource owner. */
final class RocketLoadingPortTest {

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureMinecraft();
        TileEntity.addMapping(TileEntityRocketLoadingPort.class, "test.galaxia.rocket_loading_port");
    }

    @Test
    void simulationHonorsActualSlotsAndExactItemNbtWithoutMutating() {
        var port = new TileEntityRocketLoadingPort();
        for (int i = 0; i < port.getSizeInventory(); i++)
            port.setInventorySlotContents(i, new ItemStack(Items.stick, 64));
        var tag = new NBTTagCompound();
        tag.setString("cargo", "reserved");
        var tagged = new ItemStack(Items.stick);
        tagged.setTagCompound(tag);
        var key = ItemStackWrapper.of(tagged);
        assertEquals(0, port.updateContents(key, 1, true));
        port.setInventorySlotContents(0, tagged.copy());
        assertEquals(63, port.updateContents(key, 100, true));
        assertEquals(1, port.getStackInSlot(0).stackSize);
        assertEquals(63, port.updateContents(key, 100, false));
        assertEquals(64, port.getStackInSlot(0).stackSize);
        assertEquals(64, port.updateContents(key, -100, true));
        assertEquals(64, port.getStackInSlot(0).stackSize);
        assertEquals(64, port.updateContents(key, -100, false));
        assertNull(port.getStackInSlot(0));
        assertEquals(64, port.getStackInSlot(1).stackSize);
    }

    @Test
    void twoNativeTanksConserveDistinctFluidIdentitiesAndSimulatePartialCapacity() {
        var port = new TileEntityRocketLoadingPort();
        int capacity = port.getTankInfo(ForgeDirection.UNKNOWN)[0].capacity;
        var water = new FluidKey(FluidRegistry.WATER, null);
        var lava = new FluidKey(FluidRegistry.LAVA, null);
        assertEquals(capacity - 1, port.updateContents(water, capacity - 1L, false));
        assertEquals(capacity, port.updateContents(lava, capacity * 2L, true));
        assertEquals(0, port.getFluidAmount(lava));
        assertEquals(capacity, port.updateContents(lava, capacity * 2L, false));
        var tagged = new NBTTagCompound();
        tagged.setString("grade", "other");
        assertEquals(0, port.fill(ForgeDirection.NORTH, new FluidStack(FluidRegistry.WATER, 1, tagged), true));
        assertEquals(1, port.updateContents(water, 1, false));
        assertEquals(capacity, port.updateContents(water, -capacity * 2L, true));
        assertEquals(capacity, port.getFluidAmount(water));
        assertEquals(capacity, port.updateContents(water, -capacity * 2L, false));
        assertEquals(capacity, port.getFluidAmount(lava));
    }

    @Test
    void stationBindingReusesOneDualResourceOwnerAndNativeSaveKeepsActualContents() {
        var port = new TileEntityRocketLoadingPort();
        var item = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var fluid = new FluidKey(FluidRegistry.WATER, null);
        port.updateContents(item, 7);
        port.updateContents(fluid, 37);
        var binding = new StationAttachmentRegistry.ResolvedAttachment<>(port, TileEntityRocketLoadingPort.HANDLER);
        assertSame(port, binding.distributedInventory());
        assertEquals(
            7,
            binding.distributedInventory()
                .getItemAmount(item));
        assertEquals(
            37,
            binding.distributedInventory()
                .getFluidAmount(fluid));
        var saved = new NBTTagCompound();
        port.writeToNBT(saved);
        var restored = new TileEntityRocketLoadingPort();
        restored.readFromNBT(saved);
        assertEquals(port.getItemAmounts(), restored.getItemAmounts());
        assertEquals(port.getFluidAmounts(), restored.getFluidAmounts());
        assertEquals(port.totalFluidCapacity(), restored.totalFluidCapacity());
        assertEquals(port.totalItemCapacity(), restored.totalItemCapacity());
    }
}
