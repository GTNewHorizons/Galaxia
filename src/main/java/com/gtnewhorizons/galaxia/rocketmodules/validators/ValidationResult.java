package com.gtnewhorizons.galaxia.rocketmodules.validators;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ValidationResult(boolean valid, String message) {

    public static ValidationResult success() {
        return new ValidationResult(true, "");
    }
}
