package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record FuelTankPartDef(int id, String name, int width, int height, int weight, double fuelCapacity,
    String assetFolder) implements IRocketPartDef {}
