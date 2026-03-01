package com.gtnewhorizons.galaxia.registry.block.base;

import java.util.Random;

import net.minecraft.block.BlockGlass;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSpaceStationGlass extends BlockGlass {

    public BlockSpaceStationGlass() {
        super(Material.iron, false);
        this.setBlockName("space_station_glass");
        this.stepSound = soundTypeMetal;
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.setCreativeTab(Galaxia.creativeTab);

    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("galaxia:space_station_glass");
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderBlockPass() {
        return 1;
    }
}
