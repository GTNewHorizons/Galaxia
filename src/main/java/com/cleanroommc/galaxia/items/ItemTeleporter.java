package com.cleanroommc.galaxia.items;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.cleanroommc.galaxia.client.gui.GuiPlanetTeleporter;

public class ItemTeleporter extends Item {

    public ItemTeleporter() {
        super();
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiPlanetTeleporter());
        }
        player.swingItem();
        return stack;
    }
}
