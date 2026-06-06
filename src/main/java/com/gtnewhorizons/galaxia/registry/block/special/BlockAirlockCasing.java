package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.block.base.BlockCasing;

public class BlockAirlockCasing extends BlockCasing {

    public BlockAirlockCasing() {
        super("machine/airlock_casing");
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        BlockAirlockDoor.searchAndOpenDoor(world, x, y, z);
        return false;
    }
}
