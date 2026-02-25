package com.gtnewhorizons.galaxia.client.gui.rocket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.gtnewhorizons.galaxia.registry.block.tileentities.TileSiloController;

public class ContainerSilo extends Container {

    public final TileSiloController tile;

    public ContainerSilo(TileSiloController tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return true;
    }
}
