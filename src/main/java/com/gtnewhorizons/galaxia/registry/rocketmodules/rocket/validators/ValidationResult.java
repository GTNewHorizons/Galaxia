package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.validators;

public record ValidationResult(boolean valid, String message) {

    public static ValidationResult success() {
        return new ValidationResult(true, "");
    }
}
