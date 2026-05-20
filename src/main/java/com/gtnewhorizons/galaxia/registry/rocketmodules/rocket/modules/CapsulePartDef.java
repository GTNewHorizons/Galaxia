package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

public record CapsulePartDef(int id, String name, int width, int height, int weight, String assetFolder)
    implements IRocketPartDef {}
