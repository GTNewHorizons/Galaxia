package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.SiloAutomation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * Phantom inventory behind the request list: each slot is the item template of one request
 */
public class RequestTemplateHandler extends ItemStackHandler {

    private final TileEntitySilo silo;

    public RequestTemplateHandler(TileEntitySilo silo) {
        super(SiloAutomation.MAX_REQUESTS);
        this.silo = silo;
    }

    private SiloAutomation.Request request(int slot) {
        return silo.getAutomation()
            .getRequests()
            .get(slot);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return request(slot).getTemplate();
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        SiloAutomation.Request request = request(slot);
        boolean changed = !ItemStack.areItemStacksEqual(request.getTemplate(), stack);
        request.setTemplate(stack);
        // A different item starts its count from zero
        if (changed) request.reset();
        silo.markDirty();
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
