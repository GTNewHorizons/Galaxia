package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;

public class DockBehavior implements StationBehavior {

    public static final DockBehavior INSTANCE = new DockBehavior();

    private final ArbitraryShapeDefinition<TileStation> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStation>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
        .addElement(GalaxiaStructureUtility.ofBlockAnyMeta(GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get()))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;
                dock.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
            if (tileEntity instanceof IStationAttachment) {
                BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                dock.addAttachment(pos);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.HAMMER_TARGET.get(), 0))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
            if (tileEntity instanceof IStationAttachment) {
                BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                dock.addAttachment(pos);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.HAMMER_CANNON.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .withSearchRadius(ConfigStructures.open.searchRadius)
        .open()
        .build();

    @Override
    public String getName() {
        return "dock";
    }

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.dock";
    }

    @Override
    public IStructureDefinition<TileStation> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public int getSearchRadius() {
        return 0;
    }

    @Override
    public void onStructureFormed(TileStation station) {
        station.oxygenLevel = 0;
    }

    @Override
    public void tickPostBoot(TileStation station) {
        StationGraph graph = station.getGraph();
        if (graph == null) return;

        boolean changed = false;
        Iterator<BlockPos> it = station.getAttachments().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            TileEntity te = pos.getTE(station.getWorldObj());
            if (!(te instanceof IStationAttachment)
                || (te instanceof GalaxiaBootableMultiblock<?> base && !base.isStructureValid())) {
                graph.removeAttachment(pos);
                it.remove();
                changed = true;
            }
        }
        registerAllAttachments(station);
        if (changed) station.markDirty();
    }

    @Override
    public void onGraphRebuilt(TileStation station) {
        registerAllAttachments(station);
    }

    private void registerAllAttachments(TileStation station) {
        StationGraph graph = station.getGraph();
        if (graph == null) return;

        for (BlockPos pos : station.getAttachments()) {
            if (pos.getTE(station.getWorldObj()) instanceof IStationAttachment<?> attachment) {
                graph.registerAttachment(station.getHere(), pos, attachment);
            }
        }
    }

    @Override
    public List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset) {
        return List.of(
            new TextWidget<>(IKey.dynamic(() -> {
                int count = station.getAttachments().size();
                String key = "galaxia.gui.station_controller.targets";
                return net.minecraft.util.StatCollector.translateToLocal(key) + ": " + count;
            })).pos(10, yOffset));
    }

    @Override
    public void writeToNBT(TileStation station, NBTTagCompound nbt) {
        nbt.setTag("attachments", BlockPos.listToNBT(station.getAttachments()));
    }

    @Override
    public void readFromNBT(TileStation station, NBTTagCompound nbt) {
        if (nbt.hasKey("attachments")) {
            station.setAttachments(BlockPos.listFromNBT(nbt.getTagList("attachments", Constants.NBT.TAG_COMPOUND)));
        }
    }
}
