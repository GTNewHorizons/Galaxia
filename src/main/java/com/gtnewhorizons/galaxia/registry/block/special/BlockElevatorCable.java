package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Cable of the space elevator, taken from GTNH Intergalactic. Its textures, the climber model and the cable shader
 * still come from GregTech's Intergalactic assets
 */
public class BlockElevatorCable extends Block implements ITileEntityProvider {

    /** Domain of the borrowed elevator assets */
    public static final String ASSETS = "gtnhintergalactic";
    /** Number of steps which the cable light has */
    public static final int LIGHT_STEPS = 80;

    /** Cable part, cable light and the blinking light steps */
    @SideOnly(Side.CLIENT)
    public static IIcon[] textures;
    /** Render id, set by the client proxy */
    public static int renderID = -1;

    public BlockElevatorCable() {
        super(Material.iron);
        setBlockName("elevator_cable");
        setCreativeTab(Galaxia.creativeTab);
        setHarvestLevel("pickaxe", 2);
        setHardness(1.0f);
        setResistance(6.0f);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        textures = new IIcon[2 + LIGHT_STEPS];
        textures[0] = register.registerIcon(ASSETS + ":spaceElevator/CablePart");
        textures[1] = register.registerIcon(ASSETS + ":spaceElevator/CableLight");
        for (int i = 1; i < LIGHT_STEPS + 1; i++) {
            textures[i + 1] = register.registerIcon(ASSETS + ":spaceElevator/cable/CableLightBlinking" + i);
        }
        blockIcon = textures[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return textures[0];
    }

    @Override
    public int getRenderType() {
        return renderID;
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
    public float getExplosionResistance(Entity entity) {
        return 6.0f;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileElevatorCable();
    }
}
