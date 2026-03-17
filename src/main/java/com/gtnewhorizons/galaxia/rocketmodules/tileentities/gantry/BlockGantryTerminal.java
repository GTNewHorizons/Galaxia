
package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockGantryTerminal extends BlockGantry {

    public BlockGantryTerminal() {
        super();
        this.setBlockTextureName("gold_block");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantryTerminal();
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 0;
    }

}
