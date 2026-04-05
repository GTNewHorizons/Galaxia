package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockRocketController extends Block {

    @SideOnly(Side.CLIENT)
    protected IIcon frontIconOff;

    @SideOnly(Side.CLIENT)
    protected IIcon frontIconOn;

    protected Supplier<Block> controllerMaterial;

    protected final String onLocationString;

    protected final String offLocationString;

    protected BlockRocketController(String onIcon, String offIcon, Supplier<Block> material) {
        super(Material.rock);
        this.setHardness(1.5F);
        this.onLocationString = onIcon;
        this.offLocationString = offIcon;
        this.controllerMaterial = material;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == 0) { // item in inventory - show the controller face
            if (side == 4) return frontIconOff;
            return controllerMaterial.get()
                .getIcon(side, 0);
        }

        // Metavalues 2-5 for when the structure is unformed, 6-9 for when it is formed
        boolean formed = meta >= 6;
        int facing = formed ? meta - 4 : meta;

        if (side == facing) return formed ? frontIconOn : frontIconOff;
        return controllerMaterial.get()
            .getIcon(side, 0);
    }

    /**
     * Makes sure the controller face always faces the player
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        int facing = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;

        int meta = switch (facing) {
            case 0 -> ForgeDirection.NORTH.ordinal(); // player faces south, front faces north
            case 1 -> ForgeDirection.EAST.ordinal();
            case 2 -> ForgeDirection.SOUTH.ordinal();
            case 3 -> ForgeDirection.WEST.ordinal();
            default -> ForgeDirection.NORTH.ordinal();
        };
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.frontIconOff = iconRegister.registerIcon(offLocationString);
        this.frontIconOn = iconRegister.registerIcon(onLocationString);
    }

}
