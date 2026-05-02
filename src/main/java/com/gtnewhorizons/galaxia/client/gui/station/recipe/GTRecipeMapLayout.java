package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapFrontend;

public record GTRecipeMapLayout(int width, int height, List<Slot> itemInputs, List<Slot> itemOutputs,
    List<Slot> fluidInputs, List<Slot> fluidOutputs, Progress progress) {

    public static final int DEFAULT_WIDTH = 176;
    public static final int DEFAULT_HEIGHT = 86;

    public GTRecipeMapLayout {
        itemInputs = immutable(itemInputs);
        itemOutputs = immutable(itemOutputs);
        fluidInputs = immutable(fluidInputs);
        fluidOutputs = immutable(fluidOutputs);
    }

    public static GTRecipeMapLayout fromRecipeMap(@Nullable RecipeMap<?> map) {
        return map != null ? fromFrontend(map.getFrontend()) : fallback();
    }

    public static GTRecipeMapLayout fromFrontend(@Nullable RecipeMapFrontend frontend) {
        if (frontend == null) return fallback();
        BasicUIProperties ui = frontend.getUIProperties();
        Size backgroundSize = frontend.getNEIProperties().recipeBackgroundSize;
        return fromProperties(ui, backgroundSize.width, backgroundSize.height);
    }

    public static GTRecipeMapLayout fromProperties(@Nullable BasicUIProperties ui, int width, int height) {
        if (ui == null) return fallback();
        return new GTRecipeMapLayout(
            width,
            height,
            slots(ui.itemInputPositionsGetter.apply(ui.maxItemInputs)),
            slots(ui.itemOutputPositionsGetter.apply(ui.maxItemOutputs)),
            slots(ui.fluidInputPositionsGetter.apply(ui.maxFluidInputs)),
            slots(ui.fluidOutputPositionsGetter.apply(ui.maxFluidOutputs)),
            progress(ui));
    }

    public static GTRecipeMapLayout fallback() {
        return new GTRecipeMapLayout(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            new Progress(78, 24, 20, 18, true));
    }

    private static List<Slot> slots(List<Pos2d> positions) {
        if (positions == null || positions.isEmpty()) return Collections.emptyList();
        List<Slot> slots = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            Pos2d pos = positions.get(i);
            slots.add(new Slot(i, pos.x, pos.y));
        }
        return slots;
    }

    private static Progress progress(BasicUIProperties ui) {
        if (!ui.useProgressBar) return new Progress(0, 0, 0, 0, false);
        return new Progress(
            ui.progressBarPos.x,
            ui.progressBarPos.y,
            ui.progressBarSize.width,
            ui.progressBarSize.height,
            true);
    }

    private static <T> List<T> immutable(List<T> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(input));
    }

    public record Slot(int index, int x, int y) {}

    public record Progress(int x, int y, int width, int height, boolean enabled) {}
}
