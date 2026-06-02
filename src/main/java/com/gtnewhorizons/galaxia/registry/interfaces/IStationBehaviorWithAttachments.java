package com.gtnewhorizons.galaxia.registry.interfaces;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry.ResolvedAttachment;

public interface IStationBehaviorWithAttachments extends IStationBehavior {

    default void onAttachmentsChanged(TileStation station, BlockPos pos, boolean added) {
        if (!added) return;
        StationGraph graph = station.getGraph();
        if (graph == null) return;
        ResolvedAttachment<?> ra = resolveAttachment(station, pos);
        if (ra != null) {
            graph.registerAttachment(station.getHere(), pos, ra);
        }
    }

    default void registerAttachments(TileStation station, StationGraph graph) {
        for (BlockPos pos : station.getAttachments()) {
            ResolvedAttachment<?> ra = resolveAttachment(station, pos);
            if (ra != null) {
                graph.registerAttachment(station.getHere(), pos, ra);
            }
        }
    }

    static ResolvedAttachment<?> resolveAttachment(TileStation station, BlockPos pos) {
        return StationAttachmentRegistry.resolve(station, pos);
    }

    @Override
    default void tickPostBoot(TileStation station) {
        StationGraph graph = station.getGraph();
        if (graph == null) return;

        boolean changed = false;
        for (BlockPos pos : station.getAttachments()) {
            ResolvedAttachment<?> ra = resolveAttachment(station, pos);
            if (ra == null) {
                if (graph.hasAttachment(pos)) {
                    graph.removeAttachmentSilently(pos);
                    changed = true;
                }
                continue;
            }
            boolean valid = isReady(ra);
            if (valid) {
                if (!graph.hasAttachment(pos)) {
                    graph.registerAttachment(station.getHere(), pos, ra);
                    changed = true;
                }
            } else {
                if (graph.hasAttachment(pos)) {
                    graph.removeAttachmentSilently(pos);
                    changed = true;
                }
            }
        }
        if (changed) station.markDirty();
    }

    private static <T> boolean isReady(ResolvedAttachment<T> ra) {
        return ra.handler()
            .isReady(ra.attachment());
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
