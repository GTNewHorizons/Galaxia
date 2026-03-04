package com.gtnewhorizons.galaxia.registry.items.baubles;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizons.galaxia.core.Galaxia;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;
import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;

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

    private boolean isInfinite() {
        return oxygenStorage == Integer.MAX_VALUE;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        if (!canEquip(stack, player)) return stack;

        boolean equipped = tryEquipOrReplace(player, stack);

        // clear held stack if equipped (works for both equip and replace)
        if (equipped && !player.capabilities.isCreativeMode) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
            if (player.openContainer != null) player.openContainer.detectAndSendChanges();
        }

        return stack;
    }

    private boolean tryEquipOrReplace(EntityPlayer player, ItemStack stack) {
        InventoryBaubles baubles = PlayerHandler.getPlayerBaubles(player);

        int[] oxygenSlots = Galaxia.oxygenSlots;

        int worstSlot = -1;
        int worstOxygen = Integer.MAX_VALUE;

        int[] slotsToCheck;
        if (oxygenSlots != null && oxygenSlots.length > 0) {
            slotsToCheck = oxygenSlots;
        } else {
            slotsToCheck = new int[baubles.getSizeInventory()];
            for (int i = 0; i < slotsToCheck.length; i++) slotsToCheck[i] = i;
        }

        for (int idx : slotsToCheck) {
            if (idx < 0 || idx >= baubles.getSizeInventory()) continue;

            // slot must accept this item
            if (!baubles.isItemValidForSlot(idx, stack)) continue;

            ItemStack inSlot = baubles.getStackInSlot(idx);

            // empty slot -> equip
            if (inSlot == null) {
                if (!player.capabilities.isCreativeMode) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
                baubles.setInventorySlotContents(idx, stack.copy());
                baubles.markDirty();
                onEquipped(stack, player);

                player.inventoryContainer.detectAndSendChanges();
                if (player.openContainer != null) player.openContainer.detectAndSendChanges();
                return true;
            }

            // only consider other oxygen tanks for replacement
            if (!isOxygenTank(inSlot)) continue;

            int oxygen = getOxygenSafe(inSlot);
            if (oxygen < worstOxygen) {
                worstOxygen = oxygen;
                worstSlot = idx;
            }
        }

        // replace the tank with the least oxygen
        if (worstSlot >= 0) {
            ItemStack old = baubles.getStackInSlot(worstSlot);

            boolean added = player.inventory.addItemStackToInventory(old);

            if (!added) {
                return false;
            }

            if (!player.capabilities.isCreativeMode) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }

            baubles.setInventorySlotContents(worstSlot, stack.copy());
            baubles.markDirty();
            onEquipped(stack, player);

            player.inventoryContainer.detectAndSendChanges();
            if (player.openContainer != null) player.openContainer.detectAndSendChanges();
            return true;
        }

        return false;
    }

    private boolean isOxygenTank(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemOxygenTank;
    }

    private int getOxygenSafe(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemOxygenTank tank)) return Integer.MAX_VALUE;
        return tank.getCurrentOxygen(stack);
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
        if (isInfinite()) return;
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
        if (isInfinite()) return true;

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
        if (isInfinite()) return false;
        return getDurabilityForDisplay(stack) != 0;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        super.addInformation(stack, player, tooltip, p_77624_4_);
        if (isInfinite()) tooltip.add(StatCollector.translateToLocal("item.galaxia.oxygen_tank.infinite"));
        else tooltip.add(
            StatCollector
                .translateToLocalFormatted("item.galaxia.oxygen_tank.desc", getCurrentOxygen(stack), oxygenStorage));
    }

    @Override
    public String[] getBaubleTypes(ItemStack itemstack) {
        return new String[] { BAUBLE_TYPE_OXYGEN_TANK };
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.UNIVERSAL;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {}

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

    @Override
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }
}
