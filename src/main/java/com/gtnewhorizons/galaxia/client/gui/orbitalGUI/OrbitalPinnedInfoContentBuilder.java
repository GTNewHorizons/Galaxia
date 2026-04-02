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
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.GtOreVeinDefinition;

public final class OrbitalPinnedInfoContentBuilder {

    List<PinnedInfoRow> buildRows(OrbitalCelestialBody body) {
        List<PinnedInfoRow> rows = new ArrayList<>();
        rows.add(new PinnedInfoRow("Name", body.displayName()));
        rows.add(new PinnedInfoRow("Type", formatObjectClass(body.objectClass())));
        rows.add(new PinnedInfoRow("Landable", isLandable(body) ? "Yes" : "No"));
        rows.add(new PinnedInfoRow("Dangers", buildDangerSummary(body)));
        if (body.objectClass() != CelestialObjectClass.STAR && body.objectClass() != CelestialObjectClass.GALAXY) {
            rows.add(new PinnedInfoRow("Surface", formatSurfaceType(body)));
            if (!body.properties()
                .gtOreVeins()
                .isEmpty()) {
                rows.add(PinnedInfoRow.section("Veins"));
                for (GtOreVeinDefinition vein : body.properties()
                    .gtOreVeins())
                    rows.add(PinnedInfoRow.inlineItems(vein.displayName(), resolveGtVeinDisplayItems(vein)));
            } else if (body.properties()
                .ores()
                .isEmpty()) {
                    rows.add(new PinnedInfoRow("Ores", "Undefined"));
                } else {
                    rows.add(
                        new PinnedInfoRow(
                            "Ores",
                            "",
                            body.properties()
                                .ores()));
                }
        }
        return rows;
    }

