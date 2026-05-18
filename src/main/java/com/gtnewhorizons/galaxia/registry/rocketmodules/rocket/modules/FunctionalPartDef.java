package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record FunctionalPartDef(int id, String name, int width, int height, int weight, String assetFolder)
    implements IRocketPartDef {}
