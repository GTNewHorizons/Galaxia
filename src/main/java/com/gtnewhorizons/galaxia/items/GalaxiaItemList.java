package com.gtnewhorizons.galaxia.items;

import static com.gtnewhorizons.galaxia.core.Galaxia.UNLOCALIZED_PREFIX;
import static com.gtnewhorizons.galaxia.items.GalaxiaItems.DEFAULT_ITEM_FACTORY;

import java.util.function.Supplier;

import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * ENUM for all Items in Galaxia
 */
public enum GalaxiaItemList {

    TELEPORTER("teleporter", ItemTeleporter::new, 1),
    DUST_THEIA("theia_dust"),
    MODULE_PLACER("module_placer", ItemHabitatBuilder::new),
    MODULE_MOVER("module_mover", ItemModuleMover::new);

    private final String registryName;
    private final int maxStackSize;
    private final Supplier<Item> itemFactory;
    private Item itemInstance;

    /**
     * Constructor to initialize factory and registry
     * 
     * @param registryName Name of the registry
     * @param itemFactory  The Item Factory
     * @param maxStackSize The max stack size of the item
     */
    GalaxiaItemList(String registryName, Supplier<Item> itemFactory, int maxStackSize) {
        this.registryName = registryName;
        this.maxStackSize = maxStackSize;
        this.itemFactory = itemFactory;
    }

    /**
     * Constructor to initialize factory and registry, with maxStackSize defaulted to 64
     * 
     * @param registryName Name of the registry
     * @param itemFactory  The Item Factory
     */
    GalaxiaItemList(String registryName, Supplier<Item> itemFactory) {
        this(registryName, itemFactory, 64);
    }

    /**
     * Constructor to initalize the registry using default item factory and stack size of 64
     * 
     * @param registryName Name of the registry
     */
    GalaxiaItemList(String registryName) {
        this(registryName, DEFAULT_ITEM_FACTORY, 64);
    }

    /**
     * Registers all items into the game
     */
    public void register() {
        Item item = itemFactory.get();
        item.setUnlocalizedName(UNLOCALIZED_PREFIX + registryName);
        item.setTextureName("galaxia:" + registryName);
        item.setMaxStackSize(maxStackSize);
        item.setCreativeTab(Galaxia.creativeTab);

        GameRegistry.registerItem(item, registryName);
        this.itemInstance = item;
    }

    /**
     * Gets the item instance
     * 
     * @return Item instance
     */
    public Item getItem() {
        return itemInstance;
    }

    /**
     * Gets the registry name
     * 
     * @return Registry name
     */
    public String getRegistryName() {
        return registryName;
    }
}
