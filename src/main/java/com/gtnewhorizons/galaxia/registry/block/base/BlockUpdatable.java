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

    public void blockUpdate(World aWorld, int aX, int aY, int aZ) {
        blockUpdate(aWorld, aX, aY, aZ, aWorld.getBlockMetadata(aX, aY, aZ));
    }

    public void blockUpdate(World aWorld, int aX, int aY, int aZ, int aMetaData) {
        if (GalaxiaAPI.isMachineBlock(this, aMetaData)) {
            GalaxiaAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }
}
