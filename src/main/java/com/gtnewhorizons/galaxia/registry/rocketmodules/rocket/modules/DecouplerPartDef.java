package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record DecouplerPartDef(int id, String name, double width, double height, double weight, int decouplerStage,
    String assetFolder) implements IRocketPartDef {}
