package com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier;

/**
 * Entry for controlling terrain generation with a modifier interval
 * @param modifier Modifier to measure against
 * @param lowerRange Minimum value of the interval
 * @param upperRange Maximum value of the interval
 */
public record TerrainModifierEntry(TerrainModifier modifier, double lowerRange, double upperRange) {}
