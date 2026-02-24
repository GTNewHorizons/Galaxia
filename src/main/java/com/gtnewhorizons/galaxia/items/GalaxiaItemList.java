package com.gtnewhorizons.galaxia.items;

import static com.gtnewhorizons.galaxia.core.Galaxia.UNLOCALIZED_PREFIX;

import java.util.function.Supplier;

import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.items.armor.ItemSpaceSuit;
import com.gtnewhorizons.galaxia.items.special.ItemHabitatBuilder;
import com.gtnewhorizons.galaxia.items.special.ItemModuleMover;
import com.gtnewhorizons.galaxia.items.special.ItemTeleporter;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * ENUM for all Items in Galaxia
 */
public enum GalaxiaItemList {

    GALAXIA_LOGO("galaxia_logo"),
    TELEPORTER("teleporter", ItemTeleporter::new, 1),
    DUST_THEIA("theia_dust"),
    MODULE_PLACER("module_placer", ItemHabitatBuilder::new),
    MODULE_MOVER("module_mover", ItemModuleMover::new),
    SPACESUIT_HELMET("spacesuit_helmet", () -> new ItemSpaceSuit(ItemSpaceSuit.SUIT_MATERIAL, 0, 0), 1),
    SPACESUIT_CHESTPLATE("spacesuit_chestplate", () -> new ItemSpaceSuit(ItemSpaceSuit.SUIT_MATERIAL, 0, 1), 1),
    SPACESUIT_LEGGINGS("spacesuit_leggings", () -> new ItemSpaceSuit(ItemSpaceSuit.SUIT_MATERIAL, 0, 2), 1),
    SPACESUIT_BOOTS("spacesuit_boots", () -> new ItemSpaceSuit(ItemSpaceSuit.SUIT_MATERIAL, 0, 3), 1);

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
        this(registryName, Item::new, 64);
    }

    /**
     * Registers single item into the game
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
     * Registers all items into the game
     */
    public static void registerAll() {
        for (GalaxiaItemList entry : GalaxiaItemList.values()) {
            entry.register();
        }
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
