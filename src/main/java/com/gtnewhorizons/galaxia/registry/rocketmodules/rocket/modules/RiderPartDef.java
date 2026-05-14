package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record RiderPartDef(int id, String name, double width, double height, double weight, int riderCapacity,
    String assetFolder) implements IRocketPartDef {}
