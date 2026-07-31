package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteBandwidthFormatter;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

final class DebugDataGeneratorConfigModalWidget extends ParentWidget<DebugDataGeneratorConfigModalWidget> {

    static final int WIDTH = 236;
    static final int HEIGHT = 208;

    private static final int LEFT = 8;
    private static final int BODY_TOP = 32;
    private static final int BUTTON_W = 74;
    private static final int BUTTON_H = 18;
    private static final int SMALL_W = 24;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    DebugDataGeneratorConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        overlay(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> draw()));
        addButtons();
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    private void addButtons() {
        child(
            ModuleConfigModalSupport.button(() -> module() != null, this::modeLabel, this::toggleMode)
                .pos(LEFT, BODY_TOP)
                .size(BUTTON_W, BUTTON_H));
        child(
            ModuleConfigModalSupport.button(() -> module() != null, this::dataTypeLabel, this::cycleDataType)
                .pos(LEFT, BODY_TOP + 28)
                .size(BUTTON_W * 2 + 6, BUTTON_H));
        child(
            ModuleConfigModalSupport
                .button(
                    () -> module() != null && config().mode() == ModuleDebugDataGenerator.Mode.CONSUME,
                    this::originLabel,
                    this::cycleOrigin)
                .pos(LEFT, BODY_TOP + 56)
                .size(BUTTON_W * 2 + 6, BUTTON_H));
        child(
            ModuleConfigModalSupport.button(() -> module() != null, "-10", () -> adjustAmount(-10L))
                .pos(LEFT, BODY_TOP + 90)
                .size(SMALL_W, BUTTON_H));
        child(
            ModuleConfigModalSupport.button(() -> module() != null, "+10", () -> adjustAmount(10L))
                .pos(LEFT + 126, BODY_TOP + 90)
                .size(SMALL_W + 10, BUTTON_H));
        child(
            ModuleConfigModalSupport.button(() -> module() != null, "-20t", () -> adjustDuration(-20))
                .pos(LEFT, BODY_TOP + 118)
                .size(SMALL_W + 10, BUTTON_H));
        child(
            ModuleConfigModalSupport.button(() -> module() != null, "+20t", () -> adjustDuration(20))
                .pos(LEFT + 126, BODY_TOP + 118)
                .size(SMALL_W + 10, BUTTON_H));
    }

    private void draw() {
        ModuleConfigModalSupport.drawFrame("Debug Data Generator", WIDTH, HEIGHT);
        ModuleDebugDataGenerator generator = module();
        if (generator == null) {
            ModuleConfigModalSupport
                .drawLine("No module selected", LEFT, BODY_TOP, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        ModuleDebugDataGenerator.Config config = generator.config();
        int y = BODY_TOP + 88;
        ModuleConfigModalSupport.drawCenteredLine(
            "Amount: "
                + SatelliteBandwidthFormatter.formatDataDeciKb(SatelliteBandwidthFormatter.kilobits(config.amountKb())),
            82,
            y,
            88,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawCenteredLine(
            "Duration: " + config.durationTicks() + "t",
            82,
            y + 28,
            88,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            "Progress: " + generator.jobProgressTicks() + "/" + config.durationTicks() + "t",
            LEFT,
            y + 54,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            "Consumed: " + SatelliteBandwidthFormatter.formatDataDeciKb(generator.consumedDeciKb()),
            LEFT,
            y + 66,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport
            .drawLine(counterpartStatus(generator), LEFT, y + 78, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private String modeLabel() {
        return config().mode()
            .name();
    }

    private String dataTypeLabel() {
        return config().dataType()
            .name();
    }

    private String originLabel() {
        CelestialObjectKey origin = config().originBodyKey();
        if (origin == null) return "Any origin";
        return origin.isRegistered() ? origin.registeredBodyId()
            .name() : origin.toString();
    }

    private void toggleMode() {
        ModuleDebugDataGenerator.Config config = config();
        ModuleDebugDataGenerator.Mode mode = config.mode() == ModuleDebugDataGenerator.Mode.PRODUCE
            ? ModuleDebugDataGenerator.Mode.CONSUME
            : ModuleDebugDataGenerator.Mode.PRODUCE;
        apply(
            new ModuleDebugDataGenerator.Config(
                mode,
                true,
                config.dataType(),
                config.amountKb(),
                config.durationTicks(),
                mode == ModuleDebugDataGenerator.Mode.CONSUME ? config.originBodyKey() : null));
    }

    private void cycleDataType() {
        ModuleDebugDataGenerator.Config config = config();
        SatelliteDataType[] values = SatelliteDataType.values();
        SatelliteDataType next = values[(config.dataType()
            .ordinal() + 1) % values.length];
        apply(
            new ModuleDebugDataGenerator.Config(
                config.mode(),
                true,
                next,
                config.amountKb(),
                config.durationTicks(),
                config.originBodyKey()));
    }

    private void cycleOrigin() {
        ModuleDebugDataGenerator.Config config = config();
        CelestialObjectKey next = nextOrigin(config.originBodyKey());
        apply(
            new ModuleDebugDataGenerator.Config(
                config.mode(),
                true,
                config.dataType(),
                config.amountKb(),
                config.durationTicks(),
                next));
    }

    private void adjustAmount(long delta) {
        ModuleDebugDataGenerator.Config config = config();
        apply(
            new ModuleDebugDataGenerator.Config(
                config.mode(),
                true,
                config.dataType(),
                Math.max(0L, config.amountKb() + delta),
                config.durationTicks(),
                config.originBodyKey()));
    }

    private void adjustDuration(int delta) {
        ModuleDebugDataGenerator.Config config = config();
        apply(
            new ModuleDebugDataGenerator.Config(
                config.mode(),
                true,
                config.dataType(),
                config.amountKb(),
                Math.max(1, config.durationTicks() + delta),
                config.originBodyKey()));
    }

    private void apply(ModuleDebugDataGenerator.Config config) {
        int moduleIndex = controller.moduleIndex();
        if (moduleIndex < 0) return;
        ModuleDebugDataGenerator generator = module();
        if (generator != null) generator.configure(config);
        CelestialClient.updateDebugDataGeneratorConfig(assetId, moduleIndex, config);
    }

    private ModuleDebugDataGenerator.Config config() {
        ModuleDebugDataGenerator generator = module();
        return generator == null ? ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 20)
            : generator.config();
    }

    private @Nullable ModuleDebugDataGenerator module() {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, controller.moduleId());
        return module != null && module.component() instanceof ModuleDebugDataGenerator generator ? generator : null;
    }

    private String counterpartStatus(ModuleDebugDataGenerator generator) {
        CelestialObjectKey bodyKey = generator.detectedCounterpartBodyKey();
        String role = generator.isProducer() ? "Consumer" : "Producer";
        if (bodyKey == null) return role + " not detected";
        String label = bodyKey.isRegistered() ? bodyKey.registeredBodyId()
            .name() : bodyKey.toString();
        return role + " detected at: " + label;
    }

    private static @Nullable CelestialObjectKey nextOrigin(@Nullable CelestialObjectKey current) {
        CelestialObjectId[] values = CelestialObjectId.values();
        CelestialObjectId currentId = current != null && current.isRegistered() ? current.registeredBodyId() : null;
        if (currentId == null) {
            return values.length == 0 ? null : CelestialObjectKey.registered(values[0]);
        }
        int nextOrdinal = currentId.ordinal() + 1;
        return nextOrdinal >= values.length ? null : CelestialObjectKey.registered(values[nextOrdinal]);
    }
}
