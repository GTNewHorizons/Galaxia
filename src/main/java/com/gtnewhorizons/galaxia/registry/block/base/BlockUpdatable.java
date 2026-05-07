package com.gtnewhorizons.galaxia.registry.block.base;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.compat.GTUtility;

import gregtech.api.GregTechAPI;

public abstract class BlockUpdatable extends Block {

    protected BlockUpdatable(Material materialIn) {
        super(materialIn);

        if (GTUtility.isGTLoaded) {
            GregTechAPI.registerMachineBlock(this, -1);
        }
    }

    @Override
    public void onBlockAdded(World aWorld, int aX, int aY, int aZ) {
        if (GTUtility.isGTLoaded && GregTechAPI.isMachineBlock(this, aWorld.getBlockMetadata(aX, aY, aZ))) {
            GregTechAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }

    @Override
    public void breakBlock(World aWorld, int aX, int aY, int aZ, Block aBlock, int aMetaData) {
        if (GTUtility.isGTLoaded && GregTechAPI.isMachineBlock(this, aMetaData)) {
            GregTechAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }
}
