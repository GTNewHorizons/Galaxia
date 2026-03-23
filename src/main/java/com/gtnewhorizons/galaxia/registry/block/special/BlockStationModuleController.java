package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationModuleController;

public class BlockStationModuleController extends Block implements ITileEntityProvider {

    public BlockStationModuleController() {
        super(Material.iron);
        this.setBlockName("module_controller");
        this.setBlockTextureName("galaxia:space_station/space_station_block_1");
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileStationModuleController();
    }

    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (worldIn.isRemote) return true;
        TileEntity te = worldIn.getTileEntity(x, y, z);
        if (!(te instanceof TileStationModuleController)) return false;
        for (int[] d : BlockSpaceAir.adjacents) {
            if (BlockSpaceAir.isDepressurized(worldIn, x + d[0], y + d[1], z + d[2]))
                ((TileStationModuleController) te).depressurize();
        }
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        if (!(neighbor == Block.getBlockFromName("galaxia:space_air"))) return;
        if (BlockSpaceAir.isDepressurized(worldIn, x + 1, y, z) || BlockSpaceAir.isDepressurized(worldIn, x - 1, y, z)
            || BlockSpaceAir.isDepressurized(worldIn, x, y + 1, z)
            || BlockSpaceAir.isDepressurized(worldIn, x, y - 1, z)
            || BlockSpaceAir.isDepressurized(worldIn, x, y, z + 1)
            || BlockSpaceAir.isDepressurized(worldIn, x, y, z - 1))
            ((TileStationModuleController) worldIn.getTileEntity(x, y, z)).depressurize();
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
