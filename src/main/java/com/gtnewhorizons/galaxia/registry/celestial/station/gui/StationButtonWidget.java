package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;

public class StationButtonWidget extends ButtonWidget<StationButtonWidget> {

    private static final int SQUARE_BUTTON_SIZE = 18;

    public StationButtonWidget(IKey key) {
        super();
        size(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE);
        background(
            new Rectangle().color(EnumColors.STATION_PANEL_BG.getColor()),
            new Rectangle().hollow(1)
                .color(EnumColors.STATION_HEADER_UNDERLINE.getColor()));

        hoverBackground(new Rectangle().color(EnumColors.STATION_TOGGLE_ON.getColor()));
        tooltipBuilder(t -> t.addLine(key));
    }

    public static ButtonWidget<?> closeButton(Runnable onClick) {
        return new StationButtonWidget(IKey.lang("galaxia.gui.airlock_controller.settings.close.tooltip"))
            .overlay(GuiTextures.CLOSE)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                onClick.run();
                return true;
            });
    }

    public static ButtonWidget<?> settingsButton(IPanelHandler settingsHandler) {
        return new StationButtonWidget(IKey.lang("galaxia.gui.airlock_controller.settings_button.tooltip"))
            .overlay(GuiTextures.GEAR)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;

                if (settingsHandler.isPanelOpen()) {
                    settingsHandler.closePanel();
                } else {
                    settingsHandler.openPanel();
                }

                return true;
            });
    }

    public static ButtonWidget<?> refreshButton(TileEntity tile) {
        return new StationButtonWidget(IKey.lang("galaxia.gui.airlock_controller.refresh_button.tooltip"))
            .overlay(GuiTextures.REFRESH)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                GuiFactories.tileEntity()
                    .openClient(tile.xCoord, tile.yCoord, tile.zCoord);
                return true;
            });
    }

    public static ButtonWidget<?> toggleButton(BooleanSyncValue sync, String tooltipKey, UITexture icon) {
        return new StationButtonWidget(IKey.lang(tooltipKey))
            .overlay(
                new DynamicDrawable(
                    () -> icon.withColorOverride(
                        sync.getBoolValue() ? 0xFFFFFFFF : EnumColors.MAP_COLOR_TEXT_MUTED.getColor())))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                sync.setBoolValue(!sync.getBoolValue(), true, true);
                return true;
            });
    }
}
