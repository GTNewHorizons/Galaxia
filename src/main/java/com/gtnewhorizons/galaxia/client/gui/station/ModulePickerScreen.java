package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.WidgetOutline;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ModulePickerScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_module_picker",
        ModulePickerScreen::new);

    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 430;
    private static final int HEADER_HEIGHT = 24;
    private static final int PANEL_PADDING = 8;
    private static final int BUTTON_COLUMNS = 3;
    private static final int BUTTON_TEXT_PADDING = 7;
    private static final int TEXT_BASELINE_OFFSET = 1;
    private static final int MULTIPLE_TOGGLE_WIDTH = 58;
    private static final int MULTIPLE_TOGGLE_HEIGHT = 14;
    private static final int CHECKBOX_SIZE = 10;
    private static final int SPEC_LEFT = PANEL_PADDING;
    private static final int SPEC_TOP = HEADER_HEIGHT + PANEL_PADDING;
    private static final int SPEC_BUTTON_WIDTH = 92;
    private static final int SPEC_SMALL_BUTTON_WIDTH = SPEC_BUTTON_WIDTH / 2;
    private static final int SPEC_BUTTON_HEIGHT = 18;
    private static final int SPEC_BUTTON_GAP = 5;
    private static final int SPEC_SECTION_GAP = 40;
    private static final int SPEC_FOOTER_Y = PANEL_HEIGHT - PANEL_PADDING - 20;
    private static final int SPEC_BACK_WIDTH = 58;
    private static final int SPEC_BUILD_WIDTH = 72;
    private static final int HEADER_BUTTON_GAP = 8;
    private static final float FULL_REL = 1f;
    private static final float PANEL_PADDING_X_REL = (float) PANEL_PADDING / PANEL_WIDTH;
    private static final float PANEL_PADDING_Y_REL = (float) PANEL_PADDING / PANEL_HEIGHT;
    private static final float HEADER_CONTROL_HEIGHT_REL = (float) MULTIPLE_TOGGLE_HEIGHT / PANEL_HEIGHT;
    private static final float HEADER_TITLE_WIDTH_REL = 0.45f;
    private static final float HEADER_TITLE_HEIGHT_REL = HEADER_CONTROL_HEIGHT_REL;
    private static final float HEADER_BACK_WIDTH_REL = (float) SPEC_BACK_WIDTH / PANEL_WIDTH;
    private static final float HEADER_MULTIPLE_WIDTH_REL = (float) MULTIPLE_TOGGLE_WIDTH / PANEL_WIDTH;
    private static final float LIST_TOP_REL = (float) (HEADER_HEIGHT + PANEL_PADDING) / PANEL_HEIGHT;
    private static final float LIST_HEIGHT_REL = FULL_REL - LIST_TOP_REL - PANEL_PADDING_Y_REL;
    private static final float LIST_COLUMN_GAP_REL = 5f / (PANEL_WIDTH - PANEL_PADDING * 2);
    private static final float LIST_ROW_GAP_REL = 5f / (PANEL_HEIGHT - HEADER_HEIGHT - PANEL_PADDING * 2);
    private static final float LIST_CARD_HEIGHT_REL = 72f / (PANEL_HEIGHT - HEADER_HEIGHT - PANEL_PADDING * 2);
    private static final float LIST_CARD_WIDTH_REL = (FULL_REL - LIST_COLUMN_GAP_REL * (BUTTON_COLUMNS - 1))
        / BUTTON_COLUMNS;
    private static final int HEADER_MULTIPLE_X = PANEL_WIDTH - PANEL_PADDING
        - SPEC_BACK_WIDTH
        - HEADER_BUTTON_GAP
        - MULTIPLE_TOGGLE_WIDTH;
    private static final int HEADER_BACK_X = PANEL_WIDTH - PANEL_PADDING - SPEC_BACK_WIDTH;
    private static final int HEADER_CONTROL_Y = PANEL_PADDING - 1;
    private static final int LIST_X = PANEL_PADDING;
    private static final int LIST_Y = HEADER_HEIGHT + PANEL_PADDING;
    private static final int LIST_WIDTH = PANEL_WIDTH - PANEL_PADDING * 2;
    private static final int LIST_COLUMN_GAP = 5;
    private static final int LIST_ROW_GAP = 5;
    private static final int LIST_CARD_WIDTH = (LIST_WIDTH - LIST_COLUMN_GAP * (BUTTON_COLUMNS - 1)) / BUTTON_COLUMNS;
    private static final int LIST_CARD_HEIGHT = 72;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile @Nullable StationTileCoord pendingCoord;
    private static volatile @Nullable FacilityModuleKind pendingSelectedKind;
    private static volatile ModuleTier pendingSelectedTier = ModuleTier.NONE;
    private static volatile HammerVariant pendingHammerVariant = HammerVariant.BASE;
    private static volatile MinerFocusTier pendingMinerFocusTier = MinerFocusTier.NONE;
    private static volatile short pendingSettingsGroupId;
    private static volatile boolean pendingInstantBuild;
    private static volatile boolean pendingMultipleBuild;

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord, boolean instantBuild) {
        pendingAssetId = assetId;
        pendingCoord = coord;
        pendingInstantBuild = instantBuild;
        pendingMultipleBuild = false;
        pendingSelectedKind = null;
        pendingSettingsGroupId = 0;
        FACTORY.openClient();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_station_module_picker", PANEL_WIDTH, PANEL_HEIGHT);
        ParentWidget<?> backgroundLayer = new PassiveBackgroundLayer().pos(0, 0)
            .sizeRel(FULL_REL, FULL_REL)
            .background(drawable((ctx, x, y, w, h) -> {
                net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor());
                net.minecraft.client.gui.Gui
                    .drawRect(x, y, x + w, y + HEADER_HEIGHT, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
            }));
        panel.child(backgroundLayer);
        panel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

        panel.child(
            new TextWidget<>(IKey.str("Build module")).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(PANEL_PADDING, PANEL_PADDING)
                .widthRel(HEADER_TITLE_WIDTH_REL)
                .heightRel(HEADER_TITLE_HEIGHT_REL));
        panel.child(
            createMultipleToggle().pos(HEADER_MULTIPLE_X, HEADER_CONTROL_Y)
                .widthRel(HEADER_MULTIPLE_WIDTH_REL)
                .heightRel(HEADER_CONTROL_HEIGHT_REL));

        AutomatedFacility facility = resolveFacility();
        if (facility == null || pendingCoord == null) {
            panel.child(
                new TextWidget<>(IKey.str("No station selected")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, HEADER_HEIGHT + 14)
                    .widthRel(0.5f)
                    .heightRel(HEADER_TITLE_HEIGHT_REL));
            return panel;
        }

        if (pendingSelectedKind != null) {
            buildSpecUI(panel, facility, pendingSelectedKind);
            return panel;
        }

        int column = 0;
        int row = 0;
        ParentWidget<?> buildListLayer = new ParentWidget<>().pos(LIST_X, LIST_Y)
            .widthRel(FULL_REL - PANEL_PADDING_X_REL * 2)
            .heightRel(LIST_HEIGHT_REL);
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            if (!kind.isAllowedOn(facility.kind)) continue;
            buildListLayer.child(
                createKindButton(kind).pos(listCardX(column), listCardY(row))
                    .widthRel(LIST_CARD_WIDTH_REL)
                    .heightRel(LIST_CARD_HEIGHT_REL));
            column++;
            if (column >= BUTTON_COLUMNS) {
                column = 0;
                row++;
            }
        }
        panel.child(buildListLayer);
        panel.child(
            ModuleConfigModalSupport.button(() -> pendingAssetId != null, "Back", this::backToMap)
                .pos(HEADER_BACK_X, HEADER_CONTROL_Y)
                .widthRel(HEADER_BACK_WIDTH_REL)
                .heightRel(HEADER_CONTROL_HEIGHT_REL));
        return panel;
    }

    private static int listCardX(int column) {
        return column * (LIST_CARD_WIDTH + LIST_COLUMN_GAP);
    }

    private static int listCardY(int row) {
        return row * (LIST_CARD_HEIGHT + LIST_ROW_GAP);
    }

    private static @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID assetId = pendingAssetId;
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
    }

    private ButtonWidget<?> createMultipleToggle() {
        return new ButtonWidget<>().background(drawable((ctx, x, y, w, h) -> drawMultipleToggle(x, y, w, h, false)))
            .hoverBackground(drawable((ctx, x, y, w, h) -> drawMultipleToggle(x, y, w, h, true)))
            .onMouseTapped(mouseButton -> {
                if (mouseButton != 0) return false;
                pendingMultipleBuild = !pendingMultipleBuild;
                return true;
            })
            .tooltipDynamic(t -> t.addLine("Build on multiple compatible tiles"));
    }

    private ButtonWidget<?> createKindButton(FacilityModuleKind kind) {
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
            .overlay(drawable((ctx, x, y, w, h) -> drawKindButton(kind, x, y, w, h)))
            .onMouseTapped(mouseButton -> {
                if (mouseButton != 0) return false;
                selectKind(kind);
                FACTORY.openClient();
                return true;
            });
    }

    private void buildSpecUI(ModularPanel panel, AutomatedFacility facility, FacilityModuleKind kind) {
        panel.child(
            new BuildSpecLayer(facility, kind).pos(0, 0)
                .size(PANEL_WIDTH, PANEL_HEIGHT));

        int y = SPEC_TOP + 50;
        int x = SPEC_LEFT;
        for (ModuleTier tier : kind.allowedTiers()) {
            if (tier == ModuleTier.NONE) continue;
            ModuleTier optionTier = tier;
            panel.child(
                createChoiceButton(
                    () -> optionTier.name(),
                    () -> canSelectTier(kind, optionTier),
                    () -> pendingSelectedTier == optionTier,
                    () -> {
                        pendingSelectedTier = optionTier;
                        normalizeSelectedTier(kind);
                    }).pos(x, y)
                        .size(SPEC_SMALL_BUTTON_WIDTH, SPEC_BUTTON_HEIGHT));
            x += SPEC_SMALL_BUTTON_WIDTH + SPEC_BUTTON_GAP;
        }

        y += SPEC_SECTION_GAP;
        x = SPEC_LEFT;
        if (kind == FacilityModuleKind.HAMMER) {
            for (HammerVariant variant : HammerVariant.values()) {
                HammerVariant optionVariant = variant;
                panel.child(
                    createChoiceButton(
                        () -> optionVariant.name(),
                        () -> true,
                        () -> pendingHammerVariant == optionVariant,
                        () -> {
                            pendingHammerVariant = optionVariant;
                            normalizeSelectedTier(kind);
                        }).pos(x, y)
                            .size(SPEC_BUTTON_WIDTH, SPEC_BUTTON_HEIGHT));
                x += SPEC_BUTTON_WIDTH + SPEC_BUTTON_GAP;
            }
            y += SPEC_SECTION_GAP;
            x = SPEC_LEFT;
        }
        if (kind == FacilityModuleKind.MINER) {
            for (MinerFocusTier tier : MinerFocusTier.values()) {
                MinerFocusTier optionTier = tier;
                panel.child(
                    createChoiceButton(
                        () -> optionTier == MinerFocusTier.NONE ? "None" : optionTier.name(),
                        () -> true,
                        () -> pendingMinerFocusTier == optionTier,
                        () -> pendingMinerFocusTier = optionTier).pos(x, y)
                            .size(SPEC_SMALL_BUTTON_WIDTH, SPEC_BUTTON_HEIGHT));
                x += SPEC_SMALL_BUTTON_WIDTH + SPEC_BUTTON_GAP;
            }
            y += SPEC_SECTION_GAP;
            x = SPEC_LEFT;
        }

        List<GroupOption> groups = groupOptions(facility, kind);
        for (int i = 0; i < groups.size(); i++) {
            GroupOption option = groups.get(i);
            panel.child(
                createChoiceButton(
                    option::label,
                    () -> true,
                    () -> pendingSettingsGroupId == option.groupId(),
                    () -> pendingSettingsGroupId = option.groupId()).pos(x, y)
                        .size(SPEC_BUTTON_WIDTH + 24, SPEC_BUTTON_HEIGHT));
            x += SPEC_BUTTON_WIDTH + 24 + SPEC_BUTTON_GAP;
            if (x + SPEC_BUTTON_WIDTH > PANEL_WIDTH - PANEL_PADDING) {
                x = SPEC_LEFT;
                y += SPEC_BUTTON_HEIGHT + SPEC_BUTTON_GAP;
            }
        }

        panel.child(
            ModuleConfigModalSupport.button(() -> true, "Back", this::backToKinds)
                .pos(SPEC_LEFT, SPEC_FOOTER_Y)
                .size(SPEC_BACK_WIDTH, 20));
        panel.child(
            ModuleConfigModalSupport.button(() -> true, "Build", this::confirmSelectedBuild)
                .pos(PANEL_WIDTH - PANEL_PADDING - SPEC_BUILD_WIDTH, SPEC_FOOTER_Y)
                .size(SPEC_BUILD_WIDTH, 20));
    }

    private static void selectKind(FacilityModuleKind kind) {
        pendingSelectedKind = kind;
        pendingSelectedTier = kind.defaultTier();
        pendingHammerVariant = HammerVariant.BASE;
        pendingMinerFocusTier = MinerFocusTier.NONE;
        pendingSettingsGroupId = 0;
        normalizeSelectedTier(kind);
    }

    private void backToKinds() {
        pendingSelectedKind = null;
        FACTORY.openClient();
    }

    private void backToMap() {
        CelestialAsset.ID assetId = pendingAssetId;
        boolean instantBuild = pendingInstantBuild;
        clearPending();
        if (assetId != null) {
            StationManagementScreen.open(assetId, instantBuild);
        } else {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
        }
    }

    private void confirmSelectedBuild() {
        FacilityModuleKind kind = pendingSelectedKind;
        CelestialAsset.ID assetId = pendingAssetId;
        StationTileCoord coord = pendingCoord;
        if (kind == null || assetId == null) {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
            clearPending();
            return;
        }
        ModuleShape shape = kind.defaultShape();
        ModuleTier selectedTier = ModuleUpgradeUiModel
            .normalizeBuildTier(kind, pendingSelectedTier, pendingHammerVariant);
        boolean needsBuildPicker = pendingMultipleBuild || shape != ModuleShape.SINGLE;
        if (needsBuildPicker) {
            StationManagementScreen.openBuildPicker(
                assetId,
                kind,
                shape,
                selectedTier,
                kind == FacilityModuleKind.HAMMER ? pendingHammerVariant : null,
                kind == FacilityModuleKind.MINER ? pendingMinerFocusTier : MinerFocusTier.NONE,
                pendingSettingsGroupId,
                pendingInstantBuild);
        } else if (coord != null) {
            boolean sent = CelestialClient.createModules(
                assetId,
                kind,
                shape,
                selectedTier,
                kind == FacilityModuleKind.HAMMER ? pendingHammerVariant : null,
                kind == FacilityModuleKind.MINER ? pendingMinerFocusTier : MinerFocusTier.NONE,
                pendingSettingsGroupId,
                pendingInstantBuild,
                List.of(coord));
            if (!sent) StationNotificationHelper.showFailure("Module build request failed");
            StationManagementScreen.open(assetId, pendingInstantBuild);
        } else {
            StationManagementScreen.open(assetId, pendingInstantBuild);
        }
        clearPending();
    }

    private ButtonWidget<?> createChoiceButton(java.util.function.Supplier<String> labelSupplier,
        java.util.function.BooleanSupplier enabledSupplier, java.util.function.BooleanSupplier selectedSupplier,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                drawable(
                    (ctx, x, y, w, h) -> drawChoiceButton(
                        labelSupplier.get(),
                        x,
                        y,
                        w,
                        h,
                        enabledSupplier.getAsBoolean(),
                        selectedSupplier.getAsBoolean(),
                        false)))
            .hoverBackground(
                drawable(
                    (ctx, x, y, w, h) -> drawChoiceButton(
                        labelSupplier.get(),
                        x,
                        y,
                        w,
                        h,
                        enabledSupplier.getAsBoolean(),
                        selectedSupplier.getAsBoolean(),
                        true)))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    private static void drawChoiceButton(String label, int x, int y, int width, int height, boolean enabled,
        boolean selected, boolean hovered) {
        int bg = !enabled ? EnumColors.MAP_COLOR_BTN_DISABLED.getColor()
            : hovered || selected ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor();
        int border = enabled ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
            : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor();
        BorderedRect.draw(x, y, width, height, bg, border);
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String text = fr.trimStringToWidth((selected ? "* " : "") + label, width - 4);
        int color = enabled ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
            : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
        fr.drawStringWithShadow(
            text,
            x + (width - fr.getStringWidth(text)) / 2,
            y + (height - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
            color);
    }

    private static boolean canSelectTier(FacilityModuleKind kind, ModuleTier tier) {
        return kind != FacilityModuleKind.HAMMER || ModuleHammer.supportsTier(pendingHammerVariant, tier);
    }

    private static void normalizeSelectedTier(FacilityModuleKind kind) {
        pendingSelectedTier = ModuleUpgradeUiModel.normalizeBuildTier(kind, pendingSelectedTier, pendingHammerVariant);
    }

    private static List<GroupOption> groupOptions(AutomatedFacility facility, FacilityModuleKind kind) {
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        if (definition == null || !definition.settingsGroups()) return List.of(new GroupOption("No Group", (short) 0));
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption("No Group", (short) 0));
        facility.settingsGroups()
            .groups()
            .values()
            .stream()
            .filter(group -> group.kind() == kind && group.isJoinable())
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .limit(8)
            .forEach(group -> options.add(new GroupOption(group.displayName(), group.id())));
        return options;
    }

    private static void drawMultipleToggle(int x, int y, int width, int height, boolean hovered) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int boxY = y + (height - CHECKBOX_SIZE) / 2;
        BorderedRect.draw(
            x,
            boxY,
            CHECKBOX_SIZE,
            CHECKBOX_SIZE,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
        if (pendingMultipleBuild) {
            fr.drawStringWithShadow("X", x + 2, boxY + 1, EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
        }
        String label = fr.trimStringToWidth("Multiple", width - CHECKBOX_SIZE - 4);
        fr.drawStringWithShadow(
            label,
            x + CHECKBOX_SIZE + 4,
            y + (height - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static void drawKindButton(FacilityModuleKind kind, int x, int y, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String label = kind.getDisplayName();
        int textX = x + BUTTON_TEXT_PADDING;
        int lineY = y + 5;
        fr.drawStringWithShadow(label, textX, lineY, EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());

        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(kind);
        ModuleTierData data = definition == null ? null : definition.getTierData(kind.defaultTier());
        String tier = kind.defaultTier()
            .name();
        int tierWidth = fr.getStringWidth(tier);
        fr.drawStringWithShadow(
            tier,
            x + width - tierWidth - BUTTON_TEXT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());

        lineY += fr.FONT_HEIGHT;
        fr.drawStringWithShadow(
            fr.trimStringToWidth(moduleDescription(kind), width - BUTTON_TEXT_PADDING * 2),
            textX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        if (data == null) return;

        lineY += fr.FONT_HEIGHT;
        fr.drawStringWithShadow(
            fr.trimStringToWidth(energyAndUpkeepLine(data), width - BUTTON_TEXT_PADDING * 2),
            textX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        lineY += fr.FONT_HEIGHT;
        fr.drawStringWithShadow(
            "Build Time: " + formatTicks(data.buildTicks()),
            textX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        lineY += fr.FONT_HEIGHT;
        fr.drawStringWithShadow(
            fr.trimStringToWidth("Build Cost: " + formatCost(data.constructionCost()), width - BUTTON_TEXT_PADDING * 2),
            textX,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static String moduleDescription(FacilityModuleKind kind) {
        return switch (kind) {
            case HAMMER -> "Launches logistics packages";
            case MINER -> "Extracts planetary ores";
            case POWER -> "Adds station EU generation";
            case GEOTHERMAL_GENERATOR -> "Generates EU from magma pools";
            case STORAGE -> "Adds item inventory capacity";
            case TANK -> "Adds fluid inventory capacity";
            case BATTERY -> "Adds energy buffer capacity";
            case MAINTENANCE_BAY -> "Reduces station upkeep";
            case MACERATOR -> "Runs macerator recipes";
            case CENTRIFUGE -> "Runs centrifuge recipes";
            case ELECTROLYZER -> "Runs electrolyzer recipes";
            case CHEMICAL_REACTOR -> "Runs chemical recipes";
            case ASSEMBLER -> "Runs assembler recipes";
            case DISTILLERY -> "Runs distillery recipes";
        };
    }

    private static String energyAndUpkeepLine(ModuleTierData data) {
        return "Energy (EU/t): " + formatPower(data.powerDrawEuPerTick()) + "  Upkeep (items/min): 0";
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

    private static String formatCost(java.util.Map<ItemStack, Long> cost) {
        if (cost.isEmpty()) return "free";
        int shown = 0;
        StringBuilder out = new StringBuilder();
        for (java.util.Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            if (shown > 0) out.append(", ");
            out.append(formatAmount(entry.getValue()))
                .append("x ")
                .append(
                    entry.getKey()
                        .getDisplayName());
            shown++;
            if (shown >= 2) break;
        }
        int remaining = cost.size() - shown;
        if (remaining > 0) out.append(" +")
            .append(remaining);
        return out.toString();
    }

    private static String formatAmount(long amount) {
        if (amount >= 1_000_000L) return amount / 1_000_000L + "M";
        if (amount >= 1_000L) return amount / 1_000L + "k";
        return Long.toString(amount);
    }

    private static void clearPending() {
        pendingAssetId = null;
        pendingCoord = null;
        pendingSelectedKind = null;
        pendingSelectedTier = ModuleTier.NONE;
        pendingHammerVariant = HammerVariant.BASE;
        pendingMinerFocusTier = MinerFocusTier.NONE;
        pendingSettingsGroupId = 0;
        pendingInstantBuild = false;
        pendingMultipleBuild = false;
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private static final class PassiveBackgroundLayer extends ParentWidget<PassiveBackgroundLayer> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private static final class BuildSpecLayer extends ParentWidget<BuildSpecLayer> {

        private final AutomatedFacility facility;
        private final FacilityModuleKind kind;

        private BuildSpecLayer(AutomatedFacility facility, FacilityModuleKind kind) {
            this.facility = facility;
            this.kind = kind;
        }

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            ModuleTier selectedTier = ModuleUpgradeUiModel
                .normalizeBuildTier(kind, pendingSelectedTier, pendingHammerVariant);
            int y = SPEC_TOP;
            y = drawSpecLine(
                "Configure " + kind.getDisplayName(),
                SPEC_LEFT,
                y,
                EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
            y = drawSpecLine(
                "Target: " + selectedTier + physicalSuffix(kind),
                SPEC_LEFT,
                y,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            ModuleTierData data = FacilityModuleRegistry.get(kind)
                .getTierData(selectedTier);
            if (data != null) {
                y = drawSpecLine(
                    "Build: " + formatTicks(data.buildTicks()) + "  Cost: " + formatCost(data.constructionCost()),
                    SPEC_LEFT,
                    y,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            }
            drawSpecLine("Tier", SPEC_LEFT, SPEC_TOP + 38, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
            int sectionY = SPEC_TOP + 38 + SPEC_SECTION_GAP;
            if (kind == FacilityModuleKind.HAMMER) {
                drawSpecLine("Variant", SPEC_LEFT, sectionY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
                sectionY += SPEC_SECTION_GAP;
            } else if (kind == FacilityModuleKind.MINER) {
                drawSpecLine("Focus Tier", SPEC_LEFT, sectionY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
                sectionY += SPEC_SECTION_GAP;
            }
            drawSpecLine(
                FacilityModuleRegistry.get(kind)
                    .settingsGroups() ? "Settings Group" : "Settings Group: n/a",
                SPEC_LEFT,
                sectionY,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        }

        private static String physicalSuffix(FacilityModuleKind kind) {
            if (kind == FacilityModuleKind.HAMMER) return " " + pendingHammerVariant.name();
            if (kind == FacilityModuleKind.MINER) return " focus " + pendingMinerFocusTier.name();
            return "";
        }

        private int drawSpecLine(String text, int x, int y, int color) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawStringWithShadow(fr.trimStringToWidth(text, PANEL_WIDTH - PANEL_PADDING * 2), x, y, color);
            return y + fr.FONT_HEIGHT + 3;
        }
    }

    private record GroupOption(String label, short groupId) {}
}
