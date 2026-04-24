package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ModulePickerScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_module_picker",
        ModulePickerScreen::new);

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 150;
    private static final int HEADER_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 5;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile @Nullable StationTileCoord pendingCoord;
    private static volatile boolean pendingInstantBuild;
    private static volatile @Nullable GuiScreen pendingReturnScreen;

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord, boolean instantBuild) {
        pendingAssetId = assetId;
        pendingCoord = coord;
        pendingInstantBuild = instantBuild;
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        FACTORY.openClient();
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_station_module_picker", PANEL_WIDTH, PANEL_HEIGHT);
        panel.background(drawable((ctx, x, y, w, h) -> {
            BorderedRect.draw(x, y, w, h, 0xE00B1320, 0xFF334C68);
            net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + HEADER_HEIGHT, 0xFF14243A);
        }));

        panel.child(
            new TextWidget<>(IKey.str("Build module")).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(8, 8));

        AutomatedFacility facility = resolveFacility();
        if (facility == null || pendingCoord == null) {
            panel.child(
                new TextWidget<>(IKey.str("No station selected")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(8, 38));
            return panel;
        }

        int y = HEADER_HEIGHT + 8;
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            if (!kind.isAllowedOn(facility.kind)) continue;
            panel.child(
                createKindButton(kind).pos(8, y)
                    .size(PANEL_WIDTH - 16, BUTTON_HEIGHT));
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        return panel;
    }

    private static @Nullable AutomatedFacility resolveFacility() {
        CelestialAsset.ID assetId = pendingAssetId;
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
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
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                CelestialAsset.ID assetId = pendingAssetId;
                StationTileCoord coord = pendingCoord;
                if (assetId != null && coord != null) {
                    CelestialClient.createModule(assetId, kind, pendingInstantBuild, coord);
                }
                Minecraft.getMinecraft()
                    .displayGuiScreen(pendingReturnScreen);
                clearPending();
                return true;
            });
    }

    private static void drawKindButton(FacilityModuleKind kind, int x, int y, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String label = kind.getDisplayName();
        int textY = y + (height - fr.FONT_HEIGHT) / 2 + 1;
        fr.drawStringWithShadow(label, x + 7, textY, EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());

        ModuleInstance preview = kind.createInstance();
        String stats = preview.powerDrawEuPerTick() + " EU/t";
        int statsWidth = fr.getStringWidth(stats);
        fr.drawStringWithShadow(stats, x + width - statsWidth - 7, textY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private static void clearPending() {
        pendingAssetId = null;
        pendingCoord = null;
        pendingInstantBuild = false;
        pendingReturnScreen = null;
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
