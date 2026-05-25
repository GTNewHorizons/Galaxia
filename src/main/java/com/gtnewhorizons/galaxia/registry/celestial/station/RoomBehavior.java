package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;

public class RoomBehavior implements IStationBehavior {

    private final ArbitraryShapeDefinition<TileStation> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStation>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
        .addElements(
            TileStationBase.BASE_VALID_BLOCKS.stream()
                .map(b -> GalaxiaStructureUtility.ofBlock(b, 0)))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((station, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;
                station.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .withSearchRadius(ConfigStructures.enclosed.searchRadius)
        .enclosed()
        .build();

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.room";
    }

    @Override
    public IStructureDefinition<TileStation> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public int getSearchRadius() {
        return STRUCTURE_DEFINITION.getSearchRadius();
    }

    @Override
    public void onStructureFormed(TileStation station) {
        station.oxygenLevel = TileStationBase.DEFAULT_OXYGEN_LEVEL;
    }

    @Override
    public List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset) {
        BooleanSyncValue oxygenatedSync = new BooleanSyncValue(
            () -> station.isOxygenated(),
            () -> station.isOxygenated());
        syncManager.syncValue("oxygenated", 0, oxygenatedSync);

        return List.of(new TextWidget<>(IKey.dynamic(() -> {
            boolean oxy = oxygenatedSync.getBoolValue();
            String oxygen = StatCollector.translateToLocal("galaxia.gui.station_controller.oxygen");
            String status = StatCollector.translateToLocal(oxy ? "galaxia.gui.status_yes" : "galaxia.gui.status_no");
            EnumChatFormatting color = oxy ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            return oxygen + ": " + color + status + EnumChatFormatting.RESET;
        })).pos(10, yOffset));
    }
}
