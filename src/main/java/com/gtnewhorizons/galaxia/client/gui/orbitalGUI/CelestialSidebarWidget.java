package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.TeamPermissionScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.SafePhantomItemSlot;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaScrollbar;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

record VisibleEntry(CelestialObject body, int depth, boolean hasChildren) {}

/**
 * Translucent column on the left of the map: the galaxy and system layers, the debug tools, a search field and the tree
 * of bodies in the shown system
 */
public class CelestialSidebarWidget extends ParentWidget<CelestialSidebarWidget> {

    public static final String SUPPLY_DEBUG_WINDOW = "starmap.supplyDebug";
    private static final int ROW_H = 16;
    private static final int INDENT = 10;
    private static final int ARROW = 8;

    private final CelestialObject root;
    private final OrbitalView.OrbitalMapWidget map;
    private CelestialObject currentSystem;
    private CelestialObject activeLayer;
    private String searchQuery = "";
    private final Set<CelestialObject> expanded = new HashSet<>();
    private boolean entriesDirty = true;
    private TextFieldWidget searchField;
    private ListWidget<IWidget, ?> bodyList;

    // Supply Debug window state
    private CelestialAsset.ID supplyDebugTargetAssetId;
    private final ItemStackHandler supplyDebugGhostHandler = new ItemStackHandler(1);
    private TextFieldWidget supplyDebugAmountField;
    private long lastSupplyDebugClickMs;

    public CelestialSidebarWidget(CelestialObject root, CelestialObject currentSystem,
        OrbitalView.OrbitalMapWidget map) {
        this.root = root;
        this.map = map;
        this.currentSystem = currentSystem;
        this.activeLayer = currentSystem == null ? root : currentSystem;
        expanded.add(root);
        if (this.currentSystem != null) expanded.add(this.currentSystem);
        background(EnumTextures.GUI_PANEL_TRANSLUCENT.getImage());
    }

