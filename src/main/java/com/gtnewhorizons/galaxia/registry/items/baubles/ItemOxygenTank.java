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
        setMaxDamage(oxygenStorage);
    }

    /**
     * Drain oxygen from an ItemStack containing an ItemOxygenTank. If the full amount cannot be drained, it will
     * drain as much as possible!
     * @param amount Amount of oxygen to consume.
     * @return If the full amount was successfully drained.
     */
    public boolean drainOxygen(ItemStack stackIn, int amount) {
        int damage = stackIn.getItemDamage();
        if (damage < amount) {
            stackIn.setItemDamage(0);
            return false;
        }
        stackIn.setItemDamage(damage - amount);
        return true;
    }

    @Override
    public boolean isDamageable() {
        return true;
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
