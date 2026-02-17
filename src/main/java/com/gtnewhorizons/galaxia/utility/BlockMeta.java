package com.gtnewhorizons.galaxia.utility;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.Block;

@Desugar
public record BlockMeta(Block block, int meta) {
}
