package com.gtnewhorizons.galaxia.registry.block.base;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;

public abstract class BlockUpdatable extends Block {

    protected BlockUpdatable(Material materialIn) {
        super(materialIn);
        GalaxiaAPI.registerMachineBlock(this, -1);
    }

    @Override
    public void onBlockAdded(World aWorld, int aX, int aY, int aZ) {
        blockUpdate(aWorld, aX, aY, aZ, aWorld.getBlockMetadata(aX, aY, aZ));
    }

    @Override
    public void breakBlock(World aWorld, int aX, int aY, int aZ, Block aBlock, int aMetaData) {
        blockUpdate(aWorld, aX, aY, aZ, aMetaData);
    }

    /**
     * This is for listening and propagating block updates for MultiMultiblocks
     */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        super.onNeighborBlockChange(world, x, y, z, neighbor);
        blockUpdate(world, x, y, z, world.getBlockMetadata(x, y, z));
    }

    public void blockUpdate(World aWorld, int aX, int aY, int aZ, int aMetaData) {
        if (GalaxiaAPI.isMachineBlock(this, aMetaData)) {
            GalaxiaAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }
}
