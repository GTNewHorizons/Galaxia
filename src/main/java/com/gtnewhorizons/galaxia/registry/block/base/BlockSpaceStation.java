package com.gtnewhorizons.galaxia.registry.block.base;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSpaceStation extends Block {

    private static final int METAS = 3;

    public BlockSpaceStation() {
        super(Material.iron);
        this.setBlockName("space_station_wall");
        this.stepSound = soundTypeMetal;
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.setCreativeTab(Galaxia.creativeTab);

    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item itemIn, CreativeTabs tab, List<ItemStack> list) {
        for (int i = 0; i < METAS; ++i) {
            list.add(new ItemStack(itemIn, 1, i));
        }
    }

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        meta = MathHelper.clamp_int(meta, 0, METAS - 1);
        return icons[meta];
    }

    public static class ItemBlockSpaceStation extends ItemBlock {

        public ItemBlockSpaceStation(Block block) {
            super(block);
            this.setHasSubtypes(true);
            this.setMaxDamage(0);
        }

        @Override
        public int getMetadata(int damage) {
            return damage;
        }

        @Override
        public String getUnlocalizedName(final ItemStack stack) {
            return this.getUnlocalizedName() + "." + stack.getItemDamage();
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[METAS];
        for (int i = 0; i < METAS; ++i) {
            icons[i] = reg.registerIcon("galaxia:space_station_block_" + i);
        }
    }
}
