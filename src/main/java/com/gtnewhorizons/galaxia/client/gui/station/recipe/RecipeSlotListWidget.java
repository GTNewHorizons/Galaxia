package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

public class RecipeSlotListWidget extends ParentWidget<RecipeSlotListWidget> {

    private static final int TEXT_COLOR = EnumColors.MAP_COLOR_TEXT_BODY.getColor();
    private static final int SECTION_COLOR = EnumColors.MAP_COLOR_TEXT_SECTION.getColor();
    private static final int MUTED_COLOR = EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
    private static final int ENABLED_COLOR = EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor();
    private static final int DISABLED_COLOR = EnumColors.MAP_COLOR_TEXT_DANGER.getColor();
    private static final int LINE_GAP = 3;

    private final ModuleInstance module;
    public int addRecipeX, addRecipeY, addRecipeW;

    public RecipeSlotListWidget(@Nullable ModuleInstance module) {
        this.module = module;
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    /**
     * Draws recipe slot information for an IRecipeModule.
     * Called from ModuleDetailPanel.drawBackground().
     *
     * @return the y-coordinate after the last drawn line
     */
    public int draw(int x, int y, int width) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (fr == null) return y;

        if (module == null || !(module.component() instanceof IRecipeModule recipeModule)) {
            return y;
        }

        RecipeConfig config = recipeModule.getRecipeConfig();

        fr.drawStringWithShadow("Recipes", x, y, SECTION_COLOR);
        y += fr.FONT_HEIGHT + LINE_GAP;

        if (config == null) {
            fr.drawStringWithShadow("No recipes configured", x, y, MUTED_COLOR);
            y += fr.FONT_HEIGHT + LINE_GAP;
            fr.drawStringWithShadow("Use recipe picker to add", x, y, MUTED_COLOR);
            y += fr.FONT_HEIGHT + LINE_GAP;
        } else {
            fr.drawStringWithShadow(
                "Mode: " + config.mode()
                    .name(),
                x,
                y,
                TEXT_COLOR);
            y += fr.FONT_HEIGHT + LINE_GAP;

            List<RecipeSlot> slots = config.slots()
                .toList();
            if (slots.isEmpty()) {
                fr.drawStringWithShadow("No recipe slots configured", x, y, MUTED_COLOR);
                y += fr.FONT_HEIGHT + LINE_GAP;
            } else {
                fr.drawStringWithShadow("Configured recipes:", x, y, TEXT_COLOR);
                y += fr.FONT_HEIGHT + LINE_GAP;

                int slotIdx = 0;
                for (RecipeSlot slot : slots) {
                    int maxWidth = width - 4;
                    fr.drawStringWithShadow("#" + slotIdx + "  ", x, y, TEXT_COLOR);
                    int cx = x + fr.getStringWidth("#" + slotIdx + "  ");
                    String enabledChar = slot.enabled() ? "\u2714" : "\u2718";
                    int enabledColor = slot.enabled() ? ENABLED_COLOR : DISABLED_COLOR;
                    fr.drawStringWithShadow(enabledChar, cx, y, enabledColor);
                    cx += fr.getStringWidth(enabledChar) + 2;
                    String rest = "  in:" + slot.inputGuard()
                        + "  out:"
                        + slot.outputGuard()
                        + "  pri:"
                        + (slot.priority() & 0xFF)
                        + "  size:"
                        + (slot.orderSize() & 0xFF);
                    String trimmedRest = fr.trimStringToWidth(rest, maxWidth - (cx - x));
                    fr.drawStringWithShadow(trimmedRest, cx, y, TEXT_COLOR);

                    String removeLabel = "  [Remove]";
                    int removeX = x + width - fr.getStringWidth(removeLabel) - 2;
                    fr.drawStringWithShadow(removeLabel, removeX, y, MUTED_COLOR);

                    y += fr.FONT_HEIGHT + LINE_GAP;
                    slotIdx++;
                }
            }
        }

        // Add Recipe placeholder — always visible
        y += LINE_GAP;
        addRecipeX = x;
        addRecipeY = y;
        addRecipeW = fr.getStringWidth("[Add Recipe]");
        fr.drawStringWithShadow("[Add Recipe]", x, y, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

        return y + fr.FONT_HEIGHT + LINE_GAP;
    }
}
