package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record RiderPartDef(int id, String name, int width, int height, int weight, int riderCapacity,
    String assetFolder) implements IRocketPartDef {}
