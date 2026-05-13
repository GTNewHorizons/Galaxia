package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityModuleAssembler extends GalaxiaMultiblockBase<TileEntityModuleAssembler>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE {

    private RocketBlueprint blueprint = new RocketBlueprint();
    private TileEntityGantryTerminal gantryTerminal;

    private ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static final IStructureDefinition<TileEntityModuleAssembler> STRUCTURE_DEFINITION =
        StructureDefinition.<TileEntityModuleAssembler>builder()
            .addShape(STRUCTURE_PIECE_MAIN, new String[][]{
                {"CCC", "CCC", "CCC"},
                {"C C", "T T", "C C"},
                {"C C", "C C", "C C"},
                {"C C", "C C", "C C"},
                {"CCC", "C~C", "CCC"}
            })
            .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0))
            .addElement('T', StructureUtility.ofChain(
                StructureUtility.ofTileAdder((assembler, te) -> {
                    if (te instanceof TileEntityGantryTerminal terminal) {
                        assembler.setGantryTerminal(terminal);
                        terminal.connectAssembler(assembler);
                        return true;
                    }
                    return false;
                }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
                StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
            .build();

    public TileEntityModuleAssembler() {
        super();
    }

    @Override
    public IStructureDefinition<TileEntityModuleAssembler> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override protected int getControllerOffsetX() { return 1; }
    @Override protected int getControllerOffsetY() { return 1; }
    @Override protected int getControllerOffsetZ() { return 4; }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get();
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        this.gantryTerminal = terminal;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return gantryTerminal;
    }

    public RocketBlueprint getBlueprint() {
        return blueprint;
    }

    public void addPart(RocketPartDef def, int x, int y, int z) {
        RocketPartInstance part = new RocketPartInstance(def, x, y, z, false);
        if (blueprint.addPart(part)) {
            markDirty();
            if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Compatibility method for transition period (called from Silo)
     */
    public void removeModule(int id) {
        // TODO: После полного перехода на Blueprint — удалить этот метод
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) markStructureDirty();

        ModularPanel panel = new ModularPanel("galaxia:module_assembler");
        panel.size(400, 300);

        BooleanSyncValue validSync = new BooleanSyncValue(() -> structureValid, v -> {});

        syncManager.syncValue("assemblerStructureValid", validSync);

        panel.childIf(!validSync.getBoolValue(),
            () -> IKey.str(EnumChatFormatting.RED + StatCollector.translateToLocal("galaxia.gui.module_assembler.not_formed"))
                .asWidget().pos(10, 35));

        Flow row = Flow.row().coverChildren().padding(4);
        for (RocketPartDef def : RocketPartRegistry.instance().getAll()) {
            row.child(createPartButton(def));
        }
        panel.childIf(validSync.getBoolValue(), () -> row);

        return panel;
    }

    private ButtonWidget<?> createPartButton(RocketPartDef def) {
        return new ButtonWidget<>()
            .size(80, 20)
            .overlay(IKey.str(def.name()))
            .syncHandler(new InteractionSyncHandler()
                .setOnMousePressed(md -> {
                    if (md.mouseButton == 0) {
                        addPart(def, 4, 4, 0);
                    }
                }));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("blueprint", blueprint.serializeNBT());
    }

    // IRocketControllerTE
    @Override public ForgeDirection getPlacedFacing() { return currentFacing.getDirection(); }
    @Override public void setPlacedFacing(ForgeDirection dir) { /* handled by multiblock */ }
    @Override public boolean isStructureValid() { return structureValid; }
    @Override public ExtendedFacing getCurrentFacing() { return currentFacing; }
}
