package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record EnginePartDef(int id, String name, double width, double height, double weight, double thrust,
    String assetFolder) implements IRocketPartDef {}
