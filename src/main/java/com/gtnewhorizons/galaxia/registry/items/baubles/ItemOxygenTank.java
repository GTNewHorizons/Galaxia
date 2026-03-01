package com.gtnewhorizons.galaxia.registry.items.baubles;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;

public class ItemOxygenTank extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_OXYGEN_TANK = "oxygen_tank";
    public static final String NBT_OXYGEN = "current_oxygen";

    int oxygenStorage;

    public ItemOxygenTank(int oxygenStorage) {
        this.oxygenStorage = oxygenStorage;
    }

    public int getMaxOxygen() {
        return oxygenStorage;
    }

    @Override
    public void getSubItems(Item p_150895_1_, CreativeTabs p_150895_2_, List<ItemStack> p_150895_3_) {
        p_150895_3_.add(getStack(oxygenStorage));
    }

    public @NotNull ItemStack getStack(int amount) {
        ItemStack stack = new ItemStack(this, 1);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_OXYGEN, amount);
        stack.setTagCompound(tag);
        return stack;
    }

    public int getCurrentOxygen(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound()
            .getInteger(NBT_OXYGEN);
    }

    public float getPercentFull(ItemStack stack) {
        return (float) getCurrentOxygen(stack) / oxygenStorage;
    }

    public void fillTank(ItemStack stack, int amount) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        int newAmount = Math.min(getCurrentOxygen(stack) + amount, oxygenStorage);
        stack.getTagCompound()
            .setInteger(NBT_OXYGEN, newAmount);
    }

    /**
     * Drain oxygen from an ItemStack containing an ItemOxygenTank. If the full amount cannot be drained, it will
     * drain as much as possible!
     * 
     * @param amount Amount of oxygen to consume.
     * @return If the full amount was successfully drained.
     */
    public boolean drainTank(ItemStack stack, int amount) {
        int current = getCurrentOxygen(stack);
        int drained = Math.min(current, amount);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBT_OXYGEN, current - drained);
        return drained == amount;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - getPercentFull(stack);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getDurabilityForDisplay(stack) != 0;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        super.addInformation(stack, player, tooltip, p_77624_4_);
        tooltip.add(
            StatCollector
                .translateToLocalFormatted("item.galaxia.oxygen_tank.desc", getCurrentOxygen(stack), oxygenStorage));
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_OXYGEN_TANK };
    }

    // This is for the old Baubles system that I am forced to implement. We dep Baubles-Extended anyways so this will
    // never be used.
    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.UNIVERSAL;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {

    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {

    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {

    }

    @Override
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }
}