    @Override
    public void onInit() {
        super.onInit();
        int inner = getArea().width > 0 ? getArea().width : 216;
        Flow column = Flow.column()
            .widthRel(1f)
            .heightRel(1f)
            .padding(GalaxiaUi.PAD * 2)
            .childPadding(GalaxiaUi.GAP * 2)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .collapseDisabledChild();

        column.child(
            GalaxiaUi.row()
                .child(
                    GalaxiaUi
                        .toggle(
                            () -> StatCollector.translateToLocal("galaxia.gui.orbital.layer.galaxy"),
                            labelWidth("galaxia.gui.orbital.layer.galaxy"),
                            () -> activeLayer == root,
                            () -> selectLayer(root))
                        .name("starmap.sidebar.layer.galaxy"))
                .child(
                    GalaxiaUi
                        .toggle(
                            this::currentSystemLabel,
                            80,
                            () -> activeLayer == currentSystem && currentSystem != null,
                            () -> { if (currentSystem != null) selectLayer(currentSystem); })
                        .name("starmap.sidebar.layer.system")));

        Flow tools = GalaxiaUi.row()
            .child(
                GalaxiaUi.toggle(
                    () -> StatCollector.translateToLocal("galaxia.gui.orbital.debug"),
                    labelWidth("galaxia.gui.orbital.debug"),
                    map::isCreativeBuildModeEnabled,
                    () -> {
                        map.toggleCreativeBuildMode();
                        markEntriesDirty();
                    })
                    .name("starmap.sidebar.debug")
                    .setEnabledIf(w -> map.isCreativeModeAvailable()))
            .child(
                GalaxiaUi.toggle(
                    () -> StatCollector.translateToLocal("galaxia.gui.orbital.hidden"),
                    labelWidth("galaxia.gui.orbital.hidden"),
                    map::areHiddenObjectsShown,
                    () -> {
                        map.toggleHiddenObjectsShown();
                        markEntriesDirty();
                    })
                    .name("starmap.sidebar.hidden")
                    .setEnabledIf(w -> map.isCreativeBuildModeEnabled()))
            .child(
                GalaxiaUi
                    .toggle(
                        () -> StatCollector.translateToLocal("galaxia.gui.orbital.simulator"),
                        labelWidth("galaxia.gui.orbital.simulator"),
                        map::isTransferSimulatorOpen,
                        map::toggleTransferSimulator)
                    .name("starmap.sidebar.simulator")
                    .setEnabledIf(w -> map.isCreativeBuildModeEnabled()))
            .child(
                GalaxiaUi
                    .toggle(
                        () -> StatCollector.translateToLocal("galaxia.gui.orbital.supply_debug"),
                        labelWidth("galaxia.gui.orbital.supply_debug"),
                        this::isSupplyDebugOpen,
                        this::toggleSupplyDebug)
                    .name("starmap.sidebar.supply")
                    .setEnabledIf(w -> map.isCreativeBuildModeEnabled()));
        column.child(tools.setEnabledIf(w -> map.isCreativeModeAvailable()));

        searchField = GalaxiaUi
            .textField(
                StatCollector.translateToLocal("galaxia.gui.orbital.search.placeholder"),
                inner - GalaxiaUi.PAD * 4)
            .name("starmap.sidebar.search")
            .setMaxLength(64)
            .setFocusOnGuiOpen(false);
        searchField.setEnabledIf(w -> activeLayer != root);
        column.child(searchField);

        bodyList = new ListWidget<>();
        bodyList.scrollDirection(new GalaxiaScrollbar())
            .widthRel(1f)
            .expanded()
            .crossAxisAlignment(Alignment.CrossAxis.START);
        column.child(bodyList);

        column.child(
            GalaxiaUi
                .button(
                    IKey.lang("galaxia.gui.team_config.button"),
                    GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.team_config.button")) + 10,
                    TeamPermissionScreen::open)
                .name("starmap.sidebar.team"));
        child(column);

        map.setBodySelectionListener(this::handleMapSelection);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (ItemPickerScreen.hasPendingPickForSidebarDebug()) {
            ItemStack pickedStack = ItemPickerScreen.pollPendingPickForSidebarDebug();
            if (pickedStack != null) {
                ItemStack displayStack = pickedStack.copy();
                displayStack.stackSize = 1;
                supplyDebugGhostHandler.setStackInSlot(0, displayStack);
            }
        }
        if (!map.isCreativeBuildModeEnabled() && isSupplyDebugOpen()) {
            WindowLayer layer = WindowLayer.of(this);
            if (layer != null) layer.close(SUPPLY_DEBUG_WINDOW);
        }
        String query = searchField == null ? ""
            : searchField.getText()
                .toLowerCase();
        if (!query.equals(searchQuery)) {
            searchQuery = query;
            markEntriesDirty();
        }
        if (entriesDirty && bodyList != null && bodyList.isValid()) {
            entriesDirty = false;
            rebuildBodyList();
        }
    }

    private void markEntriesDirty() {
        entriesDirty = true;
    }

    private void rebuildBodyList() {
        bodyList.removeAll();
        if (activeLayer != root) {
            List<VisibleEntry> entries = new ArrayList<>();
            for (CelestialObject child : CelestialClient.getChildren(activeLayer)) collect(child, 0, entries);
            for (VisibleEntry entry : entries) bodyList.child(bodyRow(entry));
        }
        bodyList.getScrollData()
            .scrollTo(bodyList.getScrollArea(), 0);
        bodyList.scheduleResize();
    }

