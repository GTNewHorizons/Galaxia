package com.gtnewhorizons.galaxia.registry.block.base;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.tile.TileNoduleController;

public class BlockNoduleController extends Block implements ITileEntityProvider {

    public BlockNoduleController() {
        super(Material.iron);
        this.setBlockName("nodule_controller");
        this.setBlockTextureName("galaxia:space_station_block_1");
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileNoduleController();
    }

    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (worldIn.isRemote) return true;
        TileEntity te = worldIn.getTileEntity(x, y, z);
        if (te instanceof TileNoduleController) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        if (!(neighbor instanceof BlockSpaceAir)) return;
        if (BlockSpaceAir.isBlockDepressurized(worldIn, x + 1, y, z)
            || BlockSpaceAir.isBlockDepressurized(worldIn, x - 1, y, z)
            || BlockSpaceAir.isBlockDepressurized(worldIn, x, y + 1, z)
            || BlockSpaceAir.isBlockDepressurized(worldIn, x, y - 1, z)
            || BlockSpaceAir.isBlockDepressurized(worldIn, x, y, z + 1)
            || BlockSpaceAir.isBlockDepressurized(worldIn, x, y, z - 1))
            ((TileNoduleController) worldIn.getTileEntity(x, y, z)).depressurize();

    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }
}
