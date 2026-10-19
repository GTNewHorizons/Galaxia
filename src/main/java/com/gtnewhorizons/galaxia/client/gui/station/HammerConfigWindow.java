package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

/** When a hammer shoots: always, or only under a delta-v or flight time limit, and which of the two it prefers */
final class HammerConfigWindow implements ModuleConfigWindow {

    private static final int STATUS_WIDTH = 260;
    private static final int MODE_WIDTH = 50;
    private static final int PRIORITY_WIDTH = 34;
    private static final int STEP_WIDTH = 14;
    private static final int LIMIT_WIDTH = 64;
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[0-9]*(\\.[0-9]*)?");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private @Nullable HammerDispatchStatus.Status dispatchStatus;

    HammerConfigWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
    }

    @Override
    public String title() {
        ModuleInstance module = selectedModule();
        return module == null ? tr("galaxia.gui.station.hammer.title")
            : StationUiData.moduleTitle(module, "galaxia.gui.station.config.purpose.configuration");
    }

    @Override
    public void update() {
        ModuleInstance module = selectedModule();
        dispatchStatus = module == null || !(module.component() instanceof ModuleHammer) ? null
            : CelestialClient.hammerDispatchStatus(assetId, module.id);
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        column.add(
            GalaxiaUi.dim(tr("galaxia.gui.station.hammer.none"))
                .setEnabledIf(w -> selectedHammer() == null));
        column.add(
            GalaxiaUi.text(this::statusLine, this::statusColor)
                .setEnabledIf(w -> dispatchStatus != null));
        column.add(
            GalaxiaUi.colored(this::operationLine, GalaxiaGuiStyle.WARN)
                .setEnabledIf(w -> selectedModule() != null && selectedModule().operationOrNull() != null));

        column.section(tr("galaxia.gui.station.hammer.controls"));
        column.row(
            GalaxiaUi.dim(tr("galaxia.gui.station.hammer.mode")),
            GalaxiaUi.button(this::shootingModeLabel, MODE_WIDTH, this::canConfigureHammer, this::cycleShootingMode),
            GalaxiaUi.dim(tr("galaxia.gui.station.hammer.priority")),
            GalaxiaUi
                .button(this::routePriorityLabel, PRIORITY_WIDTH, this::canConfigureHammer, this::toggleRoutePriority));
        column.row(
            GalaxiaUi.dim(tr("galaxia.gui.station.hammer.limit")),
            GalaxiaUi.button(() -> "-", STEP_WIDTH, this::canUseThresholdField, this::decrementThreshold),
            thresholdField(),
            GalaxiaUi.button(() -> "+", STEP_WIDTH, this::canUseThresholdField, this::incrementThreshold),
            GalaxiaUi.text(this::unitLabel));
        column.add(
            GalaxiaUi.button(tr("galaxia.gui.station.items"), () -> selectedModule() != null, this::openLogistics)
                .name("hammer.items"));
    }

    private String statusLine() {
        HammerDispatchStatus.Status status = dispatchStatus;
        return status == null ? ""
            : GalaxiaUi.clip(ModuleStatusTextRegistry.hammerDispatchStatusLine(status), STATUS_WIDTH);
    }

    private int statusColor() {
        HammerDispatchStatus.Status status = dispatchStatus;
        return status != null && status.code() == HammerDispatchStatus.Code.READY ? GalaxiaGuiStyle.TEXT_TITLE
            : GalaxiaGuiStyle.WARN;
    }

    private String operationLine() {
        ModuleInstance module = selectedModule();
        if (module == null || module.operationOrNull() == null) return "";
        return StationUiData.operationLabel(assetId, module, controller.isModuleOperationCancelArmed());
    }

    private void openLogistics() {
        if (selectedModule() == null) return;
        controller.openLogistics(controller.moduleId());
    }

    private TextFieldWidget thresholdField() {
        TextFieldWidget field = GalaxiaUi.textField(null, LIMIT_WIDTH);
        field.setMaxLength(12)
            .setPattern(DECIMAL_PATTERN)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .value(new StringValue.Dynamic(this::thresholdText, this::setThresholdText))
            .setFocusOnGuiOpen(false);
        return field;
    }

    private boolean canConfigureHammer() {
        return selectedHammer() != null;
    }

    private boolean canUseThresholdField() {
        ModuleHammer hammer = selectedHammer();
        return hammer != null && hammer.config()
            .mode() != AllowShootingConfig.Mode.ALWAYS;
    }

    private String shootingModeLabel() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return tr("galaxia.gui.station.hammer.mode");
        return tr(
            switch (hammer.config()
                .mode()) {
            case ALWAYS -> "galaxia.gui.station.hammer.mode.always";
            case WHEN_DV_UNDER -> "galaxia.gui.station.hammer.mode.dv";
            case WHEN_TOF_UNDER -> "galaxia.gui.station.hammer.mode.tof";
            });
    }

    private String routePriorityLabel() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return tr("galaxia.gui.station.hammer.priority");
        return tr(
            hammer.routePriority() == OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV
                ? "galaxia.gui.station.hammer.priority.dv"
                : "galaxia.gui.station.hammer.priority.tof");
    }

    /** Unit of the limit; Always has no limit and so no unit */
    private String unitLabel() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return "";
        return switch (hammer.config()
            .mode()) {
            case ALWAYS -> "";
            case WHEN_DV_UNDER -> tr("galaxia.gui.station.hammer.unit.dv");
            case WHEN_TOF_UNDER -> tr("galaxia.gui.station.hammer.unit.tof");
        };
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
        CelestialClient.configureHammer(
            assetId,
            controller.moduleId(),
            new AllowShootingConfig(
                next,
                hammer.config()
                    .threshold()),
            hammer.routePriority());
    }

    private void toggleRoutePriority() {
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return;
        CelestialClient.configureHammer(
            assetId,
            controller.moduleId(),
            hammer.config(),
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
        ModuleHammer hammer = selectedHammer();
        if (hammer == null) return;
        CelestialClient.configureHammer(
            assetId,
            controller.moduleId(),
            new AllowShootingConfig(
                hammer.config()
                    .mode(),
                value),
            hammer.routePriority());
    }

    private ModuleHammer selectedHammer() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof ModuleHammer hammer ? hammer : null;
    }

    private ModuleInstance selectedModule() {
        return StationUiData.module(assetId, controller.moduleId());
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
