package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;

public class TileStationDock extends TileStationSecondary<TileStationDock> implements IGuiHolder<PosGuiData> {

    private List<BlockPos> attachments = new ArrayList<>();

    public final ArbitraryShapeDefinition<TileStationDock> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStationDock>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_DOCK.get())
        .addElement(GalaxiaStructureUtility.ofBlockAnyMeta(GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get()))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dockController, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                dockController.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dockController, tileEntity) -> {
            if (tileEntity instanceof IStationAttachment) {
                BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                if (!dockController.attachments.contains(pos)) {
                    dockController.attachments.add(pos);
                }
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.HAMMER_TARGET.get(), 0))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dockController, tileEntity) -> {
            if (tileEntity instanceof IStationAttachment) {
                BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                if (!dockController.attachments.contains(pos)) {
                    dockController.attachments.add(pos);
                }
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.HAMMER_CANNON.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .withSearchRadius(ConfigStructures.open.searchRadius)
        .open()
        .build();

    public TileStationDock() {
        this.oxygenLevel = 0;
    }

    @Override
    protected void tickPostBoot() {
        if (graph == null) return;
        boolean changed = false;
        var it = attachments.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            TileEntity te = pos.getTE(worldObj);
            if (!(te instanceof IStationAttachment)
                || (te instanceof GalaxiaBootableMultiblock<?>base && !base.isStructureValid())) {
                graph.removeAttachment(pos);
                it.remove();
                changed = true;
            }
        }
        registerAllAttachments();
        if (changed) markDirty();
    }

    @Override
    public void onAttachmentConnected(BlockPos pos, IStationAttachment<?> attachment) {
        if (!attachments.contains(pos)) {
            attachments.add(pos);
            markDirty();
        }
    }

    @Override
    public void onAttachmentDisconnected(BlockPos pos) {
        if (attachments.remove(pos)) {
            markDirty();
        }
    }

    @Override
    public void onGraphRebuilt(TileStationController controller) {
        super.onGraphRebuilt(controller);
        registerAllAttachments();
    }

    private void registerAllAttachments() {
        if (graph == null) return;

        for (BlockPos pos : attachments) {
            if (pos.getTE(worldObj) instanceof IStationAttachment<?>attachment) {
                graph.registerAttachment(here, pos, attachment);
            }
        }
    }

    public List<BlockPos> getValidAttachments() {
        return attachments.stream()
            .filter(pos -> pos != null && pos.getTE(worldObj) instanceof GalaxiaBootableMultiblock<?>)
            .filter(pos -> ((GalaxiaBootableMultiblock<?>) pos.getTE(worldObj)).isStructureValid())
            .toList();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);
        IntSyncValue oxygenatedSync = new IntSyncValue(
            () -> getValidAttachments().size(),
            () -> getValidAttachments().size());
        syncManager.syncValue("oxygenated", 0, oxygenatedSync);

        return new ModularPanel("galaxia:station_room").size(210, 130)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.station_room.title"))
                    .asWidget()
                    .pos(8, 8))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                boolean valid = structureValidSync.getBoolValue();
                String structure = StatCollector.translateToLocal("galaxia.gui.station_room.structure");
                String status = StatCollector
                    .translateToLocal(valid ? "galaxia.gui.status_valid" : "galaxia.gui.status_invalid");
                EnumChatFormatting color = valid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
                return structure + ": " + color + status + EnumChatFormatting.RESET;
            })).pos(10, 30))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int oxy = oxygenatedSync.getIntValue();
                String targets = StatCollector.translateToLocal("galaxia.gui.station_controller.targets");
                EnumChatFormatting color = EnumChatFormatting.GREEN;
                return targets + ": " + color + oxy + EnumChatFormatting.RESET;
            })).pos(10, 50))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.station_room.refresh")))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        markStructureDirty();
                    })));
    }

    @Override
    public IStructureDefinition<TileStationDock> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public int getSearchRadius() {
        return 0;
    }

    @Override
    protected int getControllerOffsetX() {
        return 0;
    }

    @Override
    protected int getControllerOffsetY() {
        return 0;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("attachments", BlockPos.listToNBT(attachments));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("attachments")) {
            attachments = BlockPos.listFromNBT(nbt.getTagList("attachments", Constants.NBT.TAG_COMPOUND));
        }
    }
}
