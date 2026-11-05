package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

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
public final class SolarSystemAssetPanelWidget extends Widget<SolarSystemAssetPanelWidget> {

    public static final String WINDOW = "starmap.panel.assets";
    private static final int ROW_W = 236;
    private static final int ROW_H = 18;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int BODY_ICON_SIZE = 16;
    private static final int CAP_ICON_SIZE = 16;
    private static final int CAP_ICON_GAP = 2;
    private static final int ROW_PAD_X = 4;
    private static final int ASSET_REFRESH_INTERVAL_TICKS = 20;
    /** Left edge of the map area in the window layer, right of the sidebar */
    private static final int MAP_LEFT = 226;

    private final CelestialObject galaxyRoot;
    private final Supplier<CelestialObject> viewRootSupplier;
    private final Supplier<Boolean> openSupplier;
    private final Consumer<CelestialAsset.ID> onAssetSelect;
    private final Function<CelestialObject, List<CelestialAsset>> assetProvider;

    private final Runnable onClosedByPlayer;
    private boolean closing;

    private SystemAssetFilter currentFilter = SystemAssetFilter.ALL;
    private SystemAssetSort currentSort = SystemAssetSort.BY_BODY;
    private final List<SystemAssetRowView> visibleRows = new ArrayList<>();
    private final List<SatelliteRow> visibleSatelliteRows = new ArrayList<>();
    private String lastStructureSignature = "";
    private CelestialObject lastViewRoot;
    private SystemAssetFilter lastFilter;
    private SystemAssetSort lastSort;
    private CelestialObject lastRefreshViewRoot;
    private SystemAssetFilter lastRefreshFilter;
    private SystemAssetSort lastRefreshSort;
    private int assetRefreshTicks;

    public SolarSystemAssetPanelWidget(CelestialObject galaxyRoot, Supplier<CelestialObject> viewRootSupplier,
        Supplier<Boolean> openSupplier, Runnable onClosedByPlayer, Consumer<CelestialAsset.ID> onAssetSelect) {
        this(
            galaxyRoot,
            viewRootSupplier,
            openSupplier,
            onClosedByPlayer,
            onAssetSelect,
            viewRoot -> CelestialClient.listAssetsInSystem(viewRoot.key()));
    }

    SolarSystemAssetPanelWidget(CelestialObject galaxyRoot, Supplier<CelestialObject> viewRootSupplier,
        Supplier<Boolean> openSupplier, Runnable onClosedByPlayer, Consumer<CelestialAsset.ID> onAssetSelect,
        Function<CelestialObject, List<CelestialAsset>> assetProvider) {
        this.galaxyRoot = galaxyRoot;
        this.viewRootSupplier = viewRootSupplier;
        this.openSupplier = openSupplier;
        this.onClosedByPlayer = onClosedByPlayer;
        this.onAssetSelect = onAssetSelect;
        this.assetProvider = assetProvider;
        size(0, 0);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        CelestialObject viewRoot = viewRootSupplier.get();
        boolean shouldShow = openSupplier.get() && isSystemScope(viewRoot);
        WindowLayer layer = isValid() ? WindowLayer.of(this) : null;
        if (!shouldShow) {
            if (layer != null && layer.isOpen(WINDOW)) hidePanel(layer);
            return;
        }
        refreshRowsIfNeeded(viewRoot);
        if (layer == null) return;
        String signature = buildStructureSignature();
        boolean structureChanged = !signature.equals(lastStructureSignature) || viewRoot != lastViewRoot
            || currentFilter != lastFilter
            || currentSort != lastSort;
        if (structureChanged || !layer.isOpen(WINDOW)) {
            rebuildPanel(layer, viewRoot);
            lastStructureSignature = signature;
            lastViewRoot = viewRoot;
            lastFilter = currentFilter;
            lastSort = currentSort;
        }
    }

    private boolean isSystemScope(CelestialObject viewRoot) {
        return viewRoot != null && viewRoot.objectClass() == CelestialObject.Class.STAR;
    }

    private void hidePanel(WindowLayer layer) {
        closing = true;
        layer.close(WINDOW);
        closing = false;
        visibleRows.clear();
        visibleSatelliteRows.clear();
        lastStructureSignature = "";
        lastViewRoot = null;
        lastFilter = null;
        lastSort = null;
        lastRefreshViewRoot = null;
        lastRefreshFilter = null;
        lastRefreshSort = null;
        assetRefreshTicks = 0;
    }

    private void refreshRowsIfNeeded(CelestialObject viewRoot) {
        boolean refreshRequired = assetRefreshTicks <= 0 || viewRoot != lastRefreshViewRoot
            || currentFilter != lastRefreshFilter
            || currentSort != lastRefreshSort;
        if (!refreshRequired) {
            assetRefreshTicks--;
            return;
        }
        refreshRows(viewRoot);
        lastRefreshViewRoot = viewRoot;
        lastRefreshFilter = currentFilter;
        lastRefreshSort = currentSort;
        assetRefreshTicks = ASSET_REFRESH_INTERVAL_TICKS - 1;
    }

