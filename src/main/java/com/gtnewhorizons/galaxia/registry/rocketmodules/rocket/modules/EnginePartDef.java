package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record EnginePartDef(int id, String name, int width, int height, int weight, double thrust, String assetFolder)
    implements IRocketPartDef {}
