package com.gtnewhorizons.galaxia.registry.block.planet.fluid;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBucket;

import com.gtnewhorizons.galaxia.core.Galaxia;

public class ItemBucketCustom extends ItemBucket {

    public ItemBucketCustom(Block block) {
        super(block);
        // substring to remove "tile."
        String cleanName = block.getUnlocalizedName()
            .substring(5);
        String bucketName = cleanName + "_bucket";

        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(Galaxia.TEXTURE_PREFIX + "fluid/" + bucketName);
        setUnlocalizedName(bucketName);
    }
}