    private void refreshRows(CelestialObject viewRoot) {
        visibleRows.clear();
        visibleSatelliteRows.clear();
        List<CelestialAsset> assets = new ArrayList<>(assetProvider.apply(viewRoot));
        visibleRows.addAll(assetRows(assets, currentFilter, currentSort));
        visibleSatelliteRows.addAll(satelliteRows(assets));
    }

    static List<SystemAssetRowView> assetRows(List<CelestialAsset> assets, SystemAssetFilter filter,
        SystemAssetSort sort) {
        List<CelestialAsset> sorted = new ArrayList<>(assets);
        sorted.sort(sort.comparator());
        List<SystemAssetRowView> rows = new ArrayList<>();
        for (CelestialAsset asset : sorted) {
            if (filter.accepts(asset)) rows.add(new SystemAssetRowView(asset));
        }
        return rows;
    }

    static List<SatelliteRow> satelliteRows(List<CelestialAsset> assets) {
        Map<CelestialObjectKey, EnumMap<SatelliteKind, Integer>> counts = new LinkedHashMap<>();
        for (CelestialAsset asset : assets) {
            if (!(asset instanceof Satellite satellite)) continue;
            counts.computeIfAbsent(asset.celestialObjectKey, ignored -> new EnumMap<>(SatelliteKind.class))
                .merge(satellite.satelliteKind(), 1, Integer::sum);
        }
        List<SatelliteRow> rows = new ArrayList<>();
        for (Map.Entry<CelestialObjectKey, EnumMap<SatelliteKind, Integer>> body : counts.entrySet()) {
            for (Map.Entry<SatelliteKind, Integer> kind : body.getValue()
                .entrySet()) {
                rows.add(new SatelliteRow(body.getKey(), kind.getKey(), kind.getValue()));
            }
        }
        return rows;
    }

