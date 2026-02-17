package com.gtnewhorizons.galaxia.items;

import java.util.function.Supplier;

import net.minecraft.item.Item;

public class GalaxiaItems {

    static final Supplier<Item> DEFAULT_ITEM_FACTORY = Item::new;

    public static void registerAll() {
        for (GalaxiaItemList entry : GalaxiaItemList.values()) {
            entry.register();
        }
    }

    public static Item get(GalaxiaItemList key) {
        return key.getItem();
    }
}
