package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.client.gui.rocket.ContainerSilo;
import com.gtnewhorizons.galaxia.client.gui.rocket.GuiRocket;
import com.gtnewhorizons.galaxia.client.gui.rocket.GuiSilo;
import com.gtnewhorizons.galaxia.registry.block.tileentities.TileSiloController;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_SILO = 0;
    public static final int GUI_ROCKET = 1;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SILO) {
            TileSiloController tile = (TileSiloController) world.getTileEntity(x, y, z);
            return new ContainerSilo(tile);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SILO) {
            TileSiloController tile = (TileSiloController) world.getTileEntity(x, y, z);
            return new GuiSilo(new ContainerSilo(tile));
        }
        if (ID == GUI_ROCKET) {
            Entity e = world.getEntityByID(x); // x = entityId
            if (e instanceof RocketEntity) {
                return new GuiRocket((RocketEntity) e);
            }
        }
        return null;
    }
}
