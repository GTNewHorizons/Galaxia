
package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockGantryTerminal extends BlockGantry {

    public BlockGantryTerminal() {
        super();
        this.setBlockTextureName("galaxia:machine/launchpad_sheeting");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantryTerminal();
    }

    @Override
    public int getRenderType() {
        return 0;
    }

}
