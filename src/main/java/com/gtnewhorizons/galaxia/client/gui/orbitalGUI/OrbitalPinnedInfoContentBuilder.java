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
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;

public final class OrbitalPinnedInfoContentBuilder {

    List<PinnedInfoRow> buildRows(CelestialObject body) {
        if (CelestialClient.isAsteroidScanInProgress(body)) {
            return CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
                .map(scan -> List.of(row("name", "???"), row("scan", formatScanProgress(scan))))
                .orElseGet(() -> List.of(row("name", "???")));
        }
        if (CelestialClient.isSensorRevealedAsteroid(body)) {
            return List.of(row("name", "???"));
        }
        List<PinnedInfoRow> rows = new ArrayList<>();
        rows.add(row("name", body.displayName()));
        rows.add(row("type", formatObjectClass(body.objectClass())));
        rows.add(row("landable", isLandable(body) ? tr("value.yes") : tr("value.no")));
        rows.add(row("dangers", buildDangerSummary(body)));
        if (body.objectClass() != CelestialObject.Class.STAR && body.objectClass() != CelestialObject.Class.GALAXY) {
            rows.add(row("surface", formatSurfaceType(body)));
            rows.add(buildOreRow(body));
            buildScanRow(body).ifPresent(rows::add);
        }
        return rows;
    }

    void buildSignatureInto(StringBuilder signature, CelestialObject body, int width, int height) {
        signature.setLength(0);
        signature.append(body.key())
            .append('|')
            .append(width)
            .append('|')
            .append(height)
            .append('|')
            .append(body.displayName())
            .append('|')
            .append(body.objectClass())
            .append('|')
            .append(
                body.properties()
                    .visitable())
            .append('|')
            .append(
                body.properties()
                    .canCreateOutpost())
            .append('|')
            .append(
                body.properties()
                    .radiation())
            .append('|')
            .append(
                body.properties()
                    .temperature());
        String surfaceType = body.properties()
            .metadata()
            .get("surface");
        signature.append('|')
            .append(surfaceType == null ? "" : surfaceType);
        if (!canShowOreDetails(body)) {
            signature.append("|asteroidOre:")
                .append(asteroidOreKnowledge(body));
        } else {
            List<String> gtOreVeinIds = body.properties()
                .gtOreVeinIds();
            signature.append('|')
                .append(gtOreVeinIds.size());
            for (String veinId : gtOreVeinIds) {
                signature.append('|')
                    .append(veinId)
                    .append(',');
            }
        }
        CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
            .ifPresent(
                scan -> signature.append("|asteroidScan:")
                    .append(scan.step())
                    .append(':')
                    .append(scan.elapsedTicks())
                    .append(':')
                    .append(scan.capability()));
    }

    private boolean canShowOreDetails(CelestialObject body) {
        if (!body.key()
            .isMinorBody()) return true;
        return CelestialClient.asteroidProjection(body)
            .map(AsteroidStarmapProjection::canShowOreDetails)
            .orElse(false);
    }

    private PinnedInfoRow buildOreRow(CelestialObject body) {
        if (body.key()
            .isMinorBody()) {
            AsteroidStarmapProjection projection = CelestialClient.asteroidProjection(body)
                .orElse(null);
            CelestialResourceKnowledgeState oreKnowledge = projection == null ? CelestialResourceKnowledgeState.UNKNOWN
                : projection.oreKnowledgeState();
            // Asteroids can have real ore data before the player knows it. The
            // sidebar gates presentation on knowledge state, not on the profile.
            if (oreKnowledge == CelestialResourceKnowledgeState.UNKNOWN) return row("ores", tr("ore.unknown"));
        }

        if (!canShowOreDetails(body)) return row("ores", tr("ore.unknown"));

        List<ItemStack> gtOres = body.properties()
            .getResolvedGtVeinOreStacks();
        if (gtOres.isEmpty()) return row("ores", tr("ore.undefined"));
        return new PinnedInfoRow(label("ores"), "", gtOres);
    }

    private java.util.Optional<PinnedInfoRow> buildScanRow(CelestialObject body) {
        if (!body.key()
            .isMinorBody()) return java.util.Optional.empty();
        // Active scan progress is keyed by the minor-body id so switching focus
        // between generated asteroids shows the satellite's current target.
        return CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
            .map(scan -> row("scan", formatScanProgress(scan)));
    }

