package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record FuelTankPartDef(int id, String name, double width, double height, double weight, double fuelCapacity,
    String assetFolder) implements IRocketPartDef {}