    private String buildDangerSummary(OrbitalCelestialBody body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties()
            .radiation() >= 0.25) dangers.add("Radiation");
        if (body.properties()
            .temperature() > 360) dangers.add("Heat");
        if (body.properties()
            .temperature() > 0
            && body.properties()
                .temperature() < 120)
            dangers.add("Cold");
        if (!body.properties()
            .visitable() && body.properties()
                .canCreateOutpost())
            dangers.add("Remote");
        return dangers.isEmpty() ? "None" : String.join(", ", dangers);
    }

    private String formatObjectClass(CelestialObjectClass objectClass) {
        String raw = objectClass.name()
            .toLowerCase()
            .replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isLandable(OrbitalCelestialBody body) {
        return switch (body.objectClass()) {
            case PLANET, MOON, ASTEROID -> body.properties()
                .visitable();
            default -> false;
        };
    }

    private String formatSurfaceType(OrbitalCelestialBody body) {
        String surfaceType = body.properties()
            .metadata()
            .get("surface");
        if (surfaceType == null || surfaceType.isEmpty()) return "Undefined";
        return formatInfoToken(surfaceType);
    }

    private String formatInfoToken(String value) {
        String[] parts = value.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)))
                .append(part.substring(1));
        }
        return out.toString();
    }

    private List<ItemStack> resolveGtVeinDisplayItems(GtOreVeinDefinition vein) {
        List<ItemStack> items = new ArrayList<>();
        if (vein == null) return items;
        for (String oreName : vein.ores()) {
            ItemStack stack = resolveGtOreDisplayStack(oreName);
            if (stack != null) items.add(stack);
        }
        return items;
    }

    private ItemStack resolveGtOreDisplayStack(String oreName) {
        if (oreName == null || oreName.isEmpty()) return null;
        String materialKey = oreName.replaceAll("[^A-Za-z0-9]", "");
        String[] oreDictKeys = new String[] { "ore" + materialKey, "gem" + materialKey, "dust" + materialKey,
            "dustImpure" + materialKey, "crushed" + materialKey };
        for (String oreDictKey : oreDictKeys) {
            List<ItemStack> matches = OreDictionary.getOres(oreDictKey, false);
            if (matches == null || matches.isEmpty()) continue;
            ItemStack match = matches.get(0);
            if (match != null) return match.copy();
        }
        return null;
    }

    public static final class OrbitalPinnedInfoWidget extends ParentWidget<OrbitalPinnedInfoWidget> {

        interface Callbacks {

            OrbitalCelestialBody getPinnedInfoBody();

            List<PinnedInfoRow> buildRows(OrbitalCelestialBody body);
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
        private String lastSignature = "";

        OrbitalPinnedInfoWidget(Callbacks callbacks) {
            this.callbacks = callbacks;
            setEnabled(false);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            OrbitalCelestialBody body = callbacks.getPinnedInfoBody();
            if (body == null) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                lastSignature = "";
                setEnabled(false);
                return;
            }
            setEnabled(true);
            List<PinnedInfoRow> rows = callbacks.buildRows(body);
            String signature = buildSignature(body, rows);
            if (!signature.equals(lastSignature)) {
                rebuildChildren(body, rows);
                lastSignature = signature;
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        private String buildSignature(OrbitalCelestialBody body, List<PinnedInfoRow> rows) {
            StringBuilder signature = new StringBuilder(256);
            signature.append(body.id())
                .append('|')
                .append(getArea().width)
                .append('|')
                .append(getArea().height);
            for (PinnedInfoRow row : rows) signature.append('|')
                .append(row.label())
                .append(':')
                .append(row.value())
                .append(':')
                .append(row.inlineItems())
                .append(':')
                .append(
                    row.items()
                        .size());
            return signature.toString();
        }

        private void rebuildChildren(OrbitalCelestialBody body, List<PinnedInfoRow> rows) {
            removeAll();
            Minecraft mc = Minecraft.getMinecraft();
            int contentWidth = getContentWidth(mc, rows, getArea().width);
            int boxWidth = contentWidth + PANEL_PADDING * 2;
            int boxHeight = 8;
            for (PinnedInfoRow row : rows) boxHeight += getRowHeight(mc, row, contentWidth) + ROW_GAP;
            if (!rows.isEmpty()) boxHeight -= ROW_GAP;
            boxHeight += 8;
            int x = Math.max(8, getArea().width - boxWidth - 18);
            int y = Math.max(24, (getArea().height - boxHeight) / 2);
            ParentWidget<?> root = new ParentWidget<>().pos(x, y)
                .size(boxWidth, boxHeight);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(createBackgroundDrawable());
            root.child(backgroundLayer);
            root.child(WidgetOutline.create(backgroundLayer, 2, 0xFF7FB6FF));
            int currentY = 8;
            for (PinnedInfoRow row : rows) {
                buildRow(root, mc, row, contentWidth, currentY);
                currentY += getRowHeight(mc, row, contentWidth) + ROW_GAP;
            }
            child(root);
        }

        private void buildRow(ParentWidget<?> root, Minecraft mc, PinnedInfoRow row, int contentWidth, int y) {
            if (row.inlineItems()) {
                buildInlineRow(root, mc, row, contentWidth, y);
                return;
            }
            root.child(
                new TextWidget<>(row.label()).color(0xFF5A63FF)
                    .shadow(true)
                    .pos(PANEL_PADDING, y));
            if (!row.items()
                .isEmpty()) {
                buildItemGrid(root, row.items(), PANEL_PADDING, y + 12, contentWidth);
                return;
            }
            List<String> wrappedLines = wrapValue(mc, row.value(), contentWidth);
            int lineY = y + 12;
            for (String line : wrappedLines) {
                root.child(
                    new TextWidget<>(line).color(0xFFD9E0FF)
                        .shadow(true)
                        .pos(PANEL_PADDING, lineY));
                lineY += TEXT_LINE_HEIGHT;
            }
        }

        private void buildItemGrid(ParentWidget<?> root, List<ItemStack> items, int x, int y, int contentWidth) {
            if (items == null || items.isEmpty()) return;
            int itemsPerRow = Math.max(1, contentWidth / (ICON_SIZE + ICON_GAP));
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null) continue;
                int col = i % itemsPerRow;
                int row = i / itemsPerRow;
                int itemX = x + col * (ICON_SIZE + ICON_GAP);
                int itemY = y + row * (ICON_SIZE + ICON_GAP);
                root.child(
                    createItemWidget(stack, ICON_SIZE).pos(itemX, itemY)
                        .size(ICON_SIZE, ICON_SIZE));
            }
        }

        private void buildInlineRow(ParentWidget<?> root, Minecraft mc, PinnedInfoRow row, int contentWidth, int y) {
            int itemsWidth = row.items()
                .size() * INLINE_ICON_SIZE
                + Math.max(
                    0,
                    row.items()
                        .size() - 1)
                    * INLINE_ICON_GAP;
            int iconsStartX = PANEL_PADDING + Math.max(0, contentWidth - itemsWidth);
            int labelMaxWidth = Math.max(12, iconsStartX - PANEL_PADDING - 4);
            String label = mc.fontRenderer.trimStringToWidth(row.value(), labelMaxWidth);
            root.child(
                new TextWidget<>(label).color(0xFFD9E0FF)
                    .shadow(true)
                    .pos(PANEL_PADDING, y + 1));
            for (int i = 0; i < row.items()
                .size(); i++) {
                ItemStack stack = row.items()
                    .get(i);
                if (stack == null) continue;
                int itemX = iconsStartX + i * (INLINE_ICON_SIZE + INLINE_ICON_GAP);
                root.child(
                    createItemWidget(stack, INLINE_ICON_SIZE).pos(itemX, y)
                        .size(INLINE_ICON_SIZE, INLINE_ICON_SIZE));
            }
        }

        private Widget<?> createItemWidget(ItemStack stack, int size) {
            ItemStack displayStack = stack.copy();
            return drawable((context, x, y, width, height) -> drawGuiItemStack(displayStack, x, y, size)).asWidget()
                .tooltip(t -> t.addLine(displayStack.getDisplayName()));
        }

        private int getContentWidth(Minecraft mc, List<PinnedInfoRow> rows, int widgetWidth) {
            int minContentWidth = PANEL_WIDTH - PANEL_PADDING * 2;
            int maxContentWidth = Math.max(minContentWidth, widgetWidth - 34 - PANEL_PADDING * 2);
            int contentWidth = minContentWidth;
            for (PinnedInfoRow row : rows) {
                if (!row.inlineItems()) continue;
                int rowWidth = mc.fontRenderer.getStringWidth(row.value()) + 4
                    + row.items()
                        .size() * INLINE_ICON_SIZE
                    + Math.max(
                        0,
                        row.items()
                            .size() - 1)
                        * INLINE_ICON_GAP;
                contentWidth = Math.max(contentWidth, rowWidth);
            }
            return Math.min(contentWidth, maxContentWidth);
        }

        private int getRowHeight(Minecraft mc, PinnedInfoRow row, int contentWidth) {
            int height = TEXT_LINE_HEIGHT;
            if (row.inlineItems()) return Math.max(height, INLINE_ICON_SIZE);
            if (!row.items()
                .isEmpty()) {
                int itemsPerRow = Math.max(1, contentWidth / (ICON_SIZE + ICON_GAP));
                int itemRows = (row.items()
                    .size() + itemsPerRow
                    - 1) / itemsPerRow;
                return height + 4 + itemRows * ICON_SIZE + Math.max(0, itemRows - 1) * ICON_GAP;
            }
            List<String> wrappedLines = wrapValue(mc, row.value(), contentWidth);
            if (wrappedLines.isEmpty()) return height;
            return height + 4 + wrappedLines.size() * TEXT_LINE_HEIGHT;
        }

        private List<String> wrapValue(Minecraft mc, String value, int width) {
            if (value == null || value.isEmpty()) return Collections.EMPTY_LIST;
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

        private IDrawable createBackgroundDrawable() {
            return drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0xFF162133));
        }

        private IDrawable drawable(DrawCommand drawCommand) {
            return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
        }

        @FunctionalInterface
        private interface DrawCommand {

            void draw(GuiContext context, int x, int y, int width, int height);
        }

        private static final class PassiveLayer extends ParentWidget<PassiveLayer> {

            @Override
            public boolean canHover() {
                return false;
            }

            @Override
            public boolean canHoverThrough() {
                return true;
            }
        }
    }
}
