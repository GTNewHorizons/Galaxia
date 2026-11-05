package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;

public interface IStationBehavior {

    String getUnlocalizedName();

    /**
     * Builds the room shape check
     *
     * @param stationBlocks Wall blocks the dimension allows stations to be built from, empty where stations are not
     *                      allowed at all
     *
     * @return The structure definition of the room
     */
    IStructureDefinition<TileStation> buildStructureDefinition(List<Block> stationBlocks);

    int getSearchRadius();

    default void onStructureFormed(TileStation station) {}

    default void onStructureDisformed(TileStation station) {}

    default void tickPostBoot(TileStation station) {}

    default void onGraphRebuilt(TileStation station) {}

    List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset);

    default void writeToNBT(TileStation station, NBTTagCompound nbt) {}

    default void readFromNBT(TileStation station, NBTTagCompound nbt) {}
}
