package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventoryOLD;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public class TileHammerCannon extends GalaxiaBootableMultiblock<TileHammerCannon>
    implements IGuiHolder<PosGuiData>, IDistributedInventoryOLD, IStationAttachment {

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

    private final List<IInventory> inventory = new ArrayList<>();
    private final List<ItemStack> filter = new ArrayList<>();
    private @Nullable StationGraph graph;
    private BlockPos here;
    private final ModuleInstance moduleInstance;
    private final ModuleHammer hammer;

    public TileHammerCannon() {
        super();

        here = new BlockPos(xCoord, yCoord, zCoord);
        // TODO: Figure out tiering system
        this.moduleInstance = FacilityModuleKind.HAMMER.create(StationTileCoord.CORE, ModuleShape.SINGLE, ModuleTier.EV);
        this.hammer = (ModuleHammer) this.moduleInstance.component();
    }

    @Override
    public BlockPos getPosition() {
        return here;
    }

    @Override
    public void tick() {
        if (graph == null) return;
        moduleInstance.tick(CelestialAssetStore.findAsset(graph.getController().getBackingStation()));
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
    public void onAttached(StationGraph graph) {
        this.graph = graph;
    }

    @Override
    public void onDetached(StationGraph graph) {
        this.graph = null;
    }

    @Override
    public List<IInventory> getInventories() {
        return this.inventory;
    }

    @Override
    public List<ItemStack> getFiltersFor(int i) {
        return filter;
    }

    @Override
    public String getInventoryName() {
        return "Hammer cannon";
    }

    @Override
    public void markDirty() {
        super.markDirty();
        IDistributedInventoryOLD.super.markDirty();
    }

    public void setFilters(List<ItemStack> filterList) {
        filter.clear();
        if (filterList != null) {
            for (ItemStack stack : filterList) {
                if (stack != null) filter.add(stack.copy());
            }
        }
        markDirty();
    }

    @Override
    public void setFilters(int slot, List<ItemStack> filterList) {
        setFilters(filterList);
    }

    public void addFilter(ItemStack stack) {
        if (stack == null) return;
        filter.add(stack.copy());
        markDirty();
    }

    @Override
    public void addFilter(int slot, ItemStack stack) {
        addFilter(stack);
    }

    public void removeFilter(ItemStack stack) {
        if (stack == null) return;
        filter.removeIf(
            f -> f != null && f.getItem() == stack.getItem()
                && (!f.getHasSubtypes() || f.getItemDamage() == stack.getItemDamage())
                && (!f.hasTagCompound() || ItemStack.areItemStackTagsEqual(f, stack)));
        markDirty();
    }

    @Override
    public void removeFilter(int slot, ItemStack stack) {
        removeFilter(stack);
    }

    public void clearFilters() {
        filter.clear();
        markDirty();
    }

    @Override
    public void clearFilters(int slot) {
        clearFilters();
    }

    @Override
    public Map<Integer, List<ItemStack>> filtersSnapshot() {
        if (filter.isEmpty()) return Map.of();
        return Map.of(0, new ArrayList<>(filter));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList filterList = new NBTTagList();
        for (ItemStack stack : filter) {
            NBTTagCompound stackNbt = new NBTTagCompound();
            stack.writeToNBT(stackNbt);
            filterList.appendTag(stackNbt);
        }
        nbt.setTag(NBT_FILTER, filterList);
        nbt.setString(NBT_HAMMER_VARIANT, hammer.variant().name());
        nbt.setLong(NBT_HAMMER_ENERGY, hammer.energyStored());
        nbt.setInteger(NBT_HAMMER_COOLDOWN_SHOT, hammer.shotCooldownTicks());
        nbt.setInteger(NBT_HAMMER_COOLDOWN_ROUTE, hammer.routeProbeCooldownTicks());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        filter.clear();
        if (nbt.hasKey(NBT_FILTER)) {
            NBTTagList filterList = nbt.getTagList(NBT_FILTER, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < filterList.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(filterList.getCompoundTagAt(i));
                if (stack != null) filter.add(stack);
            }
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
        hammer.setDispatchCooldowns(
            nbt.getInteger(NBT_HAMMER_COOLDOWN_SHOT),
            nbt.getInteger(NBT_HAMMER_COOLDOWN_ROUTE));
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
