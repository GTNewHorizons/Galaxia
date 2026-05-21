package com.gtnewhorizons.galaxia.registry.block.tile;

import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import java.util.List;

public interface StationBehavior {

    String getName();

    String getUnlocalizedName();

    IStructureDefinition<TileStation> getStructureDefinition();

    int getSearchRadius();

    default void onStructureFormed(TileStation station) {}

    default void onStructureDisformed(TileStation station) {}

    default void tickPostBoot(TileStation station) {}

    default void onGraphRebuilt(TileStation station) {}

    /** Return extra widgets for the behavior-specific section of the GUI, or null. */
    default List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset) {
        return null;
    }

    default void writeToNBT(TileStation station, NBTTagCompound nbt) {}

    default void readFromNBT(TileStation station, NBTTagCompound nbt) {}

    default int getVolume(TileStation station) {
        var def = getStructureDefinition();
        if (def instanceof ArbitraryShapeDefinition<?> asd) {
            return asd.getVolume();
        }
        return 0;
    }
}
