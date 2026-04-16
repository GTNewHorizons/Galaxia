package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

/**
 * Map overlay that shows aggregated logistics signals for the current map scope.
 *
 * <p>
 * Toggled via a small "Signals" button in the top-left of the map area.
 * Scope follows the map's {@code viewRoot}:
 * <ul>
 * <li>GALACTIC — galaxy root → all outposts</li>
 * <li>SYSTEM — star body → outposts sharing that star's system</li>
 * <li>PLANETARY — planet/moon body → outposts sharing the same planetary anchor</li>
 * </ul>
 *
 * <p>
 * Per-item rows show: item name, net balance (surplus positive / deficit negative),
 * and total units currently in transit within scope.
 * Hovering an item cell shows a per-station tooltip filtered to the current scope.
 */
public final class LogisticsSignalsWidget extends ParentWidget<LogisticsSignalsWidget> {

    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 30;
    private static final int PANEL_W = 348;
    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE_ROWS = 20;
    private static final int CONTENT_SCROLLBAR_GAP = 14;
    private static final int COL_ICON = 4;
    private static final int COL_NAME = 22;
    private static final int COL_NET = 208;
    private static final int COL_TRANSIT = 285;
    private static final int NAME_W = 180;
    private static final int NET_W = 68;
    private static final int TRANSIT_W = 52;

    private enum ViewScope {
        GALACTIC,
        SYSTEM,
        PLANETARY
    }

    private final CelestialObject galaxyRoot;
    private final Supplier<CelestialObject> viewRootSupplier;
    private final Supplier<Boolean> openSupplier;
    private final ParentWidget<?> panelRoot;
    private ScrollWidget<?> scrollWidget;
    private VerticalScrollData scrollData;

    private int lastDataRevision = Integer.MIN_VALUE;
    private String lastStructureSignature = "";
    private ParentWidget<?> rowsContainer;
    private String cachedTitle = "";
    private final Map<String, SignalRowState> rowStates = new LinkedHashMap<>();

    LogisticsSignalsWidget(CelestialObject galaxyRoot, Supplier<CelestialObject> viewRootSupplier,
        Supplier<Boolean> openSupplier) {
        this.galaxyRoot = galaxyRoot;
        this.viewRootSupplier = viewRootSupplier;
        this.openSupplier = openSupplier;
        this.panelRoot = new ParentWidget<>().pos(0, 0)
            .size(PANEL_W, 0);
        this.panelRoot.setEnabled(false);
        size(0, 0);
        child(panelRoot);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!openSupplier.get()) {
            if (panelRoot.isEnabled()) {
                panelRoot.setEnabled(false);
                panelRoot.removeAll();
                rowsContainer = null;
                rowStates.clear();
                lastDataRevision = Integer.MIN_VALUE;
                lastStructureSignature = "";
                size(0, 0);
                panelRoot.scheduleResize();
                scheduleResize();
            }
            return;
        }

        CelestialObject viewRoot = currentViewRoot();
        int rev = currentDataRevision(viewRoot);
        if (rev == lastDataRevision) return;
        lastDataRevision = rev;

        ViewScope scope = scopeFor(viewRoot);
        List<SignalRow> rows = aggregateSignals(scope, viewRoot);
        updateRowStates(rows, scope, viewRoot);

        String structureSignature = buildStructureSignature(rows);
        if (!structureSignature.equals(lastStructureSignature) || rowsContainer == null) {
            rebuildPanel(scope, viewRoot, rows);
            lastStructureSignature = structureSignature;
        }
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    boolean isPointInPanel(int localX, int localY) {
        if (!panelRoot.isEnabled()) return false;
        return localX >= PANEL_X && localX <= PANEL_X + getArea().width
            && localY >= PANEL_Y
            && localY <= PANEL_Y + getArea().height;
    }

    private CelestialObject currentViewRoot() {
        CelestialObject viewRoot = viewRootSupplier.get();
        return viewRoot == null ? galaxyRoot : viewRoot;
    }

    private int currentDataRevision(CelestialObject viewRoot) {
        // TODO Colors
        int r = 0x1A2B3C4D;
        r = r * 31 + CelestialClient.clientSignalRevision();
        r = r * 31 + CelestialClient.clientDeliveryRevision();
        r = r * 31 + System.identityHashCode(viewRoot);
        return r == 0 ? Integer.MAX_VALUE : r;
    }

    private String buildStructureSignature(List<SignalRow> rows) {
        StringBuilder sig = new StringBuilder(rows.size() * 24);
        sig.append(rows.size())
            .append('|');
        int rowsToShow = Math.min(MAX_VISIBLE_ROWS, rows.size());
        for (int i = 0; i < rowsToShow; i++) {
            SignalRow row = rows.get(i);
            sig.append(
                row.item()
                    .toKey())
                .append(':')
                .append(row.net() >= 0 ? '+' : '-')
                .append('|');
        }
        return sig.toString();
    }

