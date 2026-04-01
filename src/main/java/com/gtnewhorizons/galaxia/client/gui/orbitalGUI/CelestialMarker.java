package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialMarker(String id, ResourceLocation texture, float alpha) {}
