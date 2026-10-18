package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel;

/**
 * How demanding a fuel is to store. A tank frame can hold every fuel up to its own containment level
 */
public enum FuelContainment {

    /** Room temperature liquids */
    BASIC,
    /** Cryogenic liquids that need insulated cores */
    CRYOGENIC,
    /** Fuels that need active containment fields */
    EXOTIC;

    public boolean canHold(FuelContainment required) {
        return required.ordinal() <= ordinal();
    }
}
