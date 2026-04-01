package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

final class OrbitalPinnedInfoPanel {

    interface Callbacks {

        void drawTooltip(String text, int x, int y);
    }

    private static final int PANEL_WIDTH = 116;
    private static final int PANEL_PADDING = 12;
    private static final int TEXT_LINE_HEIGHT = 9;
    private static final int ROW_GAP = 6;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int INLINE_ICON_SIZE = 12;
    private static final int INLINE_ICON_GAP = 1;
    private static final RenderItem GUI_ITEM_RENDERER = new RenderItem();

    private final Callbacks callbacks;
    private final List<PinnedInfoItemBounds> itemBounds = new ArrayList<>();

    OrbitalPinnedInfoPanel(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void draw(OrbitalCelestialBody body, List<PinnedInfoRow> rows, int widgetWidth, int widgetHeight) {
        itemBounds.clear();
        Minecraft mc = Minecraft.getMinecraft();
        int contentWidth = getContentWidth(mc, rows, widgetWidth);
        int boxWidth = contentWidth + PANEL_PADDING * 2;
        int boxHeight = 8;
        for (PinnedInfoRow row : rows) {
            boxHeight += getRowHeight(mc, row, contentWidth) + ROW_GAP;
        }
        if (!rows.isEmpty()) {
            boxHeight -= ROW_GAP;
        }
        boxHeight += 8;
        int x = Math.max(8, widgetWidth - boxWidth - 18);
        int y = Math.max(24, (widgetHeight - boxHeight) / 2);

        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, 0xFF162133);
        Gui.drawRect(x, y, x + boxWidth, y + 2, 0xFF7FB6FF);
        Gui.drawRect(x, y + boxHeight - 2, x + boxWidth, y + boxHeight, 0xFF7FB6FF);
        Gui.drawRect(x, y, x + 2, y + boxHeight, 0xFF7FB6FF);
        Gui.drawRect(x + boxWidth - 2, y, x + boxWidth, y + boxHeight, 0xFF7FB6FF);

        int textY = y + 8;
        for (PinnedInfoRow row : rows) {
            if (row.inlineItems) {
                drawInlineRow(mc, row, x + PANEL_PADDING, textY, contentWidth);
            } else {
                mc.fontRenderer.drawStringWithShadow(row.label, x + PANEL_PADDING, textY, 0xFF5A63FF);
            }
            if (!row.items.isEmpty() && !row.inlineItems) {
                drawItems(row.items, x + PANEL_PADDING, textY + 12, contentWidth);
            } else if (!row.inlineItems) {
                List<String> wrappedLines = wrapValue(mc, row.value, contentWidth);
                int lineY = textY + 12;
                for (String line : wrappedLines) {
                    mc.fontRenderer.drawStringWithShadow(line, x + PANEL_PADDING, lineY, 0xFFD9E0FF);
                    lineY += TEXT_LINE_HEIGHT;
                }
            }
            textY += getRowHeight(mc, row, contentWidth) + ROW_GAP;
        }
    }

    void drawHoveredItemTooltip(int mouseX, int mouseY) {
        if (itemBounds.isEmpty()) {
            return;
        }
        for (int i = itemBounds.size() - 1; i >= 0; i--) {
            PinnedInfoItemBounds bounds = itemBounds.get(i);
            if (bounds.contains(mouseX, mouseY)) {
                callbacks.drawTooltip(bounds.stack.getDisplayName(), mouseX, mouseY);
                return;
            }
        }
    }

    private int getContentWidth(Minecraft mc, List<PinnedInfoRow> rows, int widgetWidth) {
        int minContentWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int maxContentWidth = Math.max(minContentWidth, widgetWidth - 34 - PANEL_PADDING * 2);
        int contentWidth = minContentWidth;
        for (PinnedInfoRow row : rows) {
            if (!row.inlineItems) {
                continue;
            }
            int rowWidth = mc.fontRenderer.getStringWidth(row.value) + 4
                + row.items.size() * INLINE_ICON_SIZE
                + Math.max(0, row.items.size() - 1) * INLINE_ICON_GAP;
            contentWidth = Math.max(contentWidth, rowWidth);
        }
        return Math.min(contentWidth, maxContentWidth);
    }

    private int getRowHeight(Minecraft mc, PinnedInfoRow row, int contentWidth) {
        int height = TEXT_LINE_HEIGHT;
        if (row.inlineItems) {
            return Math.max(height, INLINE_ICON_SIZE);
        }
        if (!row.items.isEmpty()) {
            int itemsPerRow = Math.max(1, contentWidth / (ICON_SIZE + ICON_GAP));
            int itemRows = (row.items.size() + itemsPerRow - 1) / itemsPerRow;
            return height + 4 + itemRows * ICON_SIZE + Math.max(0, itemRows - 1) * ICON_GAP;
        }
        List<String> wrappedLines = wrapValue(mc, row.value, contentWidth);
        if (wrappedLines.isEmpty()) {
            return height;
        }
        return height + 4 + wrappedLines.size() * TEXT_LINE_HEIGHT;
    }

    private List<String> wrapValue(Minecraft mc, String value, int width) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        String[] paragraphs = value.split("\\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            lines.addAll(mc.fontRenderer.listFormattedStringToWidth(paragraph, width));
        }
        return lines;
    }

    private void drawItems(List<ItemStack> items, int x, int y, int contentWidth) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int itemsPerRow = Math.max(1, contentWidth / (ICON_SIZE + ICON_GAP));
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack == null) {
                continue;
            }
            int col = i % itemsPerRow;
            int row = i / itemsPerRow;
            int itemX = x + col * (ICON_SIZE + ICON_GAP);
            int itemY = y + row * (ICON_SIZE + ICON_GAP);
            drawGuiItemStack(stack, itemX, itemY, ICON_SIZE);
            itemBounds.add(new PinnedInfoItemBounds(stack, itemX, itemY, ICON_SIZE));
        }
    }

    private void drawInlineRow(Minecraft mc, PinnedInfoRow row, int x, int y, int contentWidth) {
        int itemsWidth = row.items.size() * INLINE_ICON_SIZE
            + Math.max(0, row.items.size() - 1) * INLINE_ICON_GAP;
        int iconsStartX = x + Math.max(0, contentWidth - itemsWidth);
        int labelMaxWidth = Math.max(12, iconsStartX - x - 4);
        String label = mc.fontRenderer.trimStringToWidth(row.value, labelMaxWidth);
        mc.fontRenderer.drawStringWithShadow(label, x, y + 1, 0xFFD9E0FF);

        for (int i = 0; i < row.items.size(); i++) {
            ItemStack stack = row.items.get(i);
            if (stack == null) {
                continue;
            }
            int itemX = iconsStartX + i * (INLINE_ICON_SIZE + INLINE_ICON_GAP);
            drawGuiItemStack(stack, itemX, y, INLINE_ICON_SIZE);
            itemBounds.add(new PinnedInfoItemBounds(stack, itemX, y, INLINE_ICON_SIZE));
        }
    }

    private void drawGuiItemStack(ItemStack stack, int x, int y, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        float scale = size / 16.0f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 200f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        float previousZ = GUI_ITEM_RENDERER.zLevel;
        GUI_ITEM_RENDERER.zLevel = 200f;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GUI_ITEM_RENDERER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        GUI_ITEM_RENDERER.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }
}
