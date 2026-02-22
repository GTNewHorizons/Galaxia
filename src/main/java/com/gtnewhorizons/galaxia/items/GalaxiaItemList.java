package com.gtnewhorizons.galaxia.items;

import static com.gtnewhorizons.galaxia.core.Galaxia.UNLOCALIZED_PREFIX;
import static com.gtnewhorizons.galaxia.items.GalaxiaItems.DEFAULT_ITEM_FACTORY;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaItemList {

    TELEPORTER("teleporter", 1, ItemTeleporter::new,
        (item) -> GameRegistry
            .addShapedRecipe(new ItemStack(item), "III", "IEI", "III", 'I', Items.iron_ingot, 'E', Items.ender_pearl)),
    ANOTHER_THING("another_thing"),
    DUST_CALX("calx_dust"),
    MODULE_PLACER("module_placer", ItemHabitatBuilder::new),
    MODULE_MOVER("module_mover", ItemModuleMover::new);

    private final String registryName;
    private final int maxStackSize;
    private final Supplier<Item> itemFactory;
    private final Consumer<Item> recipeAdder;
    private Item itemInstance;

    GalaxiaItemList(String registryName, int maxStackSize, Supplier<Item> itemFactory, Consumer<Item> recipeAdder) {
        this.registryName = registryName;
        this.maxStackSize = maxStackSize;
        this.itemFactory = itemFactory;
        this.recipeAdder = recipeAdder;
    }

    GalaxiaItemList(String registryName, Supplier<Item> itemFactory) {
        this(registryName, 64, itemFactory, null);
    }

    GalaxiaItemList(String registryName, Supplier<Item> itemFactory, Consumer<Item> recipeAdder) {
        this(registryName, 64, itemFactory, recipeAdder);
    }

    GalaxiaItemList(String registryName) {
        this(registryName, 64, DEFAULT_ITEM_FACTORY, null);
    }

    public void register() {
        Item item = itemFactory.get();
        item.setUnlocalizedName(UNLOCALIZED_PREFIX + registryName);
        item.setTextureName("galaxia:" + registryName);
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
