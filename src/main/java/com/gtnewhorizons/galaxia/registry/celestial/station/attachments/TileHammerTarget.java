package com.gtnewhorizons.galaxia.registry.celestial.station.attachments;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.interfaces.IInventoryStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public class TileHammerTarget extends GalaxiaMultiblockBase<TileHammerTarget> implements IGuiHolder<PosGuiData> {

    private final static String STRUCTURE_PIECE_MAIN = "main";
    private static final IStructureDefinition<TileHammerTarget> STRUCTURE_DEFINITION = StructureDefinition
        .<TileHammerTarget>builder()
        // spotless:off
        .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(new String[][] {
            { "  T  ", "     ", "T   T", "     ", "  T  " },
            { "  T  ", "     ", "T   T", "     ", "  T  " },
            { "  C  ", "     ", "C   C", "     ", "  C  " },
            { " CCC ", "C   C", "C   C", "C   C", " CCC " },
            { " C~C ", "CCCCC", "CCCCC", "CCCCC", " CCC " }
        }))
        // spotless:on
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(), 0))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((target, te) -> {
            if (te instanceof TileEntityChest chest) {
                target.inventory.add(chest);
                return true;
            }
            return false;
        }, Blocks.chest, 0), StructureUtility.ofBlock(GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(), 0)))
        .build();

    public static IInventoryStorageHandler<TileHammerTarget> HANDLER = new IInventoryStorageHandler<>() {

        @Override
        public ResourceFilter<ItemStackWrapper> getItemFilter(TileHammerTarget attachment) {
            return attachment.filter;
        }

        @Override
        public List<IInventory> getInventories(TileHammerTarget attachment) {
            return attachment.inventory;
        }

        @Override
        public BlockPos getPosition(TileHammerTarget attachment) {
            return attachment.here;
        }

        @Override
        public void tick(TileHammerTarget attachment) {}

        @Override
        public boolean isReady(TileHammerTarget attachment) {
            return attachment.structureValid && attachment.graph != null;
        }

        @Override
        public void onAttached(TileHammerTarget attachment, StationGraph graph) {
            attachment.graph = graph;
        }

        @Override
        public void onDetached(TileHammerTarget attachment, StationGraph graph) {
            attachment.graph = null;
        }

        @Override
        public void markDirty(TileHammerTarget attachment) {
            attachment.markDirty();
        }
    };

    private final List<IInventory> inventory = new ArrayList<>();
    private final ResourceFilter<ItemStackWrapper> filter = ResourceFilter.forItems();
    private @Nullable StationGraph graph;
    private final BlockPos here;

    public TileHammerTarget() {
        super();
        here = new BlockPos(xCoord, yCoord, zCoord);
    }

    @Override
    public void onStructureDisformed() {
        super.onStructureDisformed();
        if (graph != null) {
            graph.removeAttachment(here);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList filterList = new NBTTagList();
        for (String key : filter.serialize()) {
            filterList.appendTag(new NBTTagString(key));
        }
        nbt.setTag("filter", filterList);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        filter.clear();
        if (nbt.hasKey("filter")) {
            NBTTagList filterList = nbt.getTagList("filter", Constants.NBT.TAG_STRING);
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < filterList.tagCount(); i++) {
                keys.add(filterList.getStringTagAt(i));
            }
            filter.load(keys);
        }
    }

    @Override
    public IStructureDefinition<TileHammerTarget> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return 2;
    }

    @Override
    protected int getControllerOffsetY() {
        return 4;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.HAMMER_TARGET.get();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);

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
            })).pos(10, 30));
    }

}
