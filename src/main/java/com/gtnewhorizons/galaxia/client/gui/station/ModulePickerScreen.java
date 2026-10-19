package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

/**
 * Window over the station window for choosing the module to build on a tile: first the kind from a grid of cards, then
 * its tier, variant and settings group
 */
public final class ModulePickerScreen {

    public static final String WINDOW_ID = "station.picker";

    private static final int COLUMNS = 3;
    private static final int CARD_WIDTH = 190;
    private static final int CARD_HEIGHT = 56;
    private static final int CARD_PADDING = 5;
    private static final int CHOICE_WIDTH = 46;
    private static final int WIDE_CHOICE_WIDTH = 92;
    private static final int GROUP_COLUMNS = 4;
    private static final int MAX_GROUPS = 8;
    private static final int LIST_HEIGHT = CARD_HEIGHT * 5 + GalaxiaUi.GAP * 4;

    private final CelestialAsset.ID assetId;
    private final StationTileCoord coord;
    private final boolean instantBuild;
    private boolean multipleBuild;
    private @Nullable FacilityModuleKind selectedKind;
    private ModuleTier selectedTier = ModuleTier.NONE;
    private HammerVariant hammerVariant = HammerVariant.BASE;
    private MinerFocusTier minerFocusTier = MinerFocusTier.NONE;
    private @Nullable SettingsGroup.ID settingsGroupId;
    private @Nullable GalaxiaWindow window;

