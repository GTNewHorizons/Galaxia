package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
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
public final class LogisticsSignalsWidget extends Widget<LogisticsSignalsWidget> {

    public static final String WINDOW = "starmap.panel.signals";
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int NAME_W = 120;
    private static final int NUMBER_W = 50;
    /** Where the window opens, beside the assets window */
    private static final int ANCHOR_X = 490;

    private enum ViewScope {
        GALACTIC,
        SYSTEM,
        PLANETARY
    }

    private final CelestialObject galaxyRoot;
    private final Supplier<CelestialObject> viewRootSupplier;
    private final Supplier<Boolean> openSupplier;
    private final Runnable onClosedByPlayer;
    private boolean closing;

    private int lastDataRevision = Integer.MIN_VALUE;
    private @Nullable SignalStructure lastStructure;
    private String cachedTitle = "";
    private final Map<ItemStackWrapper, SignalRowState> rowStates = new LinkedHashMap<>();

    LogisticsSignalsWidget(CelestialObject galaxyRoot, Supplier<CelestialObject> viewRootSupplier,
        Supplier<Boolean> openSupplier, Runnable onClosedByPlayer) {
        this.galaxyRoot = galaxyRoot;
        this.viewRootSupplier = viewRootSupplier;
        this.openSupplier = openSupplier;
        this.onClosedByPlayer = onClosedByPlayer;
        size(0, 0);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        WindowLayer layer = isValid() ? WindowLayer.of(this) : null;
        if (layer == null) return;
        if (!openSupplier.get()) {
            if (layer.isOpen(WINDOW)) {
                closeWindow(layer);
                rowStates.clear();
                lastDataRevision = Integer.MIN_VALUE;
                lastStructure = null;
            }
            return;
        }

        CelestialObject viewRoot = currentViewRoot();
        int rev = currentDataRevision(viewRoot);
        if (rev == lastDataRevision && layer.isOpen(WINDOW)) return;
        lastDataRevision = rev;

        ViewScope scope = scopeFor(viewRoot);
        List<SignalRow> rows = aggregateSignals(scope, viewRoot);
        updateRowStates(rows, scope, viewRoot);

        SignalStructure structure = buildStructure(rows);
        if (!structure.equals(lastStructure) || !layer.isOpen(WINDOW)) {
            rebuildPanel(layer, rows);
            lastStructure = structure;
        }
    }

    private void closeWindow(WindowLayer layer) {
        closing = true;
        layer.close(WINDOW);
        closing = false;
    }

    private CelestialObject currentViewRoot() {
        CelestialObject viewRoot = viewRootSupplier.get();
        return viewRoot == null ? galaxyRoot : viewRoot;
    }

    private int currentDataRevision(CelestialObject viewRoot) {
        int r = 0x1A2B3C4D;
        r = r * 31 + CelestialClient.clientSignalRevision();
        r = r * 31 + CelestialClient.clientDeliveryRevision();
        r = r * 31 + System.identityHashCode(viewRoot);
        return r == 0 ? Integer.MAX_VALUE : r;
    }

