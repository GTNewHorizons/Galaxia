package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;

public interface IStationBehavior {

    String getUnlocalizedName();

    IStructureDefinition<TileStation> getStructureDefinition();

    int getSearchRadius();

    default void onStructureFormed(TileStation station) {}

    default void onStructureDisformed(TileStation station) {}

    default void tickPostBoot(TileStation station) {}

    default void onGraphRebuilt(TileStation station) {}

    List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset);

    default void writeToNBT(TileStation station, NBTTagCompound nbt) {}

    default void readFromNBT(TileStation station, NBTTagCompound nbt) {}
}
