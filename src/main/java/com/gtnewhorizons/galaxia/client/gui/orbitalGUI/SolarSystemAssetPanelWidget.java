package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

/**
 * Solar-system asset list overlay (Feature A, T1.4).
 *
 * <p>
 * Lists every team-owned {@link CelestialAsset} whose host body sits in the system rooted at the
 * map's current view scope (a STAR). Hidden when the view scope is the galaxy root or a
 * planet/moon — the panel is system-scoped by design (spec §2.2).
 *
 * <p>
 * Pure renderer. All filtering, sorting, capability and warning data come from
 * {@link GalaxiaCelestialAPI}, {@link SystemAssetFilter}, {@link SystemAssetSort}, and
 * {@link SystemAssetRowView} — no domain logic lives here (architecture §C2/§C10).
 *
 * <p>
 * Row click hands the host {@link CelestialObject} back through the constructor-supplied
 * callback; this widget does not open management screens itself.
 */
public final class SolarSystemAssetPanelWidget extends ParentWidget<SolarSystemAssetPanelWidget> {

    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 30;
    private static final int PANEL_W = 348;
    private static final int HEADER_H = 24;
    private static final int CONTROLS_H = 22;
    private static final int ROW_H = 20;
    private static final int MAX_VISIBLE_ROWS = 16;
    private static final int CONTENT_SCROLLBAR_GAP = 14;
    private static final int FILTER_BTN_W = 96;
    private static final int SORT_BTN_W = 96;
    private static final int CTRL_GAP = 6;
    private static final int BODY_ICON_SIZE = 16;
    private static final int CAP_ICON_SIZE = 16;
    private static final int CAP_ICON_GAP = 2;
    private static final int ROW_PAD_X = 4;
    private static final int NAME_W = 132;
    private static final int ROW_GAP = 2;
    private static final int PANEL_PAD_X = 10;
    private static final int SCROLL_PAD_X = 4;
    private static final int PANEL_BOTTOM_PAD = 8;
    private static final int EMPTY_VIEWPORT_H = 18;
    private static final int OUTLINE_THICKNESS = 3;
    private static final int TITLE_Y = 7;
    private static final int CONTROLS_TOP_GAP = 3;
    private static final int CTRL_BTN_H = CONTROLS_H - 6;
    private static final int LIST_TOP_GAP = 2;
    private static final int EMPTY_TEXT_NUDGE_Y = 4;

    private final CelestialObject galaxyRoot;
    private final Supplier<CelestialObject> viewRootSupplier;
    private final Supplier<Boolean> openSupplier;
    private final Consumer<CelestialObject> onAssetSelect;

    private final ParentWidget<?> panelRoot;
    private ParentWidget<?> rowsContainer;
    private ScrollWidget<?> scrollWidget;
    private VerticalScrollData scrollData;

    private SystemAssetFilter currentFilter = SystemAssetFilter.ALL;
    private SystemAssetSort currentSort = SystemAssetSort.BY_BODY;
    private final List<SystemAssetRowView> visibleRows = new ArrayList<>();
    private String lastStructureSignature = "";
    private CelestialObject lastViewRoot;
    private SystemAssetFilter lastFilter;
    private SystemAssetSort lastSort;