    private SignalStructure buildStructure(List<SignalRow> rows) {
        List<SignalStructureRow> visibleRows = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            SignalRow row = rows.get(i);
            visibleRows.add(new SignalStructureRow(row.item(), row.net()));
        }
        return new SignalStructure(rows.size(), List.copyOf(visibleRows));
    }

    private void rebuildPanel(WindowLayer layer, List<SignalRow> rows) {
        WindowSpec spec = WindowSpec.of(WINDOW, IKey.str(cachedTitle))
            .anchor((lw, lh, w, h) -> new int[] { ANCHOR_X, 30 })
            .maxHeight(GalaxiaUi.ICON + GalaxiaUi.GAP + MAX_VISIBLE_ROWS * (GalaxiaUi.ICON + GalaxiaUi.GAP))
            .onClose(() -> { if (!closing) onClosedByPlayer.run(); })
            .body(column -> fill(column, rows));
        closeWindow(layer);
        layer.open(spec);
    }

    private void fill(GalaxiaUi.Column column, List<SignalRow> rows) {
        if (rows.isEmpty()) {
            column.dim(StatCollector.translateToLocal("galaxia.gui.orbital.signals.none"));
            return;
        }
        column.add(
            GalaxiaUi.row()
                .child(
                    GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.orbital.signals.item"))
                        .width(GalaxiaUi.ICON + GalaxiaUi.GAP + NAME_W))
                .child(
                    GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.orbital.signals.net"))
                        .width(NUMBER_W))
                .child(
                    GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.orbital.signals.transit"))
                        .width(NUMBER_W)));
        for (SignalRow row : rows) {
            SignalRowState state = rowStates.get(row.item());
            if (state == null) continue;
            Flow line = GalaxiaUi.row()
                .child(GalaxiaUi.item(state.displayStack))
                .child(
                    GalaxiaUi.clipped(state.fullName, NAME_W, GalaxiaGuiStyle.TEXT)
                        .width(NAME_W))
                .child(
                    GalaxiaUi.colored(() -> state.netText, state.netColor)
                        .width(NUMBER_W))
                .child(
                    GalaxiaUi.colored(() -> state.transitText, GalaxiaGuiStyle.TEXT_DIM)
                        .width(NUMBER_W));
            line.tooltip(t -> { for (String text : state.tooltipLines) t.addLine(text); });
            column.add(line);
        }
    }

    private String buildScopeLabel(ViewScope scope, CelestialObject viewRoot) {
        if (scope == ViewScope.GALACTIC)
            return StatCollector.translateToLocal("galaxia.gui.orbital.signals.title.galaxy");
        if (scope == ViewScope.SYSTEM)
            return StatCollector.translateToLocalFormatted("galaxia.gui.orbital.signals.title", viewRoot.displayName());
        CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.orbital.signals.title",
            (anchor != null ? anchor : viewRoot).displayName());
    }

    private void updateRowStates(List<SignalRow> rows, ViewScope scope, CelestialObject viewRoot) {
        cachedTitle = buildScopeLabel(scope, viewRoot);
        Map<ItemStackWrapper, SignalRowState> nextStates = new HashMap<>();
        for (SignalRow row : rows) {
            ItemStackWrapper key = row.item();
            SignalRowState state = rowStates.get(key);
            if (state == null) {
                state = new SignalRowState(row.item());
            }
            state.refresh(row, scope, viewRoot);
            nextStates.put(key, state);
        }
        rowStates.clear();
        for (SignalRow row : rows) {
            SignalRowState state = nextStates.get(row.item());
            if (state != null) rowStates.put(row.item(), state);
        }
    }

    private ViewScope scopeFor(CelestialObject viewRoot) {
        if (viewRoot == null || viewRoot == galaxyRoot || viewRoot.objectClass() == CelestialObject.Class.GALAXY)
            return ViewScope.GALACTIC;
        if (viewRoot.objectClass() == CelestialObject.Class.STAR) return ViewScope.SYSTEM;
        return ViewScope.PLANETARY;
    }

    private boolean isOutpostInScope(CelestialAsset outpost, ViewScope scope, CelestialObject viewRoot) {
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM:
                return viewRoot.key()
                    .equals(outpost.systemKey);
            case PLANETARY: {
                CelestialObject viewAnchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
                CelestialObjectKey viewAnchorKey = viewAnchor != null ? viewAnchor.key() : viewRoot.key();
                return outpost.planetaryAnchorBodyKey != null && outpost.planetaryAnchorBodyKey.equals(viewAnchorKey);
            }
            default:
                return false;
        }
    }

    private boolean isBodyIdInScope(CelestialObjectKey bodyKey, ViewScope scope, CelestialObject viewRoot) {
        if (bodyKey == null) return false;
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM: {
                CelestialObject body = GalaxiaCelestialAPI.findBodyByKey(galaxyRoot, bodyKey);
                if (body == null) return false;
                CelestialObject star = GalaxiaCelestialAPI.findStar(galaxyRoot, body);
                return star != null && star == viewRoot;
            }
            case PLANETARY: {
                CelestialObject body = GalaxiaCelestialAPI.findBodyByKey(galaxyRoot, bodyKey);
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
        Map<ItemStackWrapper, Long> signalData;
        switch (scope) {
            case SYSTEM:
                signalData = CelestialClient.clientSignalsForSystem(viewRoot.key());
                break;
            case PLANETARY: {
                CelestialObject anchor = GalaxiaCelestialAPI.findPlanetaryAnchor(galaxyRoot, viewRoot);
                CelestialObjectKey anchorKey = anchor != null ? anchor.key() : viewRoot.key();
                signalData = CelestialClient.clientSignalsForPlanet(anchorKey);
                break;
            }
            default: // GALACTIC — placeholder, not yet implemented
                signalData = Collections.emptyMap();
                break;
        }

        Map<ItemStackWrapper, long[]> acc = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : signalData.entrySet()) {
            acc.put(e.getKey(), new long[] { e.getValue(), 0L });
        }

        for (LogisticsDelivery delivery : CelestialClient.clientDeliveries()) {
            boolean fromInScope = isBodyIdInScope(delivery.data.fromBodyKey(), scope, viewRoot);
            boolean toInScope = isBodyIdInScope(delivery.data.toBodyKey(), scope, viewRoot);
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

    private static String formatAmount(long v) {
        long abs = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (abs < 1_000) return String.valueOf(v);
        if (abs < 1_000_000) return sign + (abs / 1_000) + "k";
        return sign + (abs / 1_000_000) + "M";
    }

    private final class SignalRowState {

        private final ItemStackWrapper item;
        private ItemStack displayStack;
        private String fullName = "";
        private String netText = "0";
        private String transitText = "\u2014";
        private int netColor = GalaxiaGuiStyle.GOOD;
        private final List<String> tooltipLines = new ArrayList<>();

        private SignalRowState(ItemStackWrapper item) {
            this.item = item;
        }

        private void refresh(SignalRow row, ViewScope scope, CelestialObject viewRoot) {
            this.displayStack = item.toStack(1);
            this.fullName = displayStack.getDisplayName();
            this.netText = (row.net() >= 0 ? "+" : "") + formatAmount(row.net());
            this.transitText = row.inTransit() > 0 ? formatAmount(row.inTransit()) : "\u2014";
            this.netColor = row.net() >= 0 ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.BAD;

            tooltipLines.clear();
            tooltipLines.add(fullName);
            for (CelestialAsset outpost : CelestialAssetStore.CLIENT.assetsViewInternal()) {
                if (!isOutpostInScope(outpost, scope, viewRoot)) continue;
                if (outpost == null) continue;
                long stock = outpost instanceof Station station ? station.getCannonChestItems()
                    .getOrDefault(item, 0L)
                    : outpost instanceof AutomatedFacility facility ? facility.itemAmount(item) : 0L;
                LogisticsResourceConfig cfg = outpost.logisticsConfig.get(item);
                if (stock == 0 && cfg.minReserve() == 0 && !cfg.isImportEnabled() && !cfg.isSupplyEnabled()) continue;
                long localNet = stock - cfg.minReserve();
                String flags = (cfg.isImportEnabled() ? "I" : "-") + (cfg.isSupplyEnabled() ? "E" : "-");
                tooltipLines.add(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.gui.orbital.signals.station",
                        outpost.displayName(),
                        flags,
                        stock,
                        cfg.minReserve(),
                        (localNet >= 0 ? "+" : "") + localNet));
            }
        }
    }

    private record SignalRow(ItemStackWrapper item, long net, long inTransit) {}

    private record SignalStructure(int totalRows, List<SignalStructureRow> visibleRows) {}

    private record SignalStructureRow(ItemStackWrapper item, long net) {}
}
