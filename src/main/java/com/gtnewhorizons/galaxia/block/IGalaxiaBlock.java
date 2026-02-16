package com.gtnewhorizons.galaxia.block;

/**
 * This mostly exists right now to simplify the registering process by allowing
 * the registry to get the block name from the object so it only needs to be written once.
 * Maybe there is a better solution than this.
 */
public interface IGalaxiaBlock {

    String getBlockName();
}