    private IWidget bodyRow(VisibleEntry entry) {
        CelestialObject body = entry.body();
        Flow row = Flow.row()
            .coverChildren()
            .height(ROW_H)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .marginLeft(entry.depth() * INDENT);
        if (entry.hasChildren()) {
            row.child(GalaxiaUi.flatButton(ROW_H, ROW_H, () -> {
                if (!expanded.remove(body)) expanded.add(body);
                markEntriesDirty();
            })
                // Arrow pointing down while the branch is open
                .overlay(
                    new DynamicDrawable(
                        () -> (expanded.contains(body) ? EnumTextures.STARMAP_SIDEBAR_ARROW_DOWN
                            : EnumTextures.STARMAP_SIDEBAR_ARROW).getImage()).asIcon()
                                .size(ARROW)));
        } else {
            row.child(new Widget<>().size(ROW_H));
        }
        String name = body.displayName();
        row.child(GalaxiaUi.flatButton(GalaxiaUi.width(name) + GalaxiaUi.PAD * 2, ROW_H, () -> {
            map.focusOn(body);
            handleMapSelection(body, map.getClickMode() == OrbitalMapClickMode.HIERARCHY);
        })
            // The selected body is marked the way a selected button is
            .background(
                new DynamicDrawable(
                    () -> body.equals(map.getFocusedBody()) ? EnumTextures.GUI_BUTTON_SELECTED.getImage()
                        : IDrawable.EMPTY))
            .overlay(
                IKey.str(name)
                    .color(GalaxiaGuiStyle.TEXT)
                    .alignment(Alignment.CenterLeft)
                    .asIcon()
                    .marginLeft(GalaxiaUi.PAD))
            .hoverOverlay(
                IKey.str(name)
                    .color(GalaxiaGuiStyle.TEXT_TITLE)
                    .alignment(Alignment.CenterLeft)
                    .asIcon()
                    .marginLeft(GalaxiaUi.PAD)));
        return row;
    }

    private void collect(CelestialObject body, int depth, List<VisibleEntry> list) {
        boolean matches = searchQuery.isEmpty() || (isMajorBodySearchResult(body) && body.displayName()
            .toLowerCase()
            .contains(searchQuery));
        List<CelestialObject> children = CelestialClient.getChildren(body);
        if (matches) list.add(new VisibleEntry(body, depth, !children.isEmpty()));
        if (expanded.contains(body) || !searchQuery.isEmpty()) {
            for (CelestialObject child : children) collect(child, depth + 1, list);
        }
    }

    static boolean isMajorBodySearchResult(CelestialObject body) {
        return body != null && body.key()
            .isRegistered();
    }

    private String currentSystemLabel() {
        return currentSystem == null ? StatCollector.translateToLocal("galaxia.gui.orbital.layer.system")
            : currentSystem.displayName();
    }

    private void selectLayer(CelestialObject layerRoot) {
        activeLayer = layerRoot == null ? root : layerRoot;
        expanded.add(activeLayer);
        markEntriesDirty();
        map.showLayer(activeLayer);
    }

    private void handleMapSelection(CelestialObject body, boolean enterSystem) {
        if (body.objectClass() == CelestialObject.Class.STAR && root.key()
            .equals(body.parentKey())) {
            currentSystem = body;
            if (activeLayer == root && enterSystem) {
                activeLayer = body;
                expanded.add(body);
                markEntriesDirty();
                map.showLayer(body);
            }
        }
    }

    // region Supply Debug window

    private boolean isSupplyDebugOpen() {
        WindowLayer layer = isValid() ? WindowLayer.of(this) : null;
        return layer != null && layer.isOpen(SUPPLY_DEBUG_WINDOW);
    }

    private void toggleSupplyDebug() {
        WindowLayer layer = WindowLayer.of(this);
        if (layer == null) return;
        if (layer.isOpen(SUPPLY_DEBUG_WINDOW)) {
            layer.close(SUPPLY_DEBUG_WINDOW);
            return;
        }
        supplyDebugTargetAssetId = resolveSupplyDebugAssetId();
        supplyDebugGhostHandler.setStackInSlot(0, null);
        layer.open(
            WindowSpec.of(SUPPLY_DEBUG_WINDOW, "galaxia.gui.orbital.supply_debug")
                .anchor((lw, lh, w, h) -> new int[] { getArea().width + GalaxiaUi.PAD, 30 })
                .onClose(() -> supplyDebugTargetAssetId = null)
                .body(this::fillSupplyDebug));
    }

