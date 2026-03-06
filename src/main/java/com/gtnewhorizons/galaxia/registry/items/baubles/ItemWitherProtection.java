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

public class ItemWitherProtection extends Item implements IBaubleExpanded {

    public static final String BAUBLE_TYPE_WITHER_PROTECTION = "wither_protection";

    public ItemWitherProtection() {}

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        super.addInformation(stack, player, tooltip, p_77624_4_);
        tooltip.add(StatCollector.translateToLocalFormatted("item.galaxia.wither_protection.desc"));
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_WITHER_PROTECTION };
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

        for (int i : Galaxia.witherSlots) {
            if (!baubles.isItemValidForSlot(i, stack)) continue;

            ItemStack inSlot = baubles.getStackInSlot(i);

            if (inSlot == null) {
                baubles.setInventorySlotContents(i, stack.copy());
                baubles.markDirty();
                onEquipped(stack, player);
                return true;
            }

        }

        // No slots found - Look for potential swap
        for (int i : Galaxia.witherSlots) {
            if (!baubles.isItemValidForSlot(i, stack)) continue;
            ItemStack inSlot = baubles.getStackInSlot(i);
            boolean added = player.inventory.addItemStackToInventory(inSlot.copy());
            if (!added) return false;
            baubles.setInventorySlotContents(i, stack.copy());
            baubles.markDirty();
            onEquipped(stack, player);
            return true;
        }

        // No swaps or empty slots
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

}
