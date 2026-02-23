package com.gtnewhorizons.galaxia.utility;

import net.minecraft.block.Block;

import com.github.bsideup.jabel.Desugar;

/**
 * Basic record to hold block meta-data
 * 
 * @param block The block on which to hold the meta-data
 * @param meta  The meta-data to be held
 */
@Desugar
public record BlockMeta(Block block, int meta) {}
