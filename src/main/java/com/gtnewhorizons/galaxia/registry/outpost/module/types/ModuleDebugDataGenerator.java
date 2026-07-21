package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteBandwidthFormatter;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

public final class ModuleDebugDataGenerator extends TieredModuleComponent {

    public static final long MAX_AMOUNT_KB = Long.MAX_VALUE / 10L;

    public enum Mode {
        PRODUCE,
        CONSUME
    }

    public record Config(Mode mode, boolean enabled, SatelliteDataType dataType, long amountKb, int durationTicks,
        CelestialObjectKey originBodyId) {

        public Config {
            mode = mode == null ? Mode.PRODUCE : mode;
            enabled = true;
            dataType = dataType == null ? SatelliteDataType.PROSPECTING : dataType;
            amountKb = Math.min(MAX_AMOUNT_KB, Math.max(0L, amountKb));
            durationTicks = Math.max(1, durationTicks);
        }

        public static Config produce(SatelliteDataType dataType, long amountKb, int durationTicks) {
            return new Config(Mode.PRODUCE, true, dataType, amountKb, durationTicks, null);
        }

        public static Config consume(SatelliteDataType dataType, long amountKb, int durationTicks,
            CelestialObjectKey originBodyId) {
            return new Config(Mode.CONSUME, true, dataType, amountKb, durationTicks, originBodyId);
        }
    }

    private Config config = Config.produce(SatelliteDataType.PROSPECTING, 10L, 20);
    private int jobProgressTicks;
    private long consumedDeciKb;
    private CelestialObjectKey detectedCounterpartBodyId;

    public Config config() {
        return config;
    }

    public void configure(Config config) {
        this.config = config == null ? Config.produce(SatelliteDataType.PROSPECTING, 10L, 20) : config;
        this.jobProgressTicks = 0;
    }

    public void restore(Config config, int jobProgressTicks, long consumedDeciKb,
        CelestialObjectKey detectedCounterpartBodyId) {
        this.config = config == null ? Config.produce(SatelliteDataType.PROSPECTING, 10L, 20) : config;
        this.jobProgressTicks = Math.max(0, jobProgressTicks);
        this.consumedDeciKb = Math.max(0L, consumedDeciKb);
        this.detectedCounterpartBodyId = detectedCounterpartBodyId;
    }

    public int jobProgressTicks() {
        return jobProgressTicks;
    }

    public long consumedDeciKb() {
        return consumedDeciKb;
    }

    public CelestialObjectKey detectedCounterpartBodyId() {
        return detectedCounterpartBodyId;
    }

    public void updateDetectedCounterpart(CelestialObjectKey bodyId) {
        this.detectedCounterpartBodyId = bodyId;
    }

    public boolean isProducer() {
        return config.mode() == Mode.PRODUCE;
    }

    public boolean isConsumer() {
        return config.mode() == Mode.CONSUME;
    }

    public boolean enabled() {
        return config.enabled();
    }

    public SatelliteDataKey producedKey(CelestialObjectKey sourceBodyKey) {
        if (config.dataType() == SatelliteDataType.PROSPECTING) {
            return SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, sourceBodyKey);
        }
        return SatelliteDataKey.any(config.dataType());
    }

    public SatelliteDataKey demandKey() {
        if (config.originBodyId() != null) {
            return SatelliteDataKey.origin(config.dataType(), config.originBodyId());
        }
        return SatelliteDataKey.any(config.dataType());
    }

    public long amountDeciKb() {
        return SatelliteBandwidthFormatter.kilobits(config.amountKb());
    }

    public void advanceJob() {
        jobProgressTicks++;
    }

    public boolean jobComplete() {
        return jobProgressTicks >= config.durationTicks();
    }

    public void clearJob() {
        jobProgressTicks = 0;
    }

    public void consume(long deciKb) {
        if (deciKb > 0L) consumedDeciKb += deciKb;
    }

    @Override
    public int cooldownTicks(ModuleInstance module, ModuleTierData data) {
        return 1;
    }
}