    private void rebuildPanel(ViewScope scope, CelestialObject viewRoot, List<SignalRow> rows) {
        int rowsToShow = Math.min(MAX_VISIBLE_ROWS, rows.size());
        int panelH = 30 + 16 + rowsToShow * (ROW_H + 2) + (rows.isEmpty() ? 18 : 0) + 8;

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
                Gui.drawRect(x, y, x + w, y + 24, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
            }));
        panelRoot.child(backgroundLayer);
        panelRoot.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

        panelRoot.child(
            new TextWidget<>(IKey.dynamic(() -> cachedTitle)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(10, 7));
        panelRoot.child(
            new TextWidget<>(IKey.str("Item")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_NAME, 28));
        panelRoot.child(
            new TextWidget<>(IKey.str("Net balance")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_NET, 28));
        panelRoot.child(
            new TextWidget<>(IKey.str("In transit")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_TRANSIT, 28));

        if (rows.isEmpty()) {
            panelRoot.child(
                new TextWidget<>(IKey.str("No items tracked in this scope."))
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .pos(10, 46));
        } else {
            int viewportHeight = rowsToShow * (ROW_H + 2);
            int contentHeight = rows.size() * (ROW_H + 2);
            rowsContainer = new ParentWidget<>().widthRel(1f)
                .height(contentHeight);
            scrollData = new VerticalScrollData();
            scrollWidget = new ScrollWidget<>(scrollData).pos(4, 44)
                .size(PANEL_W - 8 - CONTENT_SCROLLBAR_GAP, viewportHeight);
            scrollWidget.child(rowsContainer);
            panelRoot.child(scrollWidget);
            for (int i = 0; i < rows.size(); i++) {
                rowsContainer.child(buildSignalRow(rows.get(i)).pos(0, i * (ROW_H + 2)));
            }
            scrollData.setScrollSize(contentHeight);
        }
        panelRoot.scheduleResize();
        scheduleResize();
    }

    private String buildScopeLabel(ViewScope scope, CelestialObject viewRoot) {
        if (scope == ViewScope.GALACTIC) return "Logistics Signals \u2014 Galaxy";
        if (scope == ViewScope.SYSTEM) return "Logistics Signals \u2014 " + viewRoot.name() + " system";
        CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
        return "Logistics Signals \u2014 " + (anchor != null ? anchor.name() : viewRoot.name());
    }

    private void updateRowStates(List<SignalRow> rows, ViewScope scope, CelestialObject viewRoot) {
        cachedTitle = buildScopeLabel(scope, viewRoot);
        Map<String, SignalRowState> nextStates = new HashMap<>();
        for (SignalRow row : rows) {
            String key = row.item()
                .toKey();
            SignalRowState state = rowStates.get(key);
            if (state == null) {
                state = new SignalRowState(row.item());
            }
            state.refresh(row, scope, viewRoot);
            nextStates.put(key, state);
        }
        rowStates.clear();
        for (SignalRow row : rows) {
            SignalRowState state = nextStates.get(
                row.item()
                    .toKey());
            if (state != null) rowStates.put(
                row.item()
                    .toKey(),
                state);
        }
    }

    private ParentWidget<?> buildSignalRow(SignalRow row) {
        SignalRowState state = rowStates.get(
            row.item()
                .toKey());
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_H)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

        if (state == null) return rowWidget;

        rowWidget.tooltip(t -> {
            for (String line : state.tooltipLines) {
                t.addLine(line);
            }
        });

        rowWidget.child(
            drawable(
                (ctx, bx, by, bw, bh) -> {
                    if (state.displayStack != null) renderItemIcon(state.displayStack, bx + 1, by + 3);
                }).asWidget()
                    .pos(COL_ICON, 0)
                    .size(16, ROW_H));

        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> state.trimmedName)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .pos(COL_NAME, 6));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> state.netText)).color(state.netColor)
                .pos(COL_NET, 6));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> state.transitText)).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_TRANSIT, 6));

        return rowWidget;
    }

    private ViewScope scopeFor(CelestialObject viewRoot) {
        if (viewRoot == null || viewRoot == galaxyRoot || viewRoot.objectClass() == CelestialObject.Class.GALAXY)
            return ViewScope.GALACTIC;
        if (viewRoot.objectClass() == CelestialObject.Class.STAR) return ViewScope.SYSTEM;
        return ViewScope.PLANETARY;
    }

    private boolean isOutpostInScope(AutomatedOutpost outpost, ViewScope scope, CelestialObject viewRoot) {
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM:
                return viewRoot.id()
                    .equals(outpost.systemId);
            case PLANETARY: {
                CelestialObject viewAnchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
                String viewAnchorId = viewAnchor != null ? viewAnchor.id()
                    .getId()
                    : viewRoot.id()
                        .getId();
                return outpost.planetaryAnchorBodyId != null && outpost.planetaryAnchorBodyId.equals(viewAnchorId);
            }
            default:
                return false;
        }
    }

    private boolean isBodyIdInScope(CelestialObjectId bodyId, ViewScope scope, CelestialObject viewRoot) {
        if (bodyId == null) return false;
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM: {
                CelestialObject body = GalaxiaCelestialAPI.findBodyById(galaxyRoot, bodyId);
                if (body == null) return false;
                CelestialObject star = GalaxiaCelestialAPI.findStar(galaxyRoot, body);
                return star != null && star == viewRoot;
            }
            case PLANETARY: {
                CelestialObject body = GalaxiaCelestialAPI.findBodyById(galaxyRoot, bodyId);
                if (body == null) return false;
                CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, body);
                CelestialObject viewAnchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
                return anchor != null && anchor == viewAnchor;
            }
            default:
                return false;
        }
    }

    private List<SignalRow> aggregateSignals(ViewScope scope, CelestialObject viewRoot) {
        Map<String, Long> signalData;
        switch (scope) {
            case SYSTEM:
                signalData = CelestialClient.clientSignalsForSystem(viewRoot.id());
                break;
            case PLANETARY: {
                CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
                CelestialObjectId anchorId = anchor != null ? anchor.id() : viewRoot.id();
                signalData = CelestialClient.clientSignalsForPlanet(anchorId);
                break;
            }
            default: // GALACTIC — placeholder, not yet implemented
                signalData = Collections.emptyMap();
                break;
        }

        Map<ItemStackWrapper, long[]> acc = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : signalData.entrySet()) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(e.getKey());
            if (item == null) continue;
            acc.put(item, new long[] { e.getValue(), 0L });
        }

        for (LogisticsDelivery delivery : CelestialClient.clientDeliveries()) {
            boolean fromInScope = isBodyIdInScope(delivery.data.fromBodyId(), scope, viewRoot);
            boolean toInScope = isBodyIdInScope(delivery.data.toBodyId(), scope, viewRoot);
            if (!fromInScope && !toInScope) continue;
            acc.computeIfAbsent(delivery.data.resourceId(), k -> new long[] { 0L, 0L })[1] += delivery.data.amount();
        }

        List<SignalRow> rows = new ArrayList<>(acc.size());
        for (Map.Entry<ItemStackWrapper, long[]> e : acc.entrySet()) {
            rows.add(new SignalRow(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        rows.sort(
            Comparator.comparingLong((SignalRow r) -> r.net())
                .reversed());
        return rows;
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        com.cleanroommc.modularui.utils.GlStateManager.scale(1f, 1f, 1f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        net.minecraft.client.renderer.entity.RenderItem ri = net.minecraft.client.renderer.entity.RenderItem
            .getInstance();
        float prevZ = ri.zLevel;
        ri.zLevel = 200f;
        net.minecraft.client.renderer.OpenGlHelper
            .setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        ri.zLevel = prevZ;
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    private static String formatAmount(long v) {
        long abs = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (abs < 1_000) return String.valueOf(v);
        if (abs < 1_000_000) return sign + (abs / 1_000) + "k";
        return sign + (abs / 1_000_000) + "M";
    }

    private static String trimToPixels(String s, int maxPx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || s == null) return s;
        return mc.fontRenderer.trimStringToWidth(s, maxPx);
    }

    private final class SignalRowState {

        private final ItemStackWrapper item;
        private ItemStack displayStack;
        private String trimmedName = "";
        private String netText = "0";
        private String transitText = "\u2014";
        private int netColor = EnumColors.MAP_COLOR_SIGNAL_POSITIVE.getColor();
        private final List<String> tooltipLines = new ArrayList<>();

        private SignalRowState(ItemStackWrapper item) {
            this.item = item;
        }

        private void refresh(SignalRow row, ViewScope scope, CelestialObject viewRoot) {
            this.displayStack = item.toStack(1);
            String fullName = displayStack != null ? displayStack.getDisplayName() : item.toKey();
            this.trimmedName = trimToPixels(fullName, NAME_W - 6);
            this.netText = (row.net() >= 0 ? "+" : "") + formatAmount(row.net());
            this.transitText = row.inTransit() > 0 ? formatAmount(row.inTransit()) : "\u2014";
            this.netColor = row.net() >= 0 ? EnumColors.MAP_COLOR_SIGNAL_POSITIVE.getColor()
                : EnumColors.MAP_COLOR_SIGNAL_NEGATIVE.getColor();

            tooltipLines.clear();
            tooltipLines.add(fullName);
            for (AutomatedOutpost outpost : CelestialClient.allOutposts()) {
                if (!isOutpostInScope(outpost, scope, viewRoot)) continue;
                CelestialAsset asset = CelestialAssetStore.findAsset(outpost.assetId);
                if (asset == null) continue;
                long stock = outpost.inventory.getAmount(item);
                LogisticsResourceConfig cfg = outpost.logisticsConfig.get(item);
                if (stock == 0 && cfg.minReserve() == 0 && !cfg.isImportEnabled() && !cfg.isSupplyEnabled()) continue;
                long localNet = stock - cfg.minReserve();
                String flags = (cfg.isImportEnabled() ? "I" : "-") + (cfg.isSupplyEnabled() ? "E" : "-");
                tooltipLines.add(
                    asset.displayName() + " ["
                        + flags
                        + "] "
                        + stock
                        + "/"
                        + cfg.minReserve()
                        + " net:"
                        + (localNet >= 0 ? "+" : "")
                        + localNet);
            }
        }
    }

    private record SignalRow(ItemStackWrapper item, long net, long inTransit) {}
}
