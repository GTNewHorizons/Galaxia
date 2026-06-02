package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class GalacticMapWidget extends ParentWidget<GalacticMapWidget> {

    private static final int SIGNALS_X = 10;
    private static final int TOP_BUTTON_Y = 10;
    private static final int SIGNALS_W = 66;
    private static final int TOP_BUTTON_H = 16;
    private static final int TRANSFERS_X = 82;
    private static final int TRANSFERS_W = 92;
    private static final int ASSETS_X = 180;
    private static final int ASSETS_W = 66;

    private final OrbitalView.OrbitalMapWidget mapWidget;
    private final ButtonWidget<?> signalsButton;
    private final ButtonWidget<?> transferVisibilityButton;
    private final ButtonWidget<?> assetsPanelButton;
    private String signalsLabel = "Signals";
    private String transfersLabel = "Hide Transfers";
    private String assetsLabel = "Assets";

    public GalacticMapWidget(CelestialObject galaxyRoot, CelestialObject initialLayer, TextFieldWidget renameField) {
        this.mapWidget = new OrbitalView.OrbitalMapWidget(galaxyRoot).withInitialLayer(initialLayer)
            .attachRenameField(renameField);
        this.signalsButton = createTopBarButton(() -> signalsLabel, () -> {
            mapWidget.toggleSignals();
            updateTopBarLabels();
        }).pos(SIGNALS_X, TOP_BUTTON_Y)
            .size(SIGNALS_W, TOP_BUTTON_H);
        this.transferVisibilityButton = createTopBarButton(() -> transfersLabel, () -> {
            mapWidget.toggleTransfersHidden();
            updateTopBarLabels();
        }).pos(TRANSFERS_X, TOP_BUTTON_Y)
            .size(TRANSFERS_W, TOP_BUTTON_H);
        this.assetsPanelButton = createTopBarButton(() -> assetsLabel, () -> {
            mapWidget.toggleAssetsPanel();
            updateTopBarLabels();
        }).pos(ASSETS_X, TOP_BUTTON_Y)
            .size(ASSETS_W, TOP_BUTTON_H);

        child(
            (IWidget) mapWidget.left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f));
        child(
            (IWidget) mapWidget.createPinnedInfoWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(
            (IWidget) mapWidget.createTransferTooltipWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(
            (IWidget) mapWidget.createTransferSimulatorWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(
            (IWidget) mapWidget.createContextMenuWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(
            (IWidget) mapWidget.createAssetActionsWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(signalsButton);
        child(
            (IWidget) mapWidget.createSignalsWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
        child(transferVisibilityButton);
        child(assetsPanelButton);
        child(
            (IWidget) mapWidget.createAssetsPanelWidget()
                .left(0)
                .top(0)
                .width(1)
                .height(1));
    }

    public OrbitalView.OrbitalMapWidget mapWidget() {
        return mapWidget;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        updateTopBarLabels();
    }

    private void updateTopBarLabels() {
        signalsLabel = mapWidget.isSignalsOpen() ? "Signals \u25b2" : "Signals";
        transfersLabel = mapWidget.areTransfersHidden() ? "Show Transfers" : "Hide Transfers";
        assetsLabel = mapWidget.isAssetsPanelOpen() ? "Assets \u25b2" : "Assets";
    }

    private ButtonWidget<?> createTopBarButton(Supplier<String> labelSupplier, Runnable onClick) {
        return new ButtonWidget<>().background(drawable((ctx, x, y, w, h) -> {
            Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor());
            Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
        }))
            .hoverBackground(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor());
                Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .overlay(IKey.dynamic(labelSupplier::get))
            .onMousePressed(mb -> {
                onClick.run();
                return true;
            });
    }

    private IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