    private String buildStructureSignature() {
        StringBuilder sig = new StringBuilder((visibleRows.size() + visibleSatelliteRows.size()) * 24);
        sig.append(currentFilter.name())
            .append('|')
            .append(currentSort.name())
            .append('|')
            .append(visibleRows.size())
            .append(':')
            .append(visibleSatelliteRows.size())
            .append('|');
        for (int i = 0; i < visibleRows.size(); i++) {
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
        for (SatelliteRow row : visibleSatelliteRows) {
            sig.append(row.bodyKey())
                .append(':')
                .append(row.kind())
                .append(':')
                .append(row.count())
                .append('|');
        }
        return sig.toString();
    }

    private void rebuildPanel(WindowLayer layer, CelestialObject viewRoot) {
        String title = StatCollector
            .translateToLocalFormatted("galaxia.gui.orbital.assets.title", viewRoot.displayName());
        WindowSpec spec = WindowSpec.of(WINDOW, IKey.str(title))
            .anchor((lw, lh, w, h) -> new int[] { MAP_LEFT, 30 })
            .maxHeight(GalaxiaUi.BUTTON_H + GalaxiaUi.GAP * 2 + MAX_VISIBLE_ROWS * (ROW_H + GalaxiaUi.GAP))
            .onClose(() -> { if (!closing) onClosedByPlayer.run(); })
            .body(this::fill);
        closing = true;
        layer.close(WINDOW);
        closing = false;
        layer.open(spec);
    }

    private void fill(GalaxiaUi.Column column) {
        column.row(
            GalaxiaUi
                .button(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.assets.filter",
                            currentFilter.getDisplayName())),
                    widestLabel("galaxia.gui.orbital.assets.filter", SystemAssetFilter.values()),
                    () -> currentFilter = cycleEnum(currentFilter, SystemAssetFilter.values()))
                .name("starmap.assets.filter"),
            GalaxiaUi
                .button(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.assets.sort",
                            currentSort.getDisplayName())),
                    widestLabel("galaxia.gui.orbital.assets.sort", SystemAssetSort.values()),
                    () -> currentSort = cycleEnum(currentSort, SystemAssetSort.values()))
                .name("starmap.assets.sort"));
        if (visibleRows.isEmpty() && visibleSatelliteRows.isEmpty()) {
            column.dim(StatCollector.translateToLocal("galaxia.gui.orbital.assets.none"));
            return;
        }
        for (SystemAssetRowView row : visibleRows) column.add(buildRowWidget(row));
        if (!visibleSatelliteRows.isEmpty()) {
            column.section(StatCollector.translateToLocal("galaxia.gui.orbital.assets.satellites"));
            for (SatelliteRow row : visibleSatelliteRows) column.add(buildSatelliteRowWidget(row));
        }
    }

    private static int widestLabel(String key, Enum<?>[] values) {
        int width = 0;
        for (Enum<?> value : values) {
            String name = value instanceof SystemAssetFilter filter ? filter.getDisplayName()
                : ((SystemAssetSort) value).getDisplayName();
            width = Math.max(width, GalaxiaUi.width(StatCollector.translateToLocalFormatted(key, name)));
        }
        return width + 10;
    }

    private ButtonWidget<?> buildRowWidget(SystemAssetRowView row) {
        CelestialObject hostBody = GalaxiaCelestialAPI.findBodyByKey(galaxyRoot, row.hostBodyKey);
        List<ResourceLocation> icons = new ArrayList<>();
        icons.add(AssetPanelIcons.iconForBody(hostBody));
        if (row.hasMining) icons.add(AssetPanelIcons.CAP_MINING);
        if (row.hasProduction) icons.add(AssetPanelIcons.CAP_PRODUCTION);
        if (row.underConstruction) icons.add(AssetPanelIcons.CAP_CONSTRUCTION);
        if (row.underDeconstruction) icons.add(AssetPanelIcons.CAP_DECONSTRUCTION);
        ResourceLocation warn = AssetPanelIcons.warningIcon(row.warning);
        if (warn != null) icons.add(warn);

        // The name takes what the icons leave and is cut short only when it must be
        int nameWidth = ROW_W - GalaxiaUi.PAD * 2 - GalaxiaUi.GAP - icons.size() * (BODY_ICON_SIZE + GalaxiaUi.GAP);
        Flow content = GalaxiaUi.row()
            .padding(GalaxiaUi.PAD, 0)
            .child(
                GalaxiaUi.clipped(row.displayName, nameWidth, GalaxiaGuiStyle.TEXT)
                    .width(nameWidth));
        for (ResourceLocation icon : icons) content.child(icon(icon));
        ButtonWidget<?> button = GalaxiaUi
            .flatButton(ROW_W, ROW_H, () -> { if (onAssetSelect != null) onAssetSelect.accept(row.assetId); })
            .background(EnumTextures.GUI_WELL.getImage());
        button.child(content);
        button.tooltip(t -> {
            t.addLine(row.displayName);
            t.addLine(
                StatCollector.translateToLocalFormatted(
                    "galaxia.system_asset.tooltip.body",
                    hostBody != null ? hostBody.displayName() : "?"));
            if (row.status != Buildable.Status.OPERATIONAL) {
                t.addLine(
                    StatCollector.translateToLocal(
                        "galaxia.system_asset.status." + row.status.name()
                            .toLowerCase()));
            }
            if (row.warning.isWarning()) {
                t.addLine(
                    StatCollector.translateToLocal(
                        "galaxia.system_asset.warning." + row.warning.name()
                            .toLowerCase()));
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

    private static Widget<?> icon(ResourceLocation texture) {
        return UITexture.fullImage(texture == null ? EnumTextures.ICON_MISSING.get() : texture)
            .asWidget()
            .size(BODY_ICON_SIZE);
    }

    private Flow buildSatelliteRowWidget(SatelliteRow row) {
        CelestialObject hostBody = GalaxiaCelestialAPI.findBodyByKey(galaxyRoot, row.bodyKey());
        String bodyName = hostBody != null ? hostBody.displayName()
            : row.bodyKey()
                .toString();
        int nameWidth = ROW_W - GalaxiaUi.PAD * 2 - GalaxiaUi.GAP - BODY_ICON_SIZE;
        Flow content = GalaxiaUi.row()
            .padding(GalaxiaUi.PAD, 0)
            .child(icon(CelestialMarkerBase.CelestialAssetIcons.get(CelestialAsset.Kind.SATELLITE)))
            .child(
                GalaxiaUi.clipped(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.system_asset.satellites",
                        bodyName,
                        StatCollector.translateToLocal(
                            "galaxia.satellite.kind." + row.kind()
                                .name()
                                .toLowerCase()),
                        row.count()),
                    nameWidth,
                    GalaxiaGuiStyle.TEXT));
        content.coverChildrenWidth()
            .width(ROW_W)
            .height(ROW_H)
            .background(EnumTextures.GUI_WELL.getImage());
        return content;
    }

    /**
     * Draws every visible row element in one overlay pass so the row owns no extra child widgets
     * (avoids hover-blocking surfaces wider than the thing they render).
     * Order left-to-right per design brief: name -> body icon -> capability icons.
     */
    private static <E extends Enum<E>> E cycleEnum(E current, E[] values) {
        return values[(current.ordinal() + 1) % values.length];
    }

    record SatelliteRow(CelestialObjectKey bodyKey, SatelliteKind kind, int count) {}
}