    private ModulePickerScreen(CelestialAsset.ID assetId, StationTileCoord coord, boolean instantBuild) {
        this.assetId = assetId;
        this.coord = coord;
        this.instantBuild = instantBuild;
    }

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord, boolean instantBuild) {
        WindowLayer layer = WindowLayer.current();
        if (layer != null) open(layer, assetId, coord, instantBuild);
    }

    public static void open(WindowLayer layer, CelestialAsset.ID assetId, StationTileCoord coord,
        boolean instantBuild) {
        layer.close(WINDOW_ID);
        ModulePickerScreen picker = new ModulePickerScreen(assetId, coord, instantBuild);
        picker.window = layer.open(
            WindowSpec.of(WINDOW_ID, "galaxia.gui.station.picker.title")
                .owner(StationManagementScreen.windowId(assetId))
                .modal()
                .centered()
                .body(picker::body));
    }

    private void body(GalaxiaUi.Column column) {
        AutomatedFacility facility = CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility found ? found
            : null;
        if (facility == null) {
            column.dim(tr("galaxia.gui.station.picker.no_station"));
            return;
        }
        if (selectedKind == null) kindStep(column, facility);
        else specStep(column, facility, selectedKind);
    }

    // region Kind step

    private void kindStep(GalaxiaUi.Column column, AutomatedFacility facility) {
        String multiple = tr("galaxia.gui.station.picker.multiple");
        column.add(
            GalaxiaUi
                .toggle(
                    () -> multiple,
                    GalaxiaUi.width(multiple) + 10,
                    () -> multipleBuild,
                    () -> multipleBuild = !multipleBuild)
                .tooltip(t -> t.addLine(tr("galaxia.gui.station.picker.multiple.tooltip"))));

        ListWidget<IWidget, ?> cards = GalaxiaUi.scroll(LIST_HEIGHT);
        Flow grid = GalaxiaUi.column();
        cards.child(grid);
        Flow line = null;
        int index = 0;
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            if (!kind.isAllowedOn(facility.kind)) continue;
            if (kind.isDebugOnly() && !instantBuild) continue;
            if (index % COLUMNS == 0) {
                line = GalaxiaUi.row();
                grid.child(line);
            }
            line.child(kindCard(kind));
            index++;
        }
        column.add(cards);
        column.add(GalaxiaUi.button(tr("galaxia.gui.common.back"), this::close));
    }

    private ButtonWidget<?> kindCard(FacilityModuleKind kind) {
        int textWidth = CARD_WIDTH - CARD_PADDING * 2;
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        ModuleTierData data = definition == null ? null : definition.getTierData(kind.defaultTier());
        // A module without tiers shows none
        String tier = kind.defaultTier() == ModuleTier.NONE ? ""
            : kind.defaultTier()
                .name();
        Flow lines = GalaxiaUi.column()
            .padding(CARD_PADDING)
            .child(
                GalaxiaUi.row()
                    .child(
                        GalaxiaUi
                            .clipped(
                                kind.getDisplayName(),
                                textWidth - GalaxiaUi.width(tier) - GalaxiaUi.GAP,
                                GalaxiaGuiStyle.TEXT)
                            .width(textWidth - GalaxiaUi.width(tier) - GalaxiaUi.GAP))
                    .child(GalaxiaUi.dim(tier)))
            .child(GalaxiaUi.clipped(moduleDescription(kind), textWidth, GalaxiaGuiStyle.TEXT_DIM));
        if (data != null && data.powerDrawEuPerTick() != 0) {
            lines.child(
                GalaxiaUi.clipped(
                    tr("galaxia.gui.station.picker.energy", formatPower(data.powerDrawEuPerTick())),
                    textWidth,
                    GalaxiaGuiStyle.TEXT));
        }
        if (data != null) {
            lines.child(
                GalaxiaUi.clipped(
                    tr(
                        "galaxia.gui.station.picker.time_and_cost",
                        formatTicks(data.buildTicks()),
                        formatCost(data.constructionCost())),
                    textWidth,
                    GalaxiaGuiStyle.TEXT));
        }
        ButtonWidget<?> card = GalaxiaUi.card(CARD_WIDTH, CARD_HEIGHT, () -> selectKind(kind));
        card.name("module.kind." + kind.name())
            .child(lines);
        return card;
    }

    private void selectKind(FacilityModuleKind kind) {
        selectedKind = kind;
        selectedTier = kind.defaultTier();
        hammerVariant = HammerVariant.BASE;
        minerFocusTier = MinerFocusTier.NONE;
        settingsGroupId = null;
        normalizeSelectedTier(kind);
        rebuild();
    }

    // endregion

    // region Spec step

    private void specStep(GalaxiaUi.Column column, AutomatedFacility facility, FacilityModuleKind kind) {
        ModuleTier tier = ModuleUpgradeUiModel.normalizeBuildTier(kind, selectedTier, hammerVariant);
        column.add(
            IKey.str(kind.getDisplayName())
                .color(GalaxiaGuiStyle.TEXT_TITLE)
                .shadow(true)
                .asWidget());
        ModuleTierData data = FacilityModuleRegistry.get(kind)
            .getTierData(tier);
        if (data != null) {
            column.keyValue(tr("galaxia.gui.station.picker.time"), () -> formatTicks(data.buildTicks()));
            column.add(costList(data.constructionCost()));
        }

        column.section(tr("galaxia.gui.station.picker.tier"));
        Flow tiers = GalaxiaUi.row();
        for (ModuleTier option : kind.allowedTiers()) {
            if (option == ModuleTier.NONE) continue;
            tiers.child(
                choice(
                    option.name(),
                    CHOICE_WIDTH,
                    () -> canSelectTier(kind, option),
                    () -> selectedTier == option,
                    () -> {
                        selectedTier = option;
                        normalizeSelectedTier(kind);
                    }).name("module.tier." + option.name()));
        }
        column.add(tiers);

        if (kind == FacilityModuleKind.HAMMER) {
            column.section(tr("galaxia.gui.station.picker.variant"));
            Flow variants = GalaxiaUi.row();
            for (HammerVariant option : HammerVariant.values()) {
                variants
                    .child(choice(option.name(), WIDE_CHOICE_WIDTH, () -> true, () -> hammerVariant == option, () -> {
                        hammerVariant = option;
                        normalizeSelectedTier(kind);
                    }).name("module.hammer." + option.name()));
            }
            column.add(variants);
        }
        if (kind == FacilityModuleKind.MINER) {
            column.section(tr("galaxia.gui.station.picker.focus"));
            Flow focus = GalaxiaUi.row();
            for (MinerFocusTier option : MinerFocusTier.values()) {
                String label = option == MinerFocusTier.NONE ? tr("galaxia.gui.station.picker.none") : option.name();
                focus.child(
                    choice(
                        label,
                        CHOICE_WIDTH,
                        () -> true,
                        () -> minerFocusTier == option,
                        () -> minerFocusTier = option).name("module.focus." + option.name()));
            }
            column.add(focus);
        }

        List<GroupOption> groups = groupOptions(facility, kind);
        if (groups.size() > 1) {
            column.section(tr("galaxia.gui.station.picker.group"));
            Flow line = null;
            for (int i = 0; i < groups.size(); i++) {
                if (i % GROUP_COLUMNS == 0) {
                    line = GalaxiaUi.row();
                    column.add(line);
                }
                GroupOption option = groups.get(i);
                line.child(
                    choice(
                        option.label(),
                        WIDE_CHOICE_WIDTH,
                        () -> true,
                        () -> Objects.equals(settingsGroupId, option.groupId()),
                        () -> settingsGroupId = option.groupId()));
            }
        }

        Flow footer = GalaxiaUi.footer(
            tr("galaxia.gui.common.back"),
            this::backToKinds,
            tr("galaxia.gui.station.picker.build"),
            () -> true,
            this::confirmSelectedBuild);
        ((Widget<?>) footer.getChildren()
            .get(1)).name("module.build");
        column.add(footer);
    }

    /** Every item the build takes, one line each */
    private static Flow costList(Map<ItemStack, Long> cost) {
        Flow list = GalaxiaUi.column();
        if (cost.isEmpty()) {
            list.child(GalaxiaUi.dim(tr("galaxia.gui.station.picker.free")));
            return list;
        }
        for (Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            list.child(
                GalaxiaUi.row()
                    .child(GalaxiaUi.item(entry.getKey()))
                    .child(
                        GalaxiaUi.text(
                            tr(
                                "galaxia.gui.station.picker.cost_line",
                                formatAmount(entry.getValue()),
                                entry.getKey()
                                    .getDisplayName()))));
        }
        return list;
    }

    /** Option button that rebuilds the window after a pick, so the cost and the tiers follow the choice */
    private ButtonWidget<?> choice(String label, int width, BooleanSupplier enabled, BooleanSupplier selected,
        Runnable onPick) {
        return GalaxiaUi.choice(() -> label, width, selected, enabled, () -> {
            onPick.run();
            rebuild();
        });
    }

    private boolean canSelectTier(FacilityModuleKind kind, ModuleTier tier) {
        return kind != FacilityModuleKind.HAMMER || ModuleHammer.supportsTier(hammerVariant, tier);
    }

    private void normalizeSelectedTier(FacilityModuleKind kind) {
        selectedTier = ModuleUpgradeUiModel.normalizeBuildTier(kind, selectedTier, hammerVariant);
    }

    private static List<GroupOption> groupOptions(AutomatedFacility facility, FacilityModuleKind kind) {
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption(tr("galaxia.gui.station.picker.no_group"), null));
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        if (definition == null || !definition.settingsGroups()) return options;
        facility.settingsGroups()
            .stream()
            .filter(group -> group.kind() == kind)
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .limit(MAX_GROUPS)
            .forEach(group -> options.add(new GroupOption(group.displayName(), group.id())));
        return options;
    }

    private void backToKinds() {
        selectedKind = null;
        rebuild();
    }

    private void confirmSelectedBuild() {
        FacilityModuleKind kind = selectedKind;
        if (kind == null) {
            close();
            return;
        }
        ModuleShape shape = kind.defaultShape();
        ModuleTier tier = ModuleUpgradeUiModel.normalizeBuildTier(kind, selectedTier, hammerVariant);
        HammerVariant variant = kind == FacilityModuleKind.HAMMER ? hammerVariant : null;
        MinerFocusTier focus = kind == FacilityModuleKind.MINER ? minerFocusTier : MinerFocusTier.NONE;
        if (multipleBuild || shape != ModuleShape.SINGLE) {
            // Reopens the station window, which closes this one with it
            StationManagementScreen
                .openBuildPicker(assetId, kind, shape, tier, variant, focus, settingsGroupId, instantBuild);
            return;
        }
        boolean sent = CelestialClient.createModules(
            assetId,
            kind,
            shape,
            tier,
            variant,
            focus,
            settingsGroupId,
            instantBuild,
            List.of(ModulePlacement.at(coord)));
        if (!sent) StationNotificationHelper.showFailure(tr("galaxia.gui.station.picker.build_failed"));
        close();
    }

    private void rebuild() {
        if (window != null) window.rebuildLater();
    }

    private void close() {
        if (window != null) window.close();
    }

    // endregion

    // region Formatting

    private static String moduleDescription(FacilityModuleKind kind) {
        return tr(
            "galaxia.gui.station.picker.description." + kind.name()
                .toLowerCase());
    }

    private static String formatTicks(int ticks) {
        if (ticks % 20 == 0) return ticks / 20 + "s";
        return ticks + "t";
    }

    private static String formatPower(long powerDraw) {
        if (powerDraw < 0) return "+" + formatAmount(-powerDraw);
        if (powerDraw > 0) return "-" + formatAmount(powerDraw);
        return "0";
    }

    private static String formatCost(Map<ItemStack, Long> cost) {
        if (cost.isEmpty()) return tr("galaxia.gui.station.picker.free");
        int shown = 0;
        StringBuilder out = new StringBuilder();
        for (Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            if (shown > 0) out.append(", ");
            out.append(
                tr(
                    "galaxia.gui.station.picker.cost_line",
                    formatAmount(entry.getValue()),
                    entry.getKey()
                        .getDisplayName()));
            shown++;
            if (shown >= 2) break;
        }
        int remaining = cost.size() - shown;
        if (remaining > 0) out.append(" ")
            .append(StatCollector.translateToLocalFormatted("galaxia.gui.common.more", remaining));
        return out.toString();
    }

    private static String formatAmount(long amount) {
        if (amount >= 1_000_000L) return amount / 1_000_000L + "M";
        if (amount >= 1_000L) return amount / 1_000L + "k";
        return Long.toString(amount);
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    // endregion

    private record GroupOption(String label, SettingsGroup.ID groupId) {}
}
