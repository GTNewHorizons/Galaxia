package com.gtnewhorizons.galaxia.items.armor;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.EnumHelper;

public class ItemSpaceSuit extends ItemArmor {

    public static final int MIN_TEMP = 223; // -50 Celsius
    public static final int MAX_TEMP = 373; // 100 Celsius

    public static final ArmorMaterial SUIT_MATERIAL = EnumHelper
        .addArmorMaterial("SPACESUIT", 30, new int[] { 3, 8, 6, 3 }, 15);

    public ItemSpaceSuit(ArmorMaterial material, int renderIndex, int armorType) {
        super(material, renderIndex, armorType);
        setMaxStackSize(1);
    }

    public static ItemStack createSuitPiece(Item item) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("minTemp", MIN_TEMP);
        nbt.setInteger("maxTemp", MAX_TEMP);
        stack.setTagCompound(nbt);
        return stack;
    }

    public static int getMinTemp(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("minTemp")) {
            return stack.getTagCompound()
                .getInteger("minTemp");
        }
        return MIN_TEMP;
    }

    public static int getMaxTemp(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("maxTemp")) {
            return stack.getTagCompound()
                .getInteger("maxTemp");
        }
        return MAX_TEMP;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }
}