    public SolarSystemAssetPanelWidget(CelestialObject galaxyRoot, Supplier<CelestialObject> viewRootSupplier,
        Supplier<Boolean> openSupplier, Consumer<CelestialObject> onAssetSelect) {
        this.galaxyRoot = galaxyRoot;
        this.viewRootSupplier = viewRootSupplier;
        this.openSupplier = openSupplier;
        this.onAssetSelect = onAssetSelect;
        this.panelRoot = new ParentWidget<>().pos(0, 0)
            .size(PANEL_W, 0);
        this.panelRoot.setEnabled(false);
        size(0, 0);
        child(panelRoot);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        CelestialObject viewRoot = viewRootSupplier.get();
        boolean shouldShow = openSupplier.get() && isSystemScope(viewRoot);
        if (!shouldShow) {
            if (panelRoot.isEnabled()) hidePanel();
            return;
        }
        refreshRows(viewRoot);
        String signature = buildStructureSignature();
        boolean structureChanged = !signature.equals(lastStructureSignature) || viewRoot != lastViewRoot
            || currentFilter != lastFilter
            || currentSort != lastSort
            || rowsContainer == null;
        if (structureChanged) {
            rebuildPanel(viewRoot);
            lastStructureSignature = signature;
            lastViewRoot = viewRoot;
            lastFilter = currentFilter;
            lastSort = currentSort;
        }
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    private boolean isSystemScope(CelestialObject viewRoot) {
        return viewRoot != null && viewRoot.objectClass() == CelestialObject.Class.STAR;
    }

    private void hidePanel() {
        panelRoot.setEnabled(false);
        panelRoot.removeAll();
        rowsContainer = null;
        scrollWidget = null;
        scrollData = null;
        visibleRows.clear();
        lastStructureSignature = "";
        lastViewRoot = null;
        lastFilter = null;
        lastSort = null;
        size(0, 0);
        panelRoot.scheduleResize();
        scheduleResize();
    }

    private void refreshRows(CelestialObject viewRoot) {
        visibleRows.clear();
        List<CelestialAsset> assets = GalaxiaCelestialAPI.listAssetsInSystem(viewRoot.id(), TempTeamCompat.getTeam());
        assets.sort(currentSort.comparator());
        for (CelestialAsset asset : assets) {
            if (currentFilter.accepts(asset)) visibleRows.add(new SystemAssetRowView(asset));
        }
    }

    private String buildStructureSignature() {
        StringBuilder sig = new StringBuilder(visibleRows.size() * 24);
        sig.append(currentFilter.name())
            .append('|')
            .append(currentSort.name())
            .append('|')
            .append(visibleRows.size())
            .append('|');
        int rowsToShow = Math.min(MAX_VISIBLE_ROWS, visibleRows.size());
        for (int i = 0; i < rowsToShow; i++) {
            SystemAssetRowView row = visibleRows.get(i);
            sig.append(row.assetId.id())
                .append(':')
                .append(row.status.ordinal())
                .append(':')
                .append(row.warning.ordinal())
                .append(':')
                .append(row.hasMining ? '1' : '0')
                .append(row.hasProduction ? '1' : '0')
                .append(row.underConstruction ? 'C' : '-')
                .append(row.underDeconstruction ? 'D' : '-')
                .append(':')
                .append(row.displayName)
                .append('|');
        }
        return sig.toString();
    }

    private void rebuildPanel(CelestialObject viewRoot) {
        String title = "Assets \u2014 " + viewRoot.displayName() + " system";
        int rowsToShow = Math.min(MAX_VISIBLE_ROWS, Math.max(1, visibleRows.size()));
        int viewportH = visibleRows.isEmpty() ? EMPTY_VIEWPORT_H : rowsToShow * (ROW_H + ROW_GAP);
        int panelH = HEADER_H + CONTROLS_H + viewportH + PANEL_BOTTOM_PAD;

        panelRoot.removeAll();
        scrollWidget = null;
        scrollData = null;
        pos(PANEL_X, PANEL_Y);
        size(PANEL_W, panelH);
        panelRoot.size(PANEL_W, panelH);
        panelRoot.setEnabled(true);

        ParentWidget<?> backgroundLayer = new ParentWidget<>().pos(0, 0)
            .size(PANEL_W, panelH)
            .background(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor());
                Gui.drawRect(x, y, x + w, y + HEADER_H, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
            }));
        panelRoot.child(backgroundLayer);
        panelRoot.child(
            WidgetOutline.create(backgroundLayer, OUTLINE_THICKNESS, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

        panelRoot.child(
            new TextWidget<>(IKey.str(title)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(PANEL_PAD_X, TITLE_Y));

        int controlsY = HEADER_H + CONTROLS_TOP_GAP;
        panelRoot.child(
            cycleButton(
                () -> "Filter: " + currentFilter.getDisplayName(),
                () -> currentFilter = cycleEnum(currentFilter, SystemAssetFilter.values())).pos(PANEL_PAD_X, controlsY)
                    .size(FILTER_BTN_W, CTRL_BTN_H));
        panelRoot.child(
            cycleButton(
                () -> "Sort: " + currentSort.getDisplayName(),
                () -> currentSort = cycleEnum(currentSort, SystemAssetSort.values()))
                    .pos(PANEL_PAD_X + FILTER_BTN_W + CTRL_GAP, controlsY)
                    .size(SORT_BTN_W, CTRL_BTN_H));

        int listY = HEADER_H + CONTROLS_H + LIST_TOP_GAP;
        if (visibleRows.isEmpty()) {
            panelRoot.child(
                new TextWidget<>(IKey.str("No assets in this system."))
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .pos(PANEL_PAD_X, listY + EMPTY_TEXT_NUDGE_Y));
            panelRoot.scheduleResize();
            scheduleResize();
            return;
        }

        int contentHeight = visibleRows.size() * (ROW_H + ROW_GAP);
        rowsContainer = new ParentWidget<>().widthRel(1f)
            .height(contentHeight);
        scrollData = new VerticalScrollData();
        scrollWidget = new ScrollWidget<>(scrollData).pos(SCROLL_PAD_X, listY)
            .size(PANEL_W - 2 * SCROLL_PAD_X - CONTENT_SCROLLBAR_GAP, viewportH);
        scrollWidget.child(rowsContainer);
        panelRoot.child(scrollWidget);
        for (int i = 0; i < visibleRows.size(); i++) {
            rowsContainer.child(buildRowWidget(visibleRows.get(i)).pos(0, i * (ROW_H + ROW_GAP)));
        }
        scrollData.setScrollSize(contentHeight);
        panelRoot.scheduleResize();
        scheduleResize();
    }

    private ButtonWidget<?> buildRowWidget(SystemAssetRowView row) {
        CelestialObject hostBody = GalaxiaCelestialAPI.findBodyById(galaxyRoot, row.hostBodyId);
        String displayName = trimToPixels(row.displayName, NAME_W);
        ResourceLocation bodyIcon = AssetPanelIcons.iconForBody(hostBody);

        ButtonWidget<?> button = new ButtonWidget<>().widthRel(1f)
            .height(ROW_H)
            .background(
                drawable((ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())))
            .hoverBackground(
                drawable(
                    (ctx, x, y, w, h) -> Gui
                        .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor())))
            .overlay(drawable((ctx, x, y, w, h) -> drawRowContent(row, displayName, bodyIcon, x, y, h)))
            .onMousePressed(btn -> {
                if (btn != 0 || onAssetSelect == null || hostBody == null) return false;
                onAssetSelect.accept(hostBody);
                return true;
            });
        button.tooltip(t -> {
            t.addLine(row.displayName);
            t.addLine(
                StatCollector.translateToLocalFormatted(
                    "galaxia.system_asset.tooltip.body",
                    hostBody != null ? hostBody.displayName() : "?"));
            t.addLine(
                StatCollector.translateToLocalFormatted("galaxia.system_asset.tooltip.status", row.status.name()));
            if (row.warning.isWarning()) {
                t.addLine(
                    StatCollector
                        .translateToLocalFormatted("galaxia.system_asset.tooltip.warning", row.warning.name()));
            }
            if (row.underConstruction) {
                t.addLine(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.system_asset.tooltip.construction",
                        Math.round(row.constructionProgress * 100f)));
            }
        });
        return button;
    }

