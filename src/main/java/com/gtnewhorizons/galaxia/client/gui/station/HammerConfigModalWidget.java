package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Locale;
import java.util.regex.Pattern;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class HammerConfigModalWidget extends ParentWidget<HammerConfigModalWidget> {

    static final int WIDTH = 320;
    static final int HEIGHT = 220;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int ITEMS_BUTTON_WIDTH = 54;
    private static final int CLOSE_BUTTON_X = WIDTH - 62;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int CONTROL_BUTTON_HEIGHT = 20;
    private static final int MODE_BUTTON_X = 58;
    private static final int MODE_BUTTON_Y = 84;
    private static final int MODE_BUTTON_WIDTH = 94;
    private static final int PRIORITY_BUTTON_X = 228;
    private static final int PRIORITY_BUTTON_Y = 84;
    private static final int PRIORITY_BUTTON_WIDTH = 76;
    private static final int LIMIT_BUTTON_Y = 112;
    private static final int LIMIT_MINUS_X = 58;
    private static final int LIMIT_FIELD_X = 84;
    private static final int LIMIT_PLUS_X = 160;
    private static final int LIMIT_FIELD_WIDTH = 70;
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[0-9]*(\\.[0-9]*)?");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    HammerConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(controller::isHammerOpen, "Items", this::openLogistics)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(ITEMS_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isHammerOpen, "Close", controller::close)
                .pos(CLOSE_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfigureHammer, this::shootingModeLabel, this::cycleShootingMode)
                .pos(MODE_BUTTON_X, MODE_BUTTON_Y)
                .size(MODE_BUTTON_WIDTH, CONTROL_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(this::canConfigureHammer, this::routePriorityLabel, this::toggleRoutePriority)
                .pos(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y)
                .size(PRIORITY_BUTTON_WIDTH, CONTROL_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canUseThresholdField, "-", this::decrementThreshold)
                .pos(LIMIT_MINUS_X, LIMIT_BUTTON_Y)
                .size(20, CONTROL_BUTTON_HEIGHT));
        child(
            thresholdField().pos(LIMIT_FIELD_X, LIMIT_BUTTON_Y)
                .size(LIMIT_FIELD_WIDTH, CONTROL_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canUseThresholdField, "+", this::incrementThreshold)
                .pos(LIMIT_PLUS_X, LIMIT_BUTTON_Y)
                .size(20, CONTROL_BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isHammerOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) {
            ModuleConfigModalSupport.drawLine(
                "No hammer selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        HammerDispatchStatus.Status status = HammerDispatchStatus
            .evaluate(facility, module, CelestialClient.allOutposts(), GalaxiaCelestialAPI.currentOrbitalTime());
        int y = ModuleConfigModalSupport.drawTrimmedLine(
            dispatchStatusLine(status),
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP,
            WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2,
            status.code() == HammerDispatchStatus.Code.READY ? EnumColors.MAP_COLOR_TEXT_TITLE.getColor()
                : EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        if (module.operationOrNull() != null) {
            ModuleConfigModalSupport.drawLine(
                operationLabel(module),
                ModuleConfigModalSupport.PANEL_PADDING,
                y,
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
        drawControls(hammer);
    }

    private void openLogistics() {
        if (selectedModule() == null) return;
        controller.openLogistics(controller.moduleIndex());
    }

    private boolean hasCancellableOperation() {
        ModuleInstance module = selectedModule();
        return controller.isHammerOpen() && module != null
            && module.operationOrNull() != null
            && !module.operationOrNull()
                .phase()
                .isTerminal();
    }

    private String operationLabel(ModuleInstance module) {
        if (hasCancellableOperation() && controller.isModuleOperationCancelArmed()) {
            return "Click Confirm to cancel operation";
        }
        return switch (module.operationOrNull()
            .phase()) {
            case WAITING_FOR_MATERIALS -> "Module is waiting for materials";
            case BUILDING -> "Module is upgrading";
            case REFUNDING -> ModuleConfigModalSupport.refundBlockedByFullInventory(assetId, module)
                ? "Inventory full; refund paused"
                : "Module is refunding";
            case COMPLETE -> "Upgrade complete";
            case CANCELLED -> "Upgrade cancelled";
        };
    }

    private void drawControls(ModuleHammer hammer) {
        int color = EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
        int titleColor = EnumColors.MAP_COLOR_TEXT_TITLE.getColor();
        ModuleConfigModalSupport.drawLine("Shooting controls", ModuleConfigModalSupport.PANEL_PADDING, 66, titleColor);
        ModuleConfigModalSupport.drawLine("Mode", ModuleConfigModalSupport.PANEL_PADDING, 90, color);
        ModuleConfigModalSupport.drawLine("Priority", 170, 90, color);
        ModuleConfigModalSupport.drawLine("Limit", ModuleConfigModalSupport.PANEL_PADDING, 118, color);
        String unit = switch (hammer.config()
            .mode()) {
            case ALWAYS -> "-";
            case WHEN_DV_UNDER -> "dV";
            case WHEN_TOF_UNDER -> "s";
        };
        ModuleConfigModalSupport.drawLine(unit, LIMIT_PLUS_X + 28, 118, color);
    }

    private TextFieldWidget thresholdField() {
        return new TextFieldWidget().setMaxLength(12)
            .setPattern(DECIMAL_PATTERN)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .value(new StringValue.Dynamic(this::thresholdText, this::setThresholdText))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> canUseThresholdField());
    }

    private boolean canConfigureHammer() {
        return controller.isHammerOpen() && selectedHammer() != null;
    }

    private boolean canUseThresholdField() {
        ModuleHammer hammer = selectedHammer();
        return controller.isHammerOpen() && hammer != null
            && hammer.config()
                .mode() != AllowShootingConfig.Mode.ALWAYS;
    }

    private String shootingModeLabel() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return "Mode";
        return switch (hammer.config()
            .mode()) {
            case ALWAYS -> "Always";
            case WHEN_DV_UNDER -> "dV <";
            case WHEN_TOF_UNDER -> "TOF <";
        };
    }

    private String routePriorityLabel() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return "Priority";
        return hammer.routePriority() == OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV ? "dV" : "TOF";
    }

    private String thresholdText() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null || hammer.config()
            .mode() == AllowShootingConfig.Mode.ALWAYS) return "";
        double value = hammer.config()
            .threshold();
        if (!Double.isFinite(value)) return "0";
        if (hammer.config()
            .mode() == AllowShootingConfig.Mode.WHEN_TOF_UNDER) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void cycleShootingMode() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return;
        AllowShootingConfig.Mode next = switch (hammer.config()
            .mode()) {
            case ALWAYS -> AllowShootingConfig.Mode.WHEN_DV_UNDER;
            case WHEN_DV_UNDER -> AllowShootingConfig.Mode.WHEN_TOF_UNDER;
            case WHEN_TOF_UNDER -> AllowShootingConfig.Mode.ALWAYS;
        };
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_MODE,
            next);
    }

    private void toggleRoutePriority() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return;
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_ROUTE_PRIORITY,
            hammer.routePriority()
                .toggled());
    }

    private void decrementThreshold() {
        adjustThreshold(-thresholdStep());
    }

    private void incrementThreshold() {
        adjustThreshold(thresholdStep());
    }

    private void adjustThreshold(double delta) {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return;
        updateThreshold(
            Math.max(
                0.0,
                hammer.config()
                    .threshold() + delta));
    }

    private double thresholdStep() {
        ModuleHammer hammer = selectedHammer();
        return hammer != null && hammer.config()
            .mode() == AllowShootingConfig.Mode.WHEN_TOF_UNDER ? 3600.0 : 1.0;
    }

    private void setThresholdText(String text) {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null || !canUseThresholdField()) return;
        double parsed = hammer.config()
            .threshold();
        if (text != null && !text.isEmpty() && !".".equals(text)) {
            try {
                parsed = Double.parseDouble(text);
            } catch (NumberFormatException ignored) {}
        }
        updateThreshold(Math.clamp(parsed, 0.0, 999_999_999.0));
    }

    private void updateThreshold(double value) {
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_THRESHOLD,
            value);
    }

    private String dispatchStatusLine(HammerDispatchStatus.Status status) {
        return switch (status.code()) {
            case READY -> "Dispatch: ready";
            case WAITING_FOR_REQUEST -> "Dispatch: waiting for request";
            case NO_EXPORT_CONFIG -> "Dispatch: export disabled";
            case NO_SURPLUS_AFTER_RESERVE -> "Dispatch: no surplus after reserve";
            case ORDER_BELOW_PACKAGE_SIZE -> "Dispatch: order below package size " + status.sendAmount()
                + "/"
                + status.orderSize();
            case DESTINATION_LACKS_PACKAGE_SPACE -> "Dispatch: destination lacks package space " + status.sendAmount()
                + "/"
                + status.orderSize();
            case DESTINATION_CAPACITY_BLOCKED -> "Dispatch: destination full, " + status.sendAmount() + " arrived";
            case NEED_BIG_HAMMER -> "Dispatch: need BIG Hammer";
            case ROUTE_UNAVAILABLE -> "Dispatch: route unavailable";
            case BLOCKED_BY_DV_LIMIT -> "Dispatch: blocked by dV limit";
            case BLOCKED_BY_TOF_LIMIT -> "Dispatch: blocked by TOF limit";
            case NEED_ENERGY -> "Dispatch: need " + ModuleConfigModalSupport.formatEu(status.requiredEnergy())
                + " EU, buffer "
                + ModuleConfigModalSupport.formatEu(status.storedEnergy());
        };
    }

    private ModuleHammer selectedHammer() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof ModuleHammer hammer ? hammer : null;
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Hammer Configuration" : ModuleConfigModalSupport.moduleTitle(module, "Configuration");
    }
}
