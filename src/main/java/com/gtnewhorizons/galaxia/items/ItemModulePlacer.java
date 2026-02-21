package com.gtnewhorizons.galaxia.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.block.GalaxiaBlocks;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;
import com.gtnewhorizons.galaxia.modules.TileEntityModuleController;

public class ItemModulePlacer extends Item {

    public ItemModulePlacer() {
        super();
        this.setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx,
        float hy, float hz) {
        if (world.isRemote) return true;

        world.setBlock(x, y + 1, z, GalaxiaBlocks.moduleController);
        TileEntityModuleController te = (TileEntityModuleController) world.getTileEntity(x, y + 1, z);
        te.setModule(ModuleTypes.HUB_3X3.getId());
        te.buildStructure();

        return true;
    }
}