    /**
     * Draws every visible row element in one overlay pass so the row owns no extra child widgets
     * (avoids hover-blocking surfaces wider than the thing they render).
     * Order left-to-right per design brief: name -> body icon -> capability icons.
     */
    private static void drawRowContent(SystemAssetRowView row, String displayName, ResourceLocation bodyIcon, int x,
        int y, int h) {
        net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int textY = y + (h - fr.FONT_HEIGHT) / 2 + 1;
        int cursor = x + ROW_PAD_X;

        fr.drawStringWithShadow(displayName, cursor, textY, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
        cursor += NAME_W + ROW_PAD_X;

        int iconY = y + (h - BODY_ICON_SIZE) / 2;
        AssetPanelIcons.drawSprite(bodyIcon, cursor, iconY, BODY_ICON_SIZE);
        cursor += BODY_ICON_SIZE + ROW_PAD_X;

        int capY = y + (h - CAP_ICON_SIZE) / 2;
        if (row.hasMining) {
            AssetPanelIcons.drawSprite(AssetPanelIcons.CAP_MINING, cursor, capY, CAP_ICON_SIZE);
            cursor += CAP_ICON_SIZE + CAP_ICON_GAP;
        }
        if (row.hasProduction) {
            AssetPanelIcons.drawSprite(AssetPanelIcons.CAP_PRODUCTION, cursor, capY, CAP_ICON_SIZE);
            cursor += CAP_ICON_SIZE + CAP_ICON_GAP;
        }
        if (row.underConstruction) {
            AssetPanelIcons.drawSprite(AssetPanelIcons.CAP_CONSTRUCTION, cursor, capY, CAP_ICON_SIZE);
            cursor += CAP_ICON_SIZE + CAP_ICON_GAP;
        }
        if (row.underDeconstruction) {
            AssetPanelIcons.drawSprite(AssetPanelIcons.CAP_DECONSTRUCTION, cursor, capY, CAP_ICON_SIZE);
            cursor += CAP_ICON_SIZE + CAP_ICON_GAP;
        }
        ResourceLocation warn = AssetPanelIcons.warningIcon(row.warning);
        if (warn != null) {
            AssetPanelIcons.drawSprite(warn, cursor, capY, CAP_ICON_SIZE);
        }
    }

    private ButtonWidget<?> cycleButton(Supplier<String> labelSupplier, Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .hoverBackground(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .overlay(drawable((ctx, x, y, w, h) -> {
                String label = labelSupplier.get();
                net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String trimmed = fr.trimStringToWidth(label, w - 6);
                int textW = fr.getStringWidth(trimmed);
                fr.drawStringWithShadow(
                    trimmed,
                    x + (w - textW) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            }))
            .onMousePressed(btn -> {
                if (btn != 0) return false;
                onClick.run();
                return true;
            });
    }

    private static <E extends Enum<E>> E cycleEnum(E current, E[] values) {
        return values[(current.ordinal() + 1) % values.length];
    }

    private static String trimToPixels(String s, int maxPx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || s == null) return s;
        return mc.fontRenderer.trimStringToWidth(s, maxPx);
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
