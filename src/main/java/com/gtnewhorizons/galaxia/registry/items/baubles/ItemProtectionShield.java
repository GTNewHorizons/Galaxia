package com.gtnewhorizons.galaxia.registry.items.baubles;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;
import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;

public class ItemProtectionShield extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_PROTECTION_SHIELD = "protection_shield";

    int pressureProtectionHigh;
    int pressureProtectionLow;
    int radiationProtection;

    public ItemProtectionShield(int highPressure, int lowPressure, int radiation) {
        this.pressureProtectionHigh = highPressure;
        this.pressureProtectionLow = lowPressure;
        this.radiationProtection = radiation;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        super.addInformation(stack, player, tooltip, p_77624_4_);
        if (pressureProtectionHigh > 0) tooltip.add(
            StatCollector.translateToLocalFormatted(
                "item.galaxia.protection_shield_pressure.desc.high",
                pressureProtectionHigh));
        if (pressureProtectionLow > 0) tooltip.add(
            StatCollector
                .translateToLocalFormatted("item.galaxia.protection_shield_pressure.desc.low", pressureProtectionLow));
        if (radiationProtection > 0) tooltip.add(
            StatCollector
                .translateToLocalFormatted("item.galaxia.protection_shield_radiation.desc", radiationProtection));
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_PROTECTION_SHIELD };
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        if (!canEquip(stack, player)) return stack;

        boolean equipped = tryEquipOrReplace(player, stack);

        if (equipped && !player.capabilities.isCreativeMode) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
            if (player.openContainer != null) player.openContainer.detectAndSendChanges();
        }

        return stack;
    }

    private boolean tryEquipOrReplace(EntityPlayer player, ItemStack stack) {
        InventoryBaubles baubles = PlayerHandler.getPlayerBaubles(player);

        for (int i : Galaxia.shieldSlots) {
            if (!baubles.isItemValidForSlot(i, stack)) continue;

            int freeSlot = -1;

            for (int j : Galaxia.shieldSlots) {
                if (!baubles.isItemValidForSlot(j, stack)) continue;

                ItemStack inSlotTryTwo = baubles.getStackInSlot(j);
                if (inSlotTryTwo == null) {
                    freeSlot = j;
                    break;
                }
            }

            ItemStack inSlot = baubles.getStackInSlot(i);

            if (inSlot == null) {
                baubles.setInventorySlotContents(i, stack.copy());
                baubles.markDirty();
                onEquipped(stack, player);
                return true;
            }

            if (freeSlot != -1) {
                baubles.setInventorySlotContents(freeSlot, stack.copy());
                baubles.markDirty();
                onEquipped(stack, player);
                return true;
            }

            boolean added = player.inventory.addItemStackToInventory(inSlot.copy());
            if (!added) return false;
            baubles.setInventorySlotContents(i, stack.copy());
            baubles.markDirty();
            onEquipped(stack, player);
            return true;
        }

        return false;
    }

    // This is for the old Baubles system that I am forced to implement. We dep
    // Baubles-Extended anyways so this will
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

    public int getPressureProtectionHigh() {
        return this.pressureProtectionHigh;
    }

    public int getPressureProtectionLow() {
        return this.pressureProtectionLow;
    }

    public int getRadiationProtection() {
        return this.radiationProtection;
    }
}
