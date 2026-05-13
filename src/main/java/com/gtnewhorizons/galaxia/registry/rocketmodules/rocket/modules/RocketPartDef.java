package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.ResourceLocation;

public class RocketPartDef {

    public record EnginePartDef(int id, String name, double width, double height, double weight, double thrust,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record DecouplerPartDef(int id, String name, double width, double height, double weight, int decouplerStage,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record CapsulePartDef(int id, String name, double width, double height, double weight,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record FuelTankPartDef(int id, String name, double width, double height, double weight, double fuelCapacity,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record StructuralPartDef(int id, String name, double width, double height, double weight,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record RiderPartDef(int id, String name, double width, double height, double weight, int riderCapacity,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record LanderPartDef(int id, String name, double width, double height, double weight,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}

    public record FunctionalPartDef(int id, String name, double width, double height, double weight,
        ResourceLocation modelLocation, ResourceLocation textureLocation) implements IRocketPartDef {}
}
