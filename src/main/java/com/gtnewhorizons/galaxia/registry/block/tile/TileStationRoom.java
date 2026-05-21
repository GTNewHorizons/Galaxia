package com.gtnewhorizons.galaxia.registry.block.tile;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

public class TileStationRoom extends TileStationSecondary<TileStationRoom> {

    public final ArbitraryShapeDefinition<TileStationRoom> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStationRoom>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_ROOM.get())
        .addElements(
            BASE_VALID_BLOCKS.stream()
                .map(b -> GalaxiaStructureUtility.ofBlock(b, 0)))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((stationRoom, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                stationRoom.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .withSearchRadius(ConfigStructures.enclosed.searchRadius)
        .enclosed()
        .build();

    @Override
    public IStructureDefinition<TileStationRoom> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);
        BooleanSyncValue oxygenatedSync = new BooleanSyncValue(() -> isOxygenated(), () -> isOxygenated());
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
                boolean oxy = oxygenatedSync.getBoolValue();
                String oxygen = StatCollector.translateToLocal("galaxia.gui.station_room.oxygen");
                String status = StatCollector
                    .translateToLocal(oxy ? "galaxia.gui.status_yes" : "galaxia.gui.status_no");
                EnumChatFormatting color = oxy ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
                return oxygen + ": " + color + status + EnumChatFormatting.RESET;
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
    public int getSearchRadius() {
        return STRUCTURE_DEFINITION.getSearchRadius();
    }

}
