package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public class TileEntityModuleAssembler extends GalaxiaMultiblockBase<TileEntityModuleAssembler>
    implements IGuiHolder<PosGuiData> {

    // Hashmap stores <Module ID, Count>
    public HashMap<Integer, Integer> moduleMap = new HashMap<>();
    private TileEntityGantryTerminal gantryTerminal;
    private int foundTerminalCount = 0;
    public boolean shouldRender = true;

    private ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    private final static String STRUCTURE_PIECE_MAIN = "main";

    private static final IStructureDefinition<TileEntityModuleAssembler> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityModuleAssembler>builder()
        // spotless:off
            .addShape(STRUCTURE_PIECE_MAIN, new String[][] {
                    { "CCC", "CCC", "CCC" },
                    { "C C", "T T", "C C" },
                    { "C C", "C C", "C C" },
                    { "C C", "C C", "C C" },
                    { "CCC", "C~C", "CCC" }
            })
            // spotless:on
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((assembler, te) -> {
            if (te instanceof TileEntityGantryTerminal terminal) {
                assembler.setGantryTerminal(terminal);
                terminal.connectAssembler(assembler);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
        .build();

    /**
     * Gets the structure definition of this multiblock
     *
     * @return The structure definition of this multiblock
     */
    @Override
    public IStructureDefinition<TileEntityModuleAssembler> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    /**
     * Gets the x offset from the origin of the multi to the controller
     *
     * @return X offset
     */
    @Override
    protected int getControllerOffsetX() {
        return 1;
    }

    /**
     * Gets the y offset from the origin of the multi to the controller
     *
     * @return Y offset
     */
    @Override
    protected int getControllerOffsetY() {
        return 1;
    }

    /**
     * Gets the z offset from the origin of the multi to the controller
     *
     * @return Z offset
     */
    @Override
    protected int getControllerOffsetZ() {
        return 4;
    }

    /**
     * Handles logic after structure is formed
     */
    @Override
    protected void onStructureFormed() {
        shouldRender = true;
    }

    /**
     * Handles the logic of checking structure validity - overridden to include
     * terminal count checks
     *
     * @return Boolean : True => valid structure
     */
    @Override
    protected boolean checkStructure() {
        if (worldObj == null || worldObj.isRemote) return structureValid;
        boolean valid = false;
        final List<ExtendedFacing> HORIZONTAL_FACINGS = Arrays.stream(ExtendedFacing.values())
            .filter(f -> f.getDirection() != ForgeDirection.UP && f.getDirection() != ForgeDirection.DOWN)
            .collect(Collectors.toList());
        for (ExtendedFacing facing : HORIZONTAL_FACINGS) {
            foundTerminalCount = 0;
            gantryTerminal = null;

            valid = getStructureDefinition().check(
                (TileEntityModuleAssembler) this,
                STRUCTURE_PIECE_MAIN,
                worldObj,
                facing,
                xCoord,
                yCoord,
                zCoord,
                getControllerOffsetX(),
                getControllerOffsetY(),
                getControllerOffsetZ(),
                false);

            if (valid && foundTerminalCount == 1) {
                currentFacing = facing;
                break;
            }
            valid = false;
        }

        if (valid != structureValid) {
            structureValid = valid;
            if (valid) onStructureFormed();
            else onStructureDisformed();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return valid;
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        if (worldObj == null) return;
        if (!hintsOnly && worldObj.isRemote) return;

        getStructureDefinition().buildOrHints(
            (TileEntityModuleAssembler) this,
            trigger,
            STRUCTURE_PIECE_MAIN,
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            hintsOnly);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (worldObj == null || worldObj.isRemote) return -1;
        if (structureValid) return -1;

        return getStructureDefinition().survivalBuild(
            (TileEntityModuleAssembler) this,
            trigger,
            STRUCTURE_PIECE_MAIN,
            worldObj,
            currentFacing,
            xCoord,
            yCoord,
            zCoord,
            getControllerOffsetX(),
            getControllerOffsetY(),
            getControllerOffsetZ(),
            elementBudget,
            env,
            false);
    }

    /**
     * Gets the controller block for this multi
     *
     * @return The controller block
     */
    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get();
    }

    /**
     * The UI builder for the Tile Entity GUI
     *
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be
     *                    registered
     * @param settings    settings which apply to the whole ui and not just this
     *                    panel
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("galaxia:module_assembler").size(240, 160);

        // Title
        panel.child(
            IKey.str(
                EnumChatFormatting.BOLD + StatCollector.translateToLocal("tile.module_assembler_controller.name")
                    + EnumChatFormatting.RESET)
                .asWidget()
                .pos(8, 8));

        // Adding module buttons
        Flow row = Flow.row()
            .coverChildren()
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row.child(createModuleButton(m));
        }
        panel.child(row);

        // Module storage counters
        Flow row2 = Flow.row()
            .coverChildren()
            .pos(10, 70)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            Supplier<String> stringSupplier = () -> m.getName() + " : " + moduleMap.getOrDefault(m.getId(), 0);
            row2.child(
                IKey.dynamic(stringSupplier)
                    .asWidget()
                    .padding(4)
                    .size(40, 20));
        }
        panel.child(row2);
        return panel;
    }

    /**
     * Creates a button to add a new module
     *
     * @param m The rocket module this button handles
     * @return The ButtonWidget needed in the main panel
     */
    private ButtonWidget<?> createModuleButton(RocketModule m) {
        return new ButtonWidget<>().size(48, 20)
            .overlay(IKey.str(m.getName()))
            .tooltip(
                t -> t.add(
                    EnumChatFormatting.GRAY + String.format("%.1fm | %.0fkg", m.getHeight(), m.getWeight())
                        + EnumChatFormatting.RESET))
            .syncHandler(
                new InteractionSyncHandler()
                    .setOnMousePressed(md -> { if (md.mouseButton == 0) addModule(m.getId()); }));
    }

    public void setGantryTerminal(TileEntityGantryTerminal teg) {
        this.gantryTerminal = teg;
        foundTerminalCount++;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return this.gantryTerminal;
    }

    /**
     * Removes a module from the module map and updates TileEntity
     *
     * @param id The module ID to remove
     */
    public void removeModule(int id) {
        moduleMap.put(id, moduleMap.getOrDefault(id, 0) - 1);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void sendModule(int id, TileEntitySilo dest) {
        GantryAPI.injectModule(ModuleRegistry.fromId(id), this, dest, false);
    }

    public void sync() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        moduleMap.clear();
        NBTTagCompound mapNbt = tag.getCompoundTag("moduleMap");
        for (String key : mapNbt.func_150296_c()) {
            moduleMap.put(Integer.parseInt(key), mapNbt.getInteger(key));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound mapNbt = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> e : moduleMap.entrySet()) {
            mapNbt.setInteger(
                e.getKey()
                    .toString(),
                e.getValue());
        }
        tag.setTag("moduleMap", mapNbt);
    }

    /**
     * Writes an NBT packet to the server
     *
     * @return Packet holding the nbt update
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    /**
     * Receives a data packet and updates NBT data
     *
     * @param net    The network manager of the server
     * @param packet The incoming packet from the server
     */
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    /**
     * Adds a new module to the internal storage
     *
     * @param id The ID of the module being added
     */
    public void addModule(int id) {
        moduleMap.put(id, moduleMap.getOrDefault(id, 0) + 1);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

}
