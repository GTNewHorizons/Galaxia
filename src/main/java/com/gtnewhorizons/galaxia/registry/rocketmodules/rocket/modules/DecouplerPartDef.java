package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record DecouplerPartDef(int id, String name, int width, int height, int weight, int decouplerStage,
    String assetFolder) implements IRocketPartDef {}
