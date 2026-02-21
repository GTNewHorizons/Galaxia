package com.gtnewhorizons.galaxia.modules;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockModuleController extends BlockContainer {

    public BlockModuleController() {
        super(Material.iron);
        setBlockName("module.controller");
        setHardness(-1F);
        setResistance(6000000F);
        setBlockUnbreakable();
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityModuleController();
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleController ctrl) {
            if (ctrl.getType() == null) {
                String id = ModuleTypes.HUB_3X3.getId();
                ctrl.setModule(id);
                ctrl.buildStructure();
            }
        }
    }

    @Override
    public boolean renderAsNormalBlock() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public int getRenderType() {
        return ModuleConfig.DEBUG_RENDER ? 0 : 1;
    }

    @Override
    public boolean isOpaqueCube() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public boolean isNormalCube() {
        return ModuleConfig.DEBUG_RENDER;
    }
}
