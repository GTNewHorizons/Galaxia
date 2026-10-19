package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteBandwidthFormatter;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

/** Debug module that makes or uses satellite data, for testing the satellite network */
final class DebugDataGeneratorConfigWindow implements ModuleConfigWindow {

    private static final int WIDE = 154;
    private static final int STEP = 34;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    DebugDataGeneratorConfigWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
    }

    @Override
    public String title() {
        return StatCollector.translateToLocal("galaxia.gui.station.debug_data.title");
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        column.add(
            GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.debug_data.none"))
                .setEnabledIf(w -> module() == null));
        column.add(GalaxiaUi.button(this::modeLabel, WIDE, () -> module() != null, this::toggleMode));
        column.add(GalaxiaUi.button(this::dataTypeLabel, WIDE, () -> module() != null, this::cycleDataType));
        column.add(
            GalaxiaUi.button(
                this::originLabel,
                WIDE,
                () -> module() != null && config().mode() == ModuleDebugDataGenerator.Mode.CONSUME,
                this::cycleOrigin));
        column.row(
            GalaxiaUi.button(() -> "-10", STEP, () -> module() != null, () -> adjustAmount(-10L)),
            GalaxiaUi.text(this::amountLine),
            GalaxiaUi.button(() -> "+10", STEP, () -> module() != null, () -> adjustAmount(10L)));
        column.row(
            GalaxiaUi.button(() -> "-20t", STEP, () -> module() != null, () -> adjustDuration(-20)),
            GalaxiaUi.text(this::durationLine),
            GalaxiaUi.button(() -> "+20t", STEP, () -> module() != null, () -> adjustDuration(20)));
        column.add(GalaxiaUi.text(() -> module() == null ? "" : progressLine()));
        column.add(
            GalaxiaUi.colored(
                () -> module() == null ? "" : consumedLine(),
                com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle.TEXT_DIM));
        column.add(
            GalaxiaUi.colored(
                () -> module() == null ? "" : counterpartStatus(module()),
                com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle.TEXT_DIM));
    }

    private String amountLine() {
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.debug_data.amount",
            SatelliteBandwidthFormatter.formatDataDeciKb(SatelliteBandwidthFormatter.kilobits(config().amountKb())));
    }

    private String durationLine() {
        return StatCollector
            .translateToLocalFormatted("galaxia.gui.station.debug_data.duration", config().durationTicks());
    }

    private String progressLine() {
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.debug_data.progress",
            module().jobProgressTicks(),
            config().durationTicks());
    }

    private String consumedLine() {
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.debug_data.consumed",
            SatelliteBandwidthFormatter.formatDataDeciKb(module().consumedDeciKb()));
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
        if (origin == null) return StatCollector.translateToLocal("galaxia.gui.station.debug_data.any_origin");
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
        CelestialClient.updateDebugDataGeneratorConfig(assetId, controller.moduleId(), config);
    }

    private ModuleDebugDataGenerator.Config config() {
        ModuleDebugDataGenerator generator = module();
        return generator == null ? ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 20)
            : generator.config();
    }

    private @Nullable ModuleDebugDataGenerator module() {
        ModuleInstance module = StationUiData.module(assetId, controller.moduleId());
        return module != null && module.component() instanceof ModuleDebugDataGenerator generator ? generator : null;
    }

    private String counterpartStatus(ModuleDebugDataGenerator generator) {
        CelestialObjectKey bodyKey = generator.detectedCounterpartBodyKey();
        String role = StatCollector.translateToLocal(
            generator.isProducer() ? "galaxia.gui.station.debug_data.consumer"
                : "galaxia.gui.station.debug_data.producer");
        if (bodyKey == null)
            return StatCollector.translateToLocalFormatted("galaxia.gui.station.debug_data.not_detected", role);
        String label = bodyKey.isRegistered() ? bodyKey.registeredBodyId()
            .name() : bodyKey.toString();
        return StatCollector.translateToLocalFormatted("galaxia.gui.station.debug_data.detected", role, label);
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
