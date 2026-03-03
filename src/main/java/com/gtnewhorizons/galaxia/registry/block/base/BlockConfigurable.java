package com.gtnewhorizons.galaxia.registry.block.base;

import static com.gtnewhorizons.galaxia.core.Galaxia.TEXTURE_PREFIX;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

public class BlockConfigurable extends Block {

    private final String name;
    private final String textureName;

    private String harvestTool = "pickaxe";
    private int harvestLevel = 0;

    private Item dropItem = null;
    private int dropMeta = 0;

    private IIcon icon;

    public BlockConfigurable(String name) {
        super(Material.rock);
        this.name = name;
        this.textureName = name;

        setBlockName(name);
        setStepSound(soundTypeStone);
    }

    public BlockConfigurable harvest(String tool, int level) {
        this.harvestTool = tool == null ? "pickaxe" : tool;
        this.harvestLevel = level;
        setHarvestLevel(this.harvestTool, this.harvestLevel);
        return this;
    }

    public BlockConfigurable hardnessAndResistance(float hardness, float resistance) {
        setHardness(hardness);
        setResistance(resistance);
        return this;
    }

    public BlockConfigurable customSound(SoundType sound) {
        setStepSound(sound);
        return this;
    }

    public BlockConfigurable drop(Item item, int meta) {
        this.dropItem = item;
        this.dropMeta = meta;
        return this;
    }

    @Override
    public Item getItemDropped(int meta, java.util.Random rand, int fortune) {
        return dropItem != null ? dropItem : Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(int meta) {
        return dropItem != null ? dropMeta : 0;
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
