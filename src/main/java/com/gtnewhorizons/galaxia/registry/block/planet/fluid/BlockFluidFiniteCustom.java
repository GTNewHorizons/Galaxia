package com.gtnewhorizons.galaxia.registry.block.planet.fluid;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizons.galaxia.core.Galaxia;

public class BlockFluidFiniteCustom extends BlockFluidClassic {

    protected IIcon stillIcon;
    protected IIcon flowingIcon;

    protected final String texturePrefix; // textures: galaxia:prefix_still / prefix_flow
    protected final boolean distinctStillAndFlowTextures;

    public BlockFluidFiniteCustom(Fluid fluid, Material material, String texturePrefix,
        boolean distinctStillAndFlowTextures) {
        super(fluid, material);
        this.texturePrefix = texturePrefix;
        this.distinctStillAndFlowTextures = distinctStillAndFlowTextures;
        setBlockName(fluid.getName());
        setLightOpacity(3);
    }

    // effect on touch
    // @Override
    // public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
    // if (entity instanceof EntityLivingBase) {
    // ((EntityLivingBase) entity).addPotionEffect(new PotionEffect(Potion.poison.id, 20, 1));
    // }
    // }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        String prefix = Galaxia.TEXTURE_PREFIX + texturePrefix;
        stillIcon = reg.registerIcon(distinctStillAndFlowTextures ? prefix + "_still" : prefix);
        flowingIcon = reg.registerIcon(distinctStillAndFlowTextures ? prefix + "_flow" : prefix);
        getFluid().setIcons(stillIcon, flowingIcon);
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return side != 0 && side != 1 ? flowingIcon : stillIcon;
    }
}
