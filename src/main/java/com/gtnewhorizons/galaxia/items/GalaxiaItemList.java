package com.gtnewhorizons.galaxia.items;

import static com.gtnewhorizons.galaxia.core.Galaxia.UNLOCALIZED_PREFIX;
import static com.gtnewhorizons.galaxia.items.GalaxiaItems.DEFAULT_ITEM_FACTORY;

import java.util.function.Supplier;

import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaItemList {

    TELEPORTER("teleporter", ItemTeleporter::new, 1),
    ANOTHER_THING("another_thing"),
    DUST_CALX("calx_dust"),
    MODULE_PLACER("module_placer", ItemHabitatBuilder::new),
    MODULE_MOVER("module_mover", ItemModuleMover::new);

    private final String registryName;
    private final int maxStackSize;
    private final Supplier<Item> itemFactory;
    private Item itemInstance;

    GalaxiaItemList(String registryName, Supplier<Item> itemFactory, int maxStackSize) {
        this.registryName = registryName;
        this.maxStackSize = maxStackSize;
        this.itemFactory = itemFactory;
    }

    GalaxiaItemList(String registryName, Supplier<Item> itemFactory) {
        this(registryName, itemFactory, 64);
    }

    GalaxiaItemList(String registryName) {
        this(registryName, DEFAULT_ITEM_FACTORY, 64);
    }

    public void register() {
        Item item = itemFactory.get();
        item.setUnlocalizedName(UNLOCALIZED_PREFIX + registryName);
        item.setTextureName("galaxia:" + registryName);
        item.setMaxStackSize(maxStackSize);
        item.setCreativeTab(Galaxia.creativeTab);

        GameRegistry.registerItem(item, registryName);
        this.itemInstance = item;
    }

    public Item getItem() {
        return itemInstance;
    }

    public String getRegistryName() {
        return registryName;
    }
}
