package com.gtnewhorizons.galaxia.items;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.Galaxia;

import cpw.mods.fml.common.registry.GameRegistry;

public class GalaxiaItems {

    private static final Supplier<Item> DEFAULT_ITEM_FACTORY = Item::new;
    private static final String UNLOCALIZED_PREFIX = "galaxia.";

    public enum GalaxiaItem {

        TELEPORTER("teleporter", 1, ItemTeleporter::new, (item) -> GameRegistry
            .addShapedRecipe(new ItemStack(item), "III", "IEI", "III", 'I', Items.iron_ingot, 'E', Items.ender_pearl)),
        ANOTHER_THING("anotherThing");

        private final String registryName;
        private final int maxStackSize;
        private final Supplier<Item> itemFactory;
        private final Consumer<Item> recipeAdder;
        private Item itemInstance;

        GalaxiaItem(String registryName, int maxStackSize, Supplier<Item> itemFactory, Consumer<Item> recipeAdder) {
            this.registryName = registryName;
            this.maxStackSize = maxStackSize;
            this.itemFactory = itemFactory;
            this.recipeAdder = recipeAdder;
        }

        GalaxiaItem(String registryName, Supplier<Item> itemFactory) {
            this(registryName, 64, itemFactory, null);
        }

        GalaxiaItem(String registryName, Supplier<Item> itemFactory, Consumer<Item> recipeAdder) {
            this(registryName, 64, itemFactory, recipeAdder);
        }

        GalaxiaItem(String registryName) {
            this(registryName, 64, DEFAULT_ITEM_FACTORY, null);
        }

        public void register() {
            Item item = itemFactory.get();
            item.setUnlocalizedName(UNLOCALIZED_PREFIX + registryName);
            item.setMaxStackSize(maxStackSize);
            item.setCreativeTab(Galaxia.creativeTab);

            GameRegistry.registerItem(item, registryName);
            this.itemInstance = item;

            if (recipeAdder != null) {
                recipeAdder.accept(item);
            }
        }

        public Item getItem() {
            return itemInstance;
        }

        public String getRegistryName() {
            return registryName;
        }
    }

    public static void registerAll() {
        for (GalaxiaItem entry : GalaxiaItem.values()) {
            entry.register();
        }
    }

    public static Item get(GalaxiaItem key) {
        return key.getItem();
    }
}
