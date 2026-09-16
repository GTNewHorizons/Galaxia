package com.gtnewhorizons.galaxia.core.state;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/** Canonical full-NBT state for one inventory resource identity. */
public final class InventoryKeyState {

    private static final String PATH = "inventoryKey";

    private InventoryKeyState() {}

    public static NBTTagCompound encode(InventoryKey key) {
        if (key == null) throw fail(PATH, "must not be null");
        NBTTagCompound out = new NBTTagCompound();
        NBTTagCompound stack = new NBTTagCompound();
        if (key instanceof ItemStackWrapper item) {
            out.setString("type", "item");
            item.toStack(1)
                .writeToNBT(stack);
        } else if (key instanceof FluidKey fluid && fluid.fluid() != null) {
            out.setString("type", "fluid");
            fluid.toStack(1)
                .writeToNBT(stack);
        } else {
            throw fail(PATH, "unsupported or invalid inventory resource " + key);
        }
        out.setTag("stack", stack);
        return out;
    }

    public static InventoryKey decode(NBTTagCompound encoded) {
        return decode(new NbtReader(encoded, PATH));
    }

    static InventoryKey decode(NbtReader in) {
        String type = in.string("type");
        NBTTagCompound stack = in.compound("stack")
            .tag();
        if ("item".equals(type)) {
            ItemStack decoded = ItemStack.loadItemStackFromNBT(stack);
            ItemStackWrapper item = ItemStackWrapper.of(decoded);
            if (item == null) throw fail(in.path() + ".stack", "unknown or malformed item");
            return item;
        }
        if ("fluid".equals(type)) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(stack);
            if (fluid == null || fluid.getFluid() == null) {
                throw fail(in.path() + ".stack", "unknown or malformed fluid");
            }
            return FluidKey.of(fluid);
        }
        throw fail(in.path() + ".type", "expected item or fluid");
    }

    private static IllegalStateException fail(String path, String message) {
        return new IllegalStateException("[STATE] " + path + ": " + message);
    }
}
