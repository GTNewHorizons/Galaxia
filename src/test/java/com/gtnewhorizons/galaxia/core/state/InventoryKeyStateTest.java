package com.gtnewhorizons.galaxia.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class InventoryKeyStateTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void itemAndFluidRoundTripWithTheirCompleteNbtIdentity() {
        ItemStack itemStack = new ItemStack(Items.diamond, 1, 7);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("identity", "item");
        itemStack.setTagCompound(itemTag);

        FluidStack fluidStack = new FluidStack(FluidRegistry.WATER, 1);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("identity", "fluid");
        fluidStack.tag = fluidTag;

        for (InventoryKey key : new InventoryKey[] { ItemStackWrapper.of(itemStack), FluidKey.of(fluidStack) }) {
            assertEquals(key, InventoryKeyState.decode(InventoryKeyState.encode(key)));
        }
    }

    @Test
    void unknownTypesMalformedStacksAndUnknownFluidsAreRejected() {
        NBTTagCompound unknownType = InventoryKeyState.encode(ItemStackWrapper.of(new ItemStack(Items.diamond)));
        unknownType.setString("type", "energy");

        NBTTagCompound malformedItem = InventoryKeyState.encode(ItemStackWrapper.of(new ItemStack(Items.diamond)));
        malformedItem.setString("stack", "wrong type");

        NBTTagCompound unknownFluid = InventoryKeyState.encode(new FluidKey(FluidRegistry.WATER, null));
        unknownFluid.getCompoundTag("stack")
            .setString("FluidName", "galaxia:unknown_state_fluid");

        assertThrows(IllegalStateException.class, () -> InventoryKeyState.decode(unknownType));
        assertThrows(IllegalStateException.class, () -> InventoryKeyState.decode(malformedItem));
        assertThrows(IllegalStateException.class, () -> InventoryKeyState.decode(unknownFluid));
    }
}
