package com.gtnewhorizons.galaxia.client.gui.station;

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
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

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
    private static final int BUTTON_HEIGHT = 72;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_COLUMNS = 3;
    private static final int BUTTON_TEXT_PADDING = 7;
    private static final int TEXT_BASELINE_OFFSET = 1;
    private static final int MULTIPLE_TOGGLE_WIDTH = 58;
    private static final int MULTIPLE_TOGGLE_HEIGHT = 14;
    private static final int CHECKBOX_SIZE = 10;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile @Nullable StationTileCoord pendingCoord;
    private static volatile boolean pendingInstantBuild;
    private static volatile boolean pendingMultipleBuild;

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord, boolean instantBuild) {
        pendingAssetId = assetId;
        pendingCoord = coord;
        pendingInstantBuild = instantBuild;
        pendingMultipleBuild = false;
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
            .size(PANEL_WIDTH, PANEL_HEIGHT)
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
                .pos(PANEL_PADDING, PANEL_PADDING));
        panel.child(
            createMultipleToggle().pos(PANEL_WIDTH - PANEL_PADDING - MULTIPLE_TOGGLE_WIDTH, PANEL_PADDING - 1)
                .size(MULTIPLE_TOGGLE_WIDTH, MULTIPLE_TOGGLE_HEIGHT));

        AutomatedFacility facility = resolveFacility();
        if (facility == null || pendingCoord == null) {
            panel.child(
                new TextWidget<>(IKey.str("No station selected")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(PANEL_PADDING, HEADER_HEIGHT + 14));
            return panel;
        }

        int buttonWidth = (PANEL_WIDTH - PANEL_PADDING * 2 - BUTTON_GAP * (BUTTON_COLUMNS - 1)) / BUTTON_COLUMNS;
        int x = PANEL_PADDING;
        int y = HEADER_HEIGHT + PANEL_PADDING;
        int column = 0;
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            if (!kind.isAllowedOn(facility.kind)) continue;
            panel.child(
                createKindButton(kind).pos(x, y)
                    .size(buttonWidth, BUTTON_HEIGHT));
            column++;
            if (column >= BUTTON_COLUMNS) {
                column = 0;
                x = PANEL_PADDING;
                y += BUTTON_HEIGHT + BUTTON_GAP;
            } else {
                x += buttonWidth + BUTTON_GAP;
            }
        }
        return panel;
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
                CelestialAsset.ID assetId = pendingAssetId;
                StationTileCoord coord = pendingCoord;
                boolean needsBuildPicker = pendingMultipleBuild || kind.defaultShape() != ModuleShape.SINGLE;
                if (assetId != null && needsBuildPicker) {
                    StationManagementScreen.openBuildPicker(assetId, kind, pendingInstantBuild);
                } else if (assetId != null && coord != null) {
                    CelestialClient.createModule(assetId, kind, pendingInstantBuild, coord);
                    StationManagementScreen.open(assetId, pendingInstantBuild);
                } else if (assetId != null) {
                    StationManagementScreen.open(assetId, pendingInstantBuild);
                } else {
                    Minecraft.getMinecraft()
                        .displayGuiScreen(null);
                }
                clearPending();
                return true;
            });
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
}
