package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

/**
 * Cartridge of a station machine. Its wear is kept in the stack's NBT, so a fresh cartridge stays a plain item, and
 * shows as a durability bar like on electric tools
 */
public class ItemStationCartridge extends Item {

    /** Wear at which a cartridge is used up */
    public static final int MAX_WEAR = 100;
    /** Ticks of work for one point of wear */
    public static final int TICKS_PER_WEAR = 360;

    private static final String WEAR = "wear";

    private final String descriptionKey;

    public ItemStationCartridge(String descriptionKey) {
        this.descriptionKey = descriptionKey;
        setNoRepair();
    }

    public static int getWear(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound()
            .getInteger(WEAR) : 0;
    }

    /**
     * Wears a cartridge down by one point
     *
     * @return True once the cartridge is used up
     */
    public static boolean wear(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        int wear = getWear(stack) + 1;
        stack.getTagCompound()
            .setInteger(WEAR, wear);
        return wear >= MAX_WEAR;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getWear(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return (double) getWear(stack) / MAX_WEAR;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(StatCollector.translateToLocal(descriptionKey));
        int minutes = (MAX_WEAR - getWear(stack)) * TICKS_PER_WEAR / 1200;
        tooltip.add(StatCollector.translateToLocalFormatted("galaxia.tooltip.station_cartridge.left", minutes));
    }
}
