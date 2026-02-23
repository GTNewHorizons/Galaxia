package com.gtnewhorizons.galaxia.block.base;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.gtnewhorizons.galaxia.block.planet.BlockPlanetGalaxia;
import com.gtnewhorizons.galaxia.core.Galaxia;

/**
 * The class to define all ItemBlocks for Galaxia Planet blocks
 */
public class ItemBlockGalaxiaPlanet extends ItemBlock {

    private final BlockPlanetGalaxia planetBlock;

    public ItemBlockGalaxiaPlanet(Block block) {
        super(block);
        this.planetBlock = (BlockPlanetGalaxia) block;
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /**
     * Gets the metadata of the item block (damage)
     *
     * @param damage the damage to the item block
     * @return Metadata of the item block
     */
    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    /**
     * Adds all metadata variants of the item block to the given list (used in creative tab)
     *
     * @param item The base item block to register variants
     * @param tab  The creative tab for the galaxia blocks
     * @param list The list of all meta-variants
     */
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (tab == Galaxia.creativeTab || tab == CreativeTabs.tabAllSearch) {
            for (int i = 0; i < planetBlock.getVariantCount(); i++) {
                list.add(new ItemStack(item, 1, i));
            }
        }
    }

    /**
     * Gets the unlocalized name of the provided ItemStack for registry
     *
     * @param stack The item stack for which the name is required
     * @return Unlocalized name of the item stack
     */
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int meta = MathHelper.clamp_int(stack.getItemDamage(), 0, planetBlock.getVariantCount() - 1);
        String suffix = planetBlock.getVariantSuffix(meta);
        return "tile." + planetBlock.getPlanetName() + capitalize(suffix);
    }

    /**
     * Capitalizes the first letter of a string
     *
     * @param s The string to capitalized
     * @return Capitalized string
     */
    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? ""
            : s.substring(0, 1)
                .toUpperCase() + s.substring(1);
    }
}
