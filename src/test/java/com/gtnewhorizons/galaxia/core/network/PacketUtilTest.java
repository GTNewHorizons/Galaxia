package com.gtnewhorizons.galaxia.core.network;

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

import io.netty.buffer.Unpooled;

final class PacketUtilTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    private enum TestEnum {
        FIRST,
        SECOND
    }

    @Test
    void readEnumCrashesForUnknownOrdinal() {
        var buf = Unpooled.buffer();
        buf.writeByte(99);

        assertThrows(IllegalStateException.class, () -> PacketUtil.readEnum(buf, TestEnum.class));
    }

    @Test
    void inventoryKeyRoundTripPreservesCompleteNbtIdentity() {
        ItemStack itemStack = new ItemStack(Items.diamond, 1, 7);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("identity", "item");
        itemStack.setTagCompound(itemTag);

        FluidStack fluidStack = new FluidStack(FluidRegistry.WATER, 1);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("identity", "fluid");
        fluidStack.tag = fluidTag;

        for (InventoryKey key : new InventoryKey[] { ItemStackWrapper.of(itemStack), FluidKey.of(fluidStack) }) {
            var buf = Unpooled.buffer();
            PacketUtil.writeInventoryKey(buf, key);

            assertEquals(key, PacketUtil.readInventoryKey(buf));
        }
    }
}
