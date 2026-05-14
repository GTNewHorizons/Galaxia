package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record CapsulePartDef(int id, String name, double width, double height, double weight, String assetFolder)
    implements IRocketPartDef {}
