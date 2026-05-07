package com.gtnewhorizons.galaxia.registry.block.base;

import static com.gtnewhorizons.galaxia.core.Galaxia.TEXTURE_PREFIX;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

// TODO: make proper builder for configuration
public class BlockCasing extends BlockUpdatable {

    private IIcon icon;

    public BlockCasing(String name) {
        super(Material.iron);
        int last = name.lastIndexOf('/');
        String registryName = (last >= 0) ? name.substring(last + 1) : name;
        this.textureName = name;

        setBlockName(registryName);
        setStepSound(soundTypeStone);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.icon = reg.registerIcon(TEXTURE_PREFIX + textureName);
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
    }

}
