package com.gtnewhorizons.galaxia.items;

import java.util.function.Supplier;

import net.minecraft.item.Item;

/**
 * Basic class for item registry and factory
 */
public class GalaxiaItems {

    static final Supplier<Item> DEFAULT_ITEM_FACTORY = Item::new;

    /**
     * Registers all items in the ENUM
     */
    public static void registerAll() {
        for (GalaxiaItemList entry : GalaxiaItemList.values()) {
            entry.register();
        }
    }

    /**
     * Gets an item by its given key in the ENUM
     * 
     * @param key The key to be used in the retrieval
     * @return the Item the key responds to
     */
    public static Item get(GalaxiaItemList key) {
        return key.getItem();
    }
}