    private void fillSupplyDebug(GalaxiaUi.Column column) {
        ItemSlot ghost = SafePhantomItemSlot.create()
            .slot(new ModularSlot(supplyDebugGhostHandler, 0))
            .background(EnumTextures.GUI_SLOT.getImage());
        supplyDebugAmountField = GalaxiaUi.textField(null, 60)
            .setMaxLength(12);
        supplyDebugAmountField.setText("64");
        column.add(GalaxiaUi.text(this::resolveSupplyDebugTargetLabel))
            .row(
                GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.orbital.supply_debug.item")),
                ghost,
                GalaxiaUi.button(StatCollector.translateToLocal("galaxia.gui.common.select"), () -> {
                    ItemPickerScreen.setPendingForSidebarDebug();
                    ItemPickerScreen.FACTORY.openClient();
                }))
            .row(
                GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.orbital.supply_debug.amount")),
                supplyDebugAmountField)
            .add(
                GalaxiaUi.confirm(
                    StatCollector.translateToLocal("galaxia.gui.orbital.supply_debug.add"),
                    () -> resolveSupplyDebugAsset() != null && supplyDebugGhostHandler.getStackInSlot(0) != null,
                    this::confirmSupplyDebug));
    }

    private String resolveSupplyDebugTargetLabel() {
        CelestialAsset asset = resolveSupplyDebugAsset();
        if (asset != null) return asset.displayName();
        CelestialObject focused = map.getFocusedBody();
        if (focused == null) return StatCollector.translateToLocal("galaxia.gui.orbital.supply_debug.no_body");
        return StatCollector
            .translateToLocalFormatted("galaxia.gui.orbital.supply_debug.no_outpost", focused.displayName());
    }

    /** First working automated outpost or station on the focused body */
    private CelestialAsset resolveSupplyDebugAsset() {
        if (supplyDebugTargetAssetId != null) {
            CelestialAsset pinned = CelestialClient.getByAssetId(supplyDebugTargetAssetId);
            if (pinned != null && pinned.status() == CelestialAsset.Status.OPERATIONAL
                && (pinned.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST
                    || pinned.kind == CelestialAsset.Kind.AUTOMATED_STATION)) {
                return pinned;
            }
            supplyDebugTargetAssetId = null;
        }
        CelestialAsset.ID currentAssetId = resolveSupplyDebugAssetId();
        if (currentAssetId != null) {
            supplyDebugTargetAssetId = currentAssetId;
            return CelestialClient.getByAssetId(currentAssetId);
        }
        return null;
    }

    private CelestialAsset.ID resolveSupplyDebugAssetId() {
        CelestialObject focused = map.getFocusedBody();
        if (focused == null) return null;
        for (CelestialAsset asset : CelestialClient.getState(focused.key())) {
            if (asset.status() != CelestialAsset.Status.OPERATIONAL) continue;
            if (asset.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST
                || asset.kind == CelestialAsset.Kind.AUTOMATED_STATION) {
                return asset.assetId;
            }
        }
        return null;
    }

    private void confirmSupplyDebug() {
        long now = System.currentTimeMillis();
        if (now - lastSupplyDebugClickMs < 200L) return;
        lastSupplyDebugClickMs = now;
        CelestialAsset asset = resolveSupplyDebugAsset();
        ItemStack selectedStack = supplyDebugGhostHandler.getStackInSlot(0);
        if (asset == null || selectedStack == null) return;
        long amount;
        try {
            amount = Long.parseLong(
                supplyDebugAmountField.getText()
                    .trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (amount <= 0) return;
        amount = Math.min(amount, Integer.MAX_VALUE);
        ItemStackWrapper resource = ItemStackWrapper.of(selectedStack);
        if (resource == null) return;
        Galaxia.LOG.info("[Supply Debug] Adding {} x {} to {}", amount, resource, asset.assetId);
        CelestialClient.addInventory(asset.assetId, resource, amount);
    }

    // endregion

    private static int labelWidth(String key) {
        return GalaxiaUi.width(StatCollector.translateToLocal(key)) + 10;
    }

}
