package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;

public record StationModuleAlert(Severity severity, String title, String message, @Nullable ResourceLocation icon) {

    public StationModuleAlert {
        Objects.requireNonNull(severity, "severity");
        title = Objects.requireNonNull(title, "title");
        message = Objects.requireNonNull(message, "message");
    }

    public static StationModuleAlert warning(String title, String message, @Nullable ResourceLocation icon) {
        return new StationModuleAlert(Severity.YELLOW, title, message, icon);
    }

    public static StationModuleAlert critical(String title, String message, @Nullable ResourceLocation icon) {
        return new StationModuleAlert(Severity.RED, title, message, icon);
    }

    public static Map<ModuleInstance.ID, StationModuleAlert> alerts(AutomatedFacility facility) {
        if (facility == null) return Map.of();
        Map<ModuleInstance.ID, StationModuleAlert> result = new LinkedHashMap<>();
        Set<ModuleInstance.ID> unpaid = null;
        for (ModuleInstance module : facility.modules()) {
            StationModuleAlert alert;
            if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE) {
                alert = critical("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_ERROR.get());
            } else {
                if (unpaid == null) {
                    unpaid = Set.copyOf(
                        UpkeepSettlement.preview(
                            facility.upkeepSummary()
                                .moduleDemands(),
                            facility.upkeepCredits(),
                            facility)
                            .unpaidModuleIds());
                }
                if (!unpaid.contains(module.id)) continue;
                alert = warning("Upkeep", "Missing upkeep resources.", EnumTextures.ICON_STATION_ALERT_WARNING.get());
            }
            result.put(module.id, alert);
        }
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }

    public enum Severity {
        YELLOW,
        RED
    }
}
