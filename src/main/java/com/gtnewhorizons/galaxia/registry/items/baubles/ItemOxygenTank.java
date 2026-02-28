package com.gtnewhorizons.galaxia.registry.items.baubles;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;

public class ItemOxygenTank extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_OXYGEN_TANK = "oxygen_tank";

    int oxygenStorage;

    public ItemOxygenTank(int oxygenStorage) {
        this.oxygenStorage = oxygenStorage;
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
