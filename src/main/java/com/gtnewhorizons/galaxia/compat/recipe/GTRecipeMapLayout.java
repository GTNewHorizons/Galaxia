package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapFrontend;

public record GTRecipeMapLayout(int width, int height, List<Slot> itemInputs, List<Slot> itemOutputs,
    List<Slot> fluidInputs, List<Slot> fluidOutputs, @Nullable Slot specialItem, Progress progress) {

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
            slots(ui.itemInputPositionsGetter.apply(ui.maxItemInputs), ui, false, false),
            slots(ui.itemOutputPositionsGetter.apply(ui.maxItemOutputs), ui, false, true),
            slots(ui.fluidInputPositionsGetter.apply(ui.maxFluidInputs), ui, true, false),
            slots(ui.fluidOutputPositionsGetter.apply(ui.maxFluidOutputs), ui, true, true),
            specialItem(ui),
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
            null,
            new Progress(78, 24, 20, 18, true, null, ProgressWidget.Direction.RIGHT, 20));
    }

    private static List<Slot> slots(List<Pos2d> positions) {
        return slots(positions, null, false, false);
    }

    private static List<Slot> slots(List<Pos2d> positions, @Nullable BasicUIProperties ui, boolean fluid,
        boolean output) {
        if (positions == null || positions.isEmpty()) return Collections.emptyList();
        List<Slot> slots = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            Pos2d pos = positions.get(i);
            slots.add(new Slot(i, pos.x, pos.y, overlay(ui, i, fluid, output, false)));
        }
        return slots;
    }

    @Nullable
    private static Slot specialItem(BasicUIProperties ui) {
        if (!ui.useSpecialSlot || ui.specialItemPositionGetter == null) return null;
        Pos2d pos = ui.specialItemPositionGetter.get();
        if (pos == null) return null;
        return new Slot(0, pos.x, pos.y, overlay(ui, 0, false, false, true));
    }

    private static IDrawable overlay(@Nullable BasicUIProperties ui, int index, boolean fluid, boolean output,
        boolean special) {
        if (ui == null || ui.slotOverlaysMUI2 == null) return IDrawable.NONE;
        IDrawable overlay = ui.slotOverlaysMUI2.apply(index, fluid, output, special);
        return overlay != null ? overlay : IDrawable.NONE;
    }

    private static Progress progress(BasicUIProperties ui) {
        if (!ui.useProgressBar) return new Progress(0, 0, 0, 0, false, null, ProgressWidget.Direction.RIGHT, 0);
        return new Progress(
            ui.progressBarPos.x,
            ui.progressBarPos.y,
            ui.progressBarSize.width,
            ui.progressBarSize.height,
            true,
            ui.progressBarMUI2,
            ui.progressBarDirectionMUI2,
            ui.progressBarDirectionMUI2 == ProgressWidget.Direction.UP
                || ui.progressBarDirectionMUI2 == ProgressWidget.Direction.DOWN ? ui.progressBarSize.height
                    : ui.progressBarSize.width);
    }

    private static <T> List<T> immutable(List<T> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(input));
    }

    public record Slot(int index, int x, int y, IDrawable overlay) {

        public Slot(int index, int x, int y) {
            this(index, x, y, IDrawable.NONE);
        }
    }

    public record Progress(int x, int y, int width, int height, boolean enabled, @Nullable UITexture texture,
        ProgressWidget.Direction direction, int imageSize) {}
}
