package com.gtnewhorizons.galaxia.registry.block.base;

import static com.gtnewhorizons.galaxia.core.Galaxia.TEXTURE_PREFIX;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockCasing extends BlockUpdatable {

    private IIcon icon;
    private boolean ignoreSimilatiry = true;
    private boolean isTransparent = false;

    public BlockCasing(String name) {
        super(Material.iron);
        int last = name.lastIndexOf('/');
        String registryName = (last >= 0) ? name.substring(last + 1) : name;
        this.textureName = name;

        setBlockName(registryName);
        setStepSound(soundTypeStone);
    }

    public BlockCasing harvest(String tool, int level) {
        setHarvestLevel(tool, level);
        return this;
    }

    public BlockCasing hardnessAndResistance(float hardness, float resistance) {
        setHardness(hardness);
        setResistance(resistance);
        return this;
    }

    public BlockCasing customStepSound(SoundType sound) {
        setStepSound(sound);
        return this;
    }

    public BlockCasing transparent() {
        this.isTransparent = true;
        setLightOpacity(0);
        return this;
    }

    public BlockCasing glass() {
        this.ignoreSimilatiry = false;
        return transparent();
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.icon = reg.registerIcon(TEXTURE_PREFIX + textureName);
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderBlockPass() {
        return isTransparent ? 1 : 0;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return !isTransparent;
    }

    @Override
    public boolean isOpaqueCube() {
        return !isTransparent;
    }

    @Override
    protected boolean canSilkHarvest() {
        return isTransparent || super.canSilkHarvest();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        if (isTransparent) {
            Block block = worldIn.getBlock(x, y, z);
            return !this.ignoreSimilatiry && block == this ? false : super.shouldSideBeRendered(worldIn, x, y, z, side);
        }
        return super.shouldSideBeRendered(worldIn, x, y, z, side);
    }
}
