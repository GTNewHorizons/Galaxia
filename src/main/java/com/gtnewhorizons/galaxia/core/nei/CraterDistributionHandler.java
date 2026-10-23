package com.gtnewhorizons.galaxia.core.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.gt5.LunarRecipes;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterSize;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Lunar size and composition probabilities from the actual world-generation definitions. */
public final class CraterDistributionHandler extends TemplateRecipeHandler {

    private static final String ID = "galaxia.crater_distribution";

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal("galaxia.nei.craters");
    }

    @Override
    public String getGuiTexture() {
        return "minecraft:textures/gui/container/inventory.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) {
            for (CraterSize size : CraterSize.values()) arecipes.add(new Distribution(size));
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        for (CraterProfile profile : CraterProfile.values()) {
            for (ItemStack solid : LunarRecipes.solids(profile)) {
                if (solid.isItemEqual(result)) {
                    loadCraftingRecipes(ID);
                    return;
                }
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {}

    @Override
    public void drawExtras(int recipe) {
        CraterSize size = ((Distribution) arecipes.get(recipe)).size;
        var font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(StatCollector.translateToLocal("galaxia.nei.craters.luna"), 4, 2, 0x404040);
        font.drawString(
            StatCollector
                .translateToLocalFormatted("galaxia.nei.craters.occurrence", 100.0 / CraterSize.GENERATION_RARITY),
            4,
            14,
            0x404040);
        font.drawString(
            StatCollector.translateToLocalFormatted(
                "galaxia.nei.craters.size",
                StatCollector.translateToLocal(size.translationKey()),
                size.weight),
            4,
            26,
            0x404040);
        font.drawString(
            StatCollector
                .translateToLocalFormatted("galaxia.nei.craters.diameter", size.meanDiameter, size.diameterDeviation),
            4,
            38,
            0x404040);
        int row = 0;
        for (CraterProfile profile : CraterProfile.values()) {
            font.drawString(
                StatCollector.translateToLocal("galaxia.crater.profile." + profile.id),
                44,
                55 + row * 21,
                0x404040);
            font.drawString(profile.weight(size) + "%", 135, 65 + row * 21, 0x404040);
            row++;
        }
        font.drawString(StatCollector.translateToLocal("galaxia.nei.craters.provisional"), 4, 140, 0x606060);
    }

    private final class Distribution extends CachedRecipe {

        private final CraterSize size;
        private final List<PositionedStack> solids = new ArrayList<>();

        private Distribution(CraterSize size) {
            this.size = size;
            int row = 0;
            for (CraterProfile profile : CraterProfile.values()) {
                int column = 0;
                for (ItemStack solid : LunarRecipes.solids(profile)) {
                    solids.add(new PositionedStack(solid, 4 + column++ * 18, 54 + row * 21));
                }
                row++;
            }
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return solids;
        }
    }
}