    private String formatScanProgress(CelestialDiscoveryScanSnapshot scan) {
        int percent = scan.step()
            .durationTicks() == 0
                ? 100
                : Math.min(
                    100,
                    Math.max(
                        0,
                        (int) Math.round(
                            scan.elapsedTicks() * 100.0
                                / scan.step()
                                    .durationTicks())));
        return StatCollector.translateToLocalFormatted(
            key("scan.progress"),
            tr(
                "scan.pass." + scan.step()
                    .name()
                    .toLowerCase()),
            percent);
    }

    private CelestialResourceKnowledgeState asteroidOreKnowledge(CelestialObject body) {
        return CelestialKnowledgeClientState.resourceKnowledge(body.key())
            .orElse(CelestialResourceKnowledgeState.UNKNOWN);
    }

    private String buildDangerSummary(CelestialObject body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties()
            .radiation() >= 0.25) dangers.add(tr("danger.radiation"));
        if (body.properties()
            .temperature() > 360) dangers.add(tr("danger.heat"));
        if (body.properties()
            .temperature() > 0
            && body.properties()
                .temperature() < 120)
            dangers.add(tr("danger.cold"));
        if (!body.properties()
            .visitable() && body.properties()
                .canCreateOutpost())
            dangers.add(tr("danger.remote"));
        return dangers.isEmpty() ? tr("danger.none") : String.join(", ", dangers);
    }

    private String formatObjectClass(CelestialObject.Class objectClass) {
        return tr(
            "type." + objectClass.name()
                .toLowerCase());
    }

    private boolean isLandable(CelestialObject body) {
        return body.isLandable();
    }

    private String formatSurfaceType(CelestialObject body) {
        String surfaceType = body.properties()
            .metadata()
            .get("surface");
        if (surfaceType == null || surfaceType.isEmpty()) return tr("surface.undefined");
        return formatInfoToken(surfaceType);
    }

    private static PinnedInfoRow row(String labelKeySuffix, String value) {
        return new PinnedInfoRow(label(labelKeySuffix), value);
    }

    private static String label(String suffix) {
        return tr("label." + suffix);
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }

    private static String key(String suffix) {
        return "galaxia.gui.orbital.pinned_info." + suffix;
    }

    private String formatInfoToken(String value) {
        String[] parts = value.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)))
                .append(part.substring(1));
        }
        return out.toString();
    }

    public static final class OrbitalPinnedInfoWidget extends ParentWidget<OrbitalPinnedInfoWidget> {

        interface Callbacks {

            CelestialObject getPinnedInfoBody();

            int getViewportWidth();

            int getViewportHeight();

            void buildSignatureInto(StringBuilder buf, CelestialObject body, int width, int height);

            List<PinnedInfoRow> buildRows(CelestialObject body);
        }

        private static final int PANEL_WIDTH = 116;
        private static final int PANEL_PADDING = 12;
        private static final int TEXT_LINE_HEIGHT = 9;
        private static final int ROW_GAP = 6;
        private static final int ICON_SIZE = 16;
        private static final int ICON_GAP = 2;
        private static final int GT_ORE_STACKS_PER_VEIN = 4;
        private static final int INLINE_ICON_SIZE = 12;
        private static final int INLINE_ICON_GAP = 1;
        private final Callbacks callbacks;
        private final StringBuilder sigBuf = new StringBuilder(256);
        private String lastSignature = "";
        private List<PinnedInfoRow> cachedRows = List.of();

        OrbitalPinnedInfoWidget(Callbacks callbacks) {
            this.callbacks = callbacks;
            setEnabled(false);
            size(0, 0);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            CelestialObject body = callbacks.getPinnedInfoBody();
            if (body == null) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                lastSignature = "";
                cachedRows = List.of();
                setEnabled(false);
                size(0, 0);
                return;
            }
            setEnabled(true);
            callbacks.buildSignatureInto(sigBuf, body, callbacks.getViewportWidth(), callbacks.getViewportHeight());
            if (!lastSignature.contentEquals(sigBuf)) {
                cachedRows = callbacks.buildRows(body);
                rebuildChildren(body, cachedRows);
                lastSignature = sigBuf.toString();
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        private void rebuildChildren(CelestialObject body, List<PinnedInfoRow> rows) {
            removeAll();
            Minecraft mc = Minecraft.getMinecraft();
            int viewportWidth = callbacks.getViewportWidth();
            int viewportHeight = callbacks.getViewportHeight();
            int contentWidth = getContentWidth(mc, rows, viewportWidth);
            int boxWidth = contentWidth + PANEL_PADDING * 2;
            // Pre-compute row heights once to avoid double wrapValue calls
            int n = rows.size();
            int[] rowHeights = new int[n];
            int boxHeight = 8;
            for (int i = 0; i < n; i++) {
                rowHeights[i] = getRowHeight(mc, rows.get(i), contentWidth);
                boxHeight += rowHeights[i] + ROW_GAP;
            }
            if (n > 0) boxHeight -= ROW_GAP;
            boxHeight += 8;
            int x = Math.max(8, viewportWidth - boxWidth - 18);
            int y = Math.max(24, (viewportHeight - boxHeight) / 2);
            pos(x, y);
            size(boxWidth, boxHeight);
            ParentWidget<?> root = new ParentWidget<>().pos(0, 0)
                .size(boxWidth, boxHeight);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(createBackgroundDrawable());
            root.child(backgroundLayer);
            root.child(WidgetOutline.create(backgroundLayer, 2, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()));
            int currentY = 8;
            for (int i = 0; i < n; i++) {
                buildRow(root, mc, rows.get(i), contentWidth, currentY);
                currentY += rowHeights[i] + ROW_GAP;
            }
            child(root);
            scheduleResize();
        }

        private void buildRow(ParentWidget<?> root, Minecraft mc, PinnedInfoRow row, int contentWidth, int y) {
            if (row.inlineItems()) {
                buildInlineRow(root, mc, row, contentWidth, y);
                return;
            }
            root.child(
                new TextWidget<>(IKey.str(row.label())).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, y));
            if (!row.items()
                .isEmpty()) {
                buildItemGrid(root, row, PANEL_PADDING, y + 12, contentWidth);
                return;
            }
            List<String> wrappedLines = wrapValue(mc, row.value(), contentWidth);
            int lineY = y + 12;
            for (String line : wrappedLines) {
                root.child(
                    new TextWidget<>(IKey.str(line)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                        .shadow(true)
                        .pos(PANEL_PADDING, lineY));
                lineY += TEXT_LINE_HEIGHT;
            }
        }

        private void buildItemGrid(ParentWidget<?> root, PinnedInfoRow row, int x, int y, int contentWidth) {
            List<ItemStack> items = row.items();
            if (items == null || items.isEmpty()) return;
            int itemsPerRow = itemGridColumns(row, contentWidth);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null) continue;
                int col = i % itemsPerRow;
                int rowIndex = i / itemsPerRow;
                int itemX = x + col * (ICON_SIZE + ICON_GAP);
                int itemY = y + rowIndex * (ICON_SIZE + ICON_GAP);
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
                new TextWidget<>(IKey.str(label)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
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
                int itemsPerRow = itemGridColumns(row, contentWidth);
                int itemRows = (row.items()
                    .size() + itemsPerRow
                    - 1) / itemsPerRow;
                return height + 4 + itemRows * ICON_SIZE + Math.max(0, itemRows - 1) * ICON_GAP;
            }
            List<String> wrappedLines = wrapValue(mc, row.value(), contentWidth);
            if (wrappedLines.isEmpty()) return height;
            return height + 4 + wrappedLines.size() * TEXT_LINE_HEIGHT;
        }

        private int itemGridColumns(PinnedInfoRow row, int contentWidth) {
            int columns = Math.max(1, contentWidth / (ICON_SIZE + ICON_GAP));
            if (label("ores").equals(row.label())) return Math.min(GT_ORE_STACKS_PER_VEIN, columns);
            return columns;
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
            final RenderItem GUI_ITEM_RENDERER = new RenderItem();
            GUI_ITEM_RENDERER.zLevel = 200f;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GUI_ITEM_RENDERER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }

        private IDrawable createBackgroundDrawable() {
            return drawable(
                (context, x, y, width, height) -> Gui
                    .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_MODAL_BG.getColor()));
        }

        private IDrawable drawable(DrawableCommand drawCommand) {
            return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
        }

    }
}
