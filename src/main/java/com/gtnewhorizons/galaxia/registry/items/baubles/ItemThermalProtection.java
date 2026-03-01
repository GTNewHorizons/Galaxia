package com.gtnewhorizons.galaxia.registry.items.baubles;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;

public class ItemThermalProtection extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_THERMAL_PROTECTION = "thermal_protection";

    int coldProtection;
    int heatProtection;

    public ItemThermalProtection(int heatProtection, int coldProtection) {
        this.heatProtection = heatProtection;
        this.coldProtection = coldProtection;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        super.addInformation(stack, player, tooltip, p_77624_4_);
        if (coldProtection > 0) tooltip
            .add(StatCollector.translateToLocalFormatted("item.galaxia.thermal_protection.desc.cold", coldProtection));
        if (heatProtection > 0) tooltip
            .add(StatCollector.translateToLocalFormatted("item.galaxia.thermal_protection.desc.hot", heatProtection));
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_THERMAL_PROTECTION };
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
