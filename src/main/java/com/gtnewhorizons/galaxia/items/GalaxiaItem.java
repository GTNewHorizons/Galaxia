package com.gtnewhorizons.galaxia.items;

import java.util.function.Supplier;

import net.minecraft.item.Item;

public class GalaxiaItem {

    static final Supplier<Item> DEFAULT_ITEM_FACTORY = Item::new;

    public static void registerAll() {
        for (GalaxiaItems entry : GalaxiaItems.values()) {
            entry.register();
        }
    }

    public static Item get(GalaxiaItems key) {
        return key.getItem();
    }
}
