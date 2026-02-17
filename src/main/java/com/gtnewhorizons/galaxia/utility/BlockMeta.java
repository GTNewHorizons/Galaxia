package com.gtnewhorizons.galaxia.utility;

import net.minecraft.block.Block;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record BlockMeta(Block block, int meta) {}
