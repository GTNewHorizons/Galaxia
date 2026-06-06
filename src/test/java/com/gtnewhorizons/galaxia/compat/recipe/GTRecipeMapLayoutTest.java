package com.gtnewhorizons.galaxia.compat.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIProperties;

final class GTRecipeMapLayoutTest {

    @Test
    void layoutCopiesSlotAndProgressPositionsFromGT5UIProperties() throws Exception {
        BasicUIProperties props = properties(
            2,
            1,
            1,
            2,
            count -> List.of(new Pos2d(5, 6), new Pos2d(23, 6))
                .subList(0, count),
            count -> List.of(new Pos2d(106, 24))
                .subList(0, count),
            count -> List.of(new Pos2d(16, 62))
                .subList(0, count),
            count -> List.of(new Pos2d(106, 62), new Pos2d(124, 62))
                .subList(0, count),
            new Pos2d(78, 24),
            new Size(20, 18));

        GTRecipeMapLayout layout = GTRecipeMapLayout.fromProperties(props, 176, 86);

        assertEquals(176, layout.width());
        assertEquals(86, layout.height());
        assertEquals(
            List.of(new GTRecipeMapLayout.Slot(0, 5, 6)),
            layout.itemInputs()
                .subList(0, 1));
        assertEquals(
            new GTRecipeMapLayout.Slot(1, 23, 6),
            layout.itemInputs()
                .get(1));
        assertEquals(List.of(new GTRecipeMapLayout.Slot(0, 106, 24)), layout.itemOutputs());
        assertEquals(List.of(new GTRecipeMapLayout.Slot(0, 16, 62)), layout.fluidInputs());
        assertEquals(
            new GTRecipeMapLayout.Slot(1, 124, 62),
            layout.fluidOutputs()
                .get(1));
        assertEquals(
            78,
            layout.progress()
                .x());
        assertEquals(
            24,
            layout.progress()
                .y());
        assertEquals(
            20,
            layout.progress()
                .width());
        assertEquals(
            18,
            layout.progress()
                .height());
        assertEquals(
            true,
            layout.progress()
                .enabled());
    }

    private static BasicUIProperties properties(int maxItemInputs, int maxItemOutputs, int maxFluidInputs,
        int maxFluidOutputs, IntFunction<List<Pos2d>> itemInputs, IntFunction<List<Pos2d>> itemOutputs,
        IntFunction<List<Pos2d>> fluidInputs, IntFunction<List<Pos2d>> fluidOutputs, Pos2d progressPos,
        Size progressSize) throws Exception {
        return BasicUIProperties.builder()
            .maxItemInputs(maxItemInputs)
            .maxItemOutputs(maxItemOutputs)
            .maxFluidInputs(maxFluidInputs)
            .maxFluidOutputs(maxFluidOutputs)
            .itemInputPositionsGetter(itemInputs)
            .itemOutputPositionsGetter(itemOutputs)
            .fluidInputPositionsGetter(fluidInputs)
            .fluidOutputPositionsGetter(fluidOutputs)
            .progressBarPos(progressPos)
            .progressBarSize(progressSize)
            .useProgressBar(true)
            .build();
    }
}
