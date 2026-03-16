package com.gtnewhorizons.galaxia.core.nei;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;

import blockrenderer6343.integration.nei.MultiblockHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

public class GalaxiaMultiblockHandler extends MultiblockHandler {

    private static final Map<Block, GalaxiaMultiblockBase<?>> REGISTRY = new HashMap<>();

    public static void register(GalaxiaMultiblockBase<?> prototype) {
        REGISTRY.put(prototype.getControllerBlock(), prototype);
    }

    public GalaxiaMultiblockHandler() {
        super(new GalaxiaGuiMultiblockHandler());
    }

    @Override
    public TemplateRecipeHandler newInstance() {
        return new GalaxiaMultiblockHandler();
    }

    @Override
    public @NotNull ItemStack getConstructableStack(IConstructable multiblock) {
        if (multiblock instanceof GalaxiaMultiblockBase<?>base) {
            return new ItemStack(base.getControllerBlock());
        }
        return new ItemStack(Items.paper);
    }

    @Override
    protected @NotNull ObjectSet<IConstructable> tryLoadingMultiblocks(ItemStack candidate) {
        ObjectSet<IConstructable> result = new ObjectOpenHashSet<>();
        if (candidate == null) return result;
        Block block = Block.getBlockFromItem(candidate.getItem());
        GalaxiaMultiblockBase<?> found = REGISTRY.get(block);
        if (found != null) result.add(found);
        return result;
    }
}
