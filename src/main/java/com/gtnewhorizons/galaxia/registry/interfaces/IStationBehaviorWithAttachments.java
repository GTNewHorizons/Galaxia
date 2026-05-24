package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;

public interface IStationBehaviorWithAttachments extends IStationBehavior {

    default void onAttachmentsChanged(TileStation station, BlockPos pos, boolean added) {
        if (!added) return;
        StationGraph graph = station.getGraph();
        if (graph == null) return;
        if (pos.getTE(station.getWorldObj()) instanceof IStationAttachment<?>attachment) {
            graph.registerAttachment(station.getHere(), pos, attachment);
        }
    }

    default void registerAttachments(TileStation station, StationGraph graph) {
        for (BlockPos pos : station.getAttachments()) {
            if (pos.getTE(station.getWorldObj()) instanceof IStationAttachment<?>attachment) {
                graph.registerAttachment(station.getHere(), pos, attachment);
            }
        }
    }

    @Override
    default void tickPostBoot(TileStation station) {
        StationGraph graph = station.getGraph();
        if (graph == null) return;

        boolean changed = false;
        List<BlockPos> attachments = station.getAttachments();
        for (int i = attachments.size(); --i >= 0;) {
            BlockPos pos = attachments.get(i);
            TileEntity te = pos.getTE(station.getWorldObj());
            if (!(te instanceof IStationAttachment)
                || (te instanceof GalaxiaBootableMultiblock<?>base && !base.isStructureValid())) {
                graph.removeAttachment(pos);
                changed = true;
            }
        }
        registerAttachments(station, graph);
        if (changed) station.markDirty();
    }

    @Override
    default void writeToNBT(TileStation station, NBTTagCompound nbt) {
        nbt.setTag("attachments", BlockPos.listToNBT(station.getAttachments()));
    }

    @Override
    default void readFromNBT(TileStation station, NBTTagCompound nbt) {
        if (!nbt.hasKey("attachments")) return;
        station.setAttachments(BlockPos.listFromNBT(nbt.getTagList("attachments", Constants.NBT.TAG_COMPOUND)));
    }
}
