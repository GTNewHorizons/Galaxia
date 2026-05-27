package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

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

    public enum Severity {
        YELLOW,
        RED
    }
}
