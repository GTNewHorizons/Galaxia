package com.gtnewhorizons.galaxia.registry.block.tile;

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
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public class TileHammerCannon extends GalaxiaBootableMultiblock<TileHammerCannon>
    implements IGuiHolder<PosGuiData>, IDistributedInventory, IStationAttachment<TileHammerCannon> {

    private static final String NBT_FILTER = "filter";
    private static final String NBT_HAMMER_VARIANT = "hammerVariant";
    private static final String NBT_HAMMER_ENERGY = "hammerEnergy";
    private static final String NBT_HAMMER_COOLDOWN_SHOT = "hammerCooldownShot";
    private static final String NBT_HAMMER_COOLDOWN_ROUTE = "hammerCooldownShot";

    private final static String STRUCTURE_PIECE_MAIN = "main";
    private static final IStructureDefinition<TileHammerCannon> STRUCTURE_DEFINITION = StructureDefinition
        .<TileHammerCannon>builder()
        // spotless:off
        .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(new String[][]{
            {"  T  ", "     ", "T   T", "     ", "  T  "},
            {"  T  ", "     ", "T   T", "     ", "  T  "},
            {"  C  ", "     ", "C   C", "     ", "  C  "},
            {" CCC ", "C   C", "C   C", "C   C", " CCC "},
            {" C~C ", "CCCCC", "CCCCC", "CCCCC", " CCC "}
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

    // Internal inventory only available for firing hammer packages
    private final List<IInventory> inventory = new ArrayList<>();
    private final ResourceFilter<ItemStackWrapper> filter = ResourceFilter.forItems();
    private @Nullable StationGraph graph;
    private BlockPos here;
    private final ModuleInstance moduleInstance;
    private final ModuleHammer hammer;

    public ModuleInstance getModuleInstance() {
        return moduleInstance;
    }

    public ModuleHammer getHammer() {
        return hammer;
    }

    public List<IInventory> getChestInventories() {
        return inventory;
    }

    public ResourceFilter<ItemStackWrapper> getFilter() {
        return filter;
    }

    public TileHammerCannon() {
        super();

        here = new BlockPos(xCoord, yCoord, zCoord);
        // TODO: Figure out tiering system
        this.moduleInstance = FacilityModuleKind.HAMMER
            .create(StationTileCoord.CORE, ModuleShape.SINGLE, ModuleTier.UV);
        moduleInstance.updateStatus(Buildable.Status.DISABLED);
        this.hammer = (ModuleHammer) this.moduleInstance.component();
        this.hammer.setVariant(HammerVariant.BIG);
    }

    @Override
    public BlockPos getPosition() {
        return here;
    }

    @Override
    public void tick() {
        if (graph == null) return;
        moduleInstance.tick(
            CelestialAssetStore.findAsset(
                graph.getController()
                    .getBackingStation()));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
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
    protected boolean attemptBoot() {
        return graph != null;
    }

    @Override
    protected void onBootComplete() {
        moduleInstance.updateStatus(Buildable.Status.OPERATIONAL);
    }

    @Override
    protected void onBootFailed() {
        moduleInstance.updateStatus(Buildable.Status.DISABLED);
    }

    @Override
    public void onAttached(StationGraph graph) {
        this.graph = graph;
    }

    @Override
    public void onDetached(StationGraph graph) {
        this.graph = null;
    }

    @Override
    public List<IInventory> getInventories() {
        return List.of();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList filterList = new NBTTagList();
        for (String key : filter.serialize()) {
            filterList.appendTag(new NBTTagString(key));
        }
        nbt.setTag(NBT_FILTER, filterList);
        nbt.setString(
            NBT_HAMMER_VARIANT,
            hammer.variant()
                .name());
        nbt.setLong(NBT_HAMMER_ENERGY, hammer.energyStored());
        nbt.setInteger(NBT_HAMMER_COOLDOWN_SHOT, hammer.shotCooldownTicks());
        nbt.setInteger(NBT_HAMMER_COOLDOWN_ROUTE, hammer.routeProbeCooldownTicks());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        filter.clear();
        if (nbt.hasKey(NBT_FILTER)) {
            NBTTagList filterList = nbt.getTagList(NBT_FILTER, Constants.NBT.TAG_STRING);
            java.util.ArrayList<String> keys = new java.util.ArrayList<>();
            for (int i = 0; i < filterList.tagCount(); i++) {
                keys.add(filterList.getStringTagAt(i));
            }
            filter.load(keys);
        }
        if (nbt.hasKey(NBT_HAMMER_VARIANT)) {
            try {
                HammerVariant variant = HammerVariant.valueOf(nbt.getString(NBT_HAMMER_VARIANT));
                hammer.setVariant(variant);
            } catch (IllegalArgumentException e) {
                hammer.setVariant(HammerVariant.BIG);
            }
        }
        hammer.setEnergyStored(nbt.getLong(NBT_HAMMER_ENERGY));
        hammer
            .setDispatchCooldowns(nbt.getInteger(NBT_HAMMER_COOLDOWN_SHOT), nbt.getInteger(NBT_HAMMER_COOLDOWN_ROUTE));
    }

    @Override
    public IStructureDefinition<TileHammerCannon> getStructureDefinition() {
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
        return GalaxiaBlocksEnum.HAMMER_CANNON.get();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);

        return new ModularPanel("galaxia:hammer_cannon").size(210, 130)
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
