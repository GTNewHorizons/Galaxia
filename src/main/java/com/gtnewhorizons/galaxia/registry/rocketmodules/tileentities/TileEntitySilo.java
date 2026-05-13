package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.core.network.RocketDestinationSyncPacket;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public class TileEntitySilo extends GalaxiaMultiblockBase<TileEntitySilo>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE {

    private RocketBlueprint blueprint = new RocketBlueprint();

    private EntityRocket entityRocket;
    public boolean shouldRender = true;

    private int destination = -1;
    private final IntValue.Dynamic selectedDim = new IntValue.Dynamic(() -> destination, v -> {
        destination = v;
        GALAXIA_NETWORK.sendToServer(new DestinationSetPacket(xCoord, yCoord, zCoord, v));
    });

    private String pendingSchematicName = "";

    private TileEntityGantryTerminal gantryTerminal;
    private TileEntityModuleAssembler moduleAssembler;
    private int[] pendingAssemblerCoords;
    private boolean hasAssembler = false;

    public ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    private ForgeDirection placedFacing = ForgeDirection.NORTH;

    public static final int SILO_DEFAULT_X_OFFSET = 0;
    public static final int SILO_DEFAULT_Y_OFFSET = 1;
    public static final int SILO_DEFAULT_Z_OFFSET = 2;

    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static final IStructureDefinition<TileEntitySilo> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntitySilo>builder()
        .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(new String[][]{
            {"  T  ", "     ", "T   T", "     ", "  T  "},
            {"  T  ", "     ", "T   T", "     ", "  T  "},
            {"  C  ", "     ", "C   C", "     ", "  C  "},
            {" CCC ", "C   C", "C   C", "C   C", " CCC "},
            {" C~C ", "CCCCC", "CCCCC", "CCCCC", " CCC "}
        }))
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0))
        .addElement('T', StructureUtility.ofChain(
            StructureUtility.ofTileAdder((silo, te) -> {
                if (te instanceof TileEntityGantryTerminal terminal) {
                    silo.setGantryTerminal(terminal);
                    terminal.connectSilo(silo);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
        .build();

    public TileEntitySilo() {
        super();
    }

    @Override
    public IStructureDefinition<TileEntitySilo> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override protected int getControllerOffsetX() { return 2; }
    @Override protected int getControllerOffsetY() { return 4; }
    @Override protected int getControllerOffsetZ() { return 0; }

    @Override
    protected void onStructureFormed() {
        super.onStructureFormed();
        updateLinkedAssembler();
        shouldRender = true;
    }

    @Override
    protected void onStructureDisformed() {
        super.onStructureDisformed();
        updateLinkedAssembler();
        shouldRender = false;
    }

    public static int[] getRotatedOffset(int localX, int localY, int localZ, ExtendedFacing currentFacing) {
        return switch (currentFacing.getDirection()) {
            case SOUTH -> new int[]{localX, localY, -localZ};
            case NORTH -> new int[]{-localX, localY, localZ};
            case EAST  -> new int[]{-localZ, localY, -localX};
            case WEST  -> new int[]{localZ, localY, localX};
            default    -> new int[]{localX, localY, -localZ};
        };
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.SILO_CONTROLLER.get();
    }

    public void updateLinkedAssembler() {
        if (worldObj.isRemote || gantryTerminal == null) {
            moduleAssembler = null;
            hasAssembler = false;
            return;
        }

        if (!gantryTerminal.checkValidGraph()) {
            moduleAssembler = null;
            hasAssembler = false;
            return;
        }

        for (TileEntityGantryTerminal terminal : GantryAPI.findEndpointTerminals(gantryTerminal)) {
            if (terminal.getAssembler() != null) {
                moduleAssembler = terminal.getAssembler();
                hasAssembler = true;
                return;
            }
        }

        moduleAssembler = null;
        hasAssembler = false;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
            updateLinkedAssembler();
        }

        BooleanSyncValue validSync = new BooleanSyncValue(() -> structureValid, v -> {});
        BooleanSyncValue assemblerSync = new BooleanSyncValue(() -> hasAssembler, v -> {});
        StringSyncValue nameSync = new StringSyncValue(this::getPendingSchematicName, this::setPendingSchematicName);

        syncManager.syncValue("rocketSiloStructureValid", validSync);
        syncManager.syncValue("rocketSiloModuleAssembler", assemblerSync);

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_silo_main").size(350, 160);

        // Invalid structure messages
        panel.childIf(!validSync.getBoolValue(),
            () -> IKey.str(EnumChatFormatting.RED + StatCollector.translateToLocal("galaxia.gui.rocket_silo.not_formed"))
                .asWidget().pos(10, 35));

        panel.childIf(validSync.getBoolValue() && !assemblerSync.getBoolValue(),
            () -> IKey.str(EnumChatFormatting.RED + StatCollector.translateToLocal("galaxia.gui.rocket_silo.assembler_none"))
                .asWidget().pos(10, 35));

        // Tabs
        panel.childIf(validSync.getBoolValue() && assemblerSync.getBoolValue(),
            () -> new PageButton(0, tabController).size(120, 28).pos(0, -28)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.build"))));

        panel.childIf(validSync.getBoolValue() && assemblerSync.getBoolValue(),
            () -> new PageButton(1, tabController).size(120, 28).pos(120, -28)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.launch"))));

        panel.childIf(validSync.getBoolValue() && assemblerSync.getBoolValue(),
            () -> new PageButton(2, tabController).size(120, 28).pos(240, -28)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.save"))));

        // Title
        panel.childIf(validSync.getBoolValue() && assemblerSync.getBoolValue(),
            () -> IKey.str(EnumChatFormatting.BOLD + StatCollector.translateToLocal("galaxia.gui.rocket_silo.title"))
                .asWidget().pos(8, 8));

        // === BUILD PAGE ===
        Flow moduleRow = Flow.row().coverChildren().pos(8, 35).padding(4);
        // TODO: Replace with part palette from RocketPartRegistry when editor is integrated
        // For now keep old module buttons as bridge if needed

        // === LAUNCH PAGE ===
        Flow destRow = Flow.row().coverChildren().pos(10, 35).padding(4);
        if (worldObj.provider.dimensionId != 0) {
            destRow.child(new ToggleButton().size(48, 20)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.button.overworld")))
                .valueWrapped(selectedDim, 0));
        }
        for (BasePlanet dim : SolarSystemRegistry.getAllPlanets()) {
            if (dim.getPlanetEnum().getId() != worldObj.provider.dimensionId) {
                destRow.child(createDestinationButton(dim));
            }
        }

        panel.childIf(validSync.getBoolValue() && assemblerSync.getBoolValue(),
            () -> new PagedWidget<>().controller(tabController)
                .addPage(buildBuildPage(moduleRow))
                .addPage(buildLaunchPage(destRow, data))
                .addPage(buildSchematicPage(nameSync, data)));

        return panel;
    }

    private ParentWidget<?> buildBuildPage(Flow moduleRow) {
        return new ParentWidget<>().size(240, 160)
            .child(moduleRow)
            .child(new ButtonWidget<>().size(220, 30).pos(10, 80)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.return_modules"))
                    .alignment(Alignment.Center))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !worldObj.isRemote) returnModules();
                })));
    }

    private ParentWidget<?> buildLaunchPage(Flow destRow, PosGuiData data) {
        return new ParentWidget<>().size(240, 160)
            .child(destRow)
            .child(new ButtonWidget<>().size(220, 30).pos(10, 120)
                .overlay(IKey.dynamic(() -> ( EnumChatFormatting.GREEN) // removed validators for now
                    + StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.enter_rocket")
                    + EnumChatFormatting.RESET).alignment(Alignment.CENTER))
                .tooltipAutoUpdate(true)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !worldObj.isRemote) enterRocket(data);
                })));
    }

    private ParentWidget<?> buildSchematicPage(StringSyncValue nameSync, PosGuiData data) {
        return new ParentWidget<>().size(240, 160)
            .child(IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.schematic_text"))
                .asWidget().pos(10, 40))
            .child(new TextFieldWidget().size(220, 30).pos(10, 60).setMaxLength(64).value(nameSync).autoUpdateOnChange(true))
            .child(new ButtonWidget<>().size(220, 30).pos(10, 120)
                .overlay(IKey.str(EnumChatFormatting.GREEN + StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.schematic_save")
                    + EnumChatFormatting.RESET).alignment(Alignment.CENTER))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !worldObj.isRemote) captureSchematic(data.getPlayer());
                })));
    }

    private ToggleButton createDestinationButton(BasePlanet dim) {
        return new ToggleButton().size(48, 20)
            .overlay(IKey.str(dim.getPlanetEnum().getName()))
            .valueWrapped(selectedDim, dim.getPlanetEnum().getId());
    }

    private void enterRocket(PosGuiData data) {
        if (blueprint.isEmpty()) return;

        EntityRocket rocket = getOrCreateEntityRocket();
        if (rocket == null) return;

        rocket.setBlueprint(blueprint.copy());
        rocket.setDestination(destination);
        GALAXIA_NETWORK.sendToServer(new RocketDestinationSyncPacket(rocket.getEntityId(), destination));

        shouldRender = false;
        sync();

        rocket.interactFirst(data.getPlayer());
        if (!rocket.shouldRender()) rocket.launch();
    }

    public void requestModuleFromAssembler(int moduleId) {
        if (moduleAssembler == null || worldObj.isRemote) return;
        moduleAssembler.removeModule(moduleId); // old method — will be migrated later
        GantryAPI.injectModule(null, moduleAssembler, this, false); // TODO: migrate to blueprint parts
        sync();
    }

    public boolean receiveModulePart(RocketPartInstance part) {
        if (part == null) return false;

        if (blueprint.addPart(part.copy())) {
            sync();
            return true;
        }
        return false;
    }

    public void openEditor(EntityPlayer player) {
        if (!worldObj.isRemote) {
            new RocketEditorUI(blueprint, this).open(player);
        }
    }

    public void returnModules() {
        if (moduleAssembler == null || worldObj.isRemote) return;
        // TODO: Inject blueprint parts back via gantry
        blueprint = new RocketBlueprint();
        shouldRender = true;
        sync();
    }

    public void captureSchematic(EntityPlayer player) {
        if (worldObj.isRemote || blueprint.isEmpty()) return;
        ItemStack schematic = ItemRocketSchematic.captureFromSilo(this, pendingSchematicName);
        if (schematic != null) {
            player.inventory.addItemStackToInventory(schematic);
        }
    }

    private EntityRocket getOrCreateEntityRocket() {
        if (entityRocket != null && !entityRocket.isDead) return entityRocket;

        entityRocket = new EntityRocket(worldObj);
        int[] offset = getRotatedOffset(SILO_DEFAULT_X_OFFSET, SILO_DEFAULT_Y_OFFSET, SILO_DEFAULT_Z_OFFSET, currentFacing);
        entityRocket.setPosition(xCoord + offset[0] + 0.5, yCoord + offset[1], zCoord + offset[2] + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
        return entityRocket;
    }

    public RocketBlueprint getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp.copy() : new RocketBlueprint();
        markDirty();
    }

    public void sync() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        if (shouldRender && (entityRocket == null || entityRocket.isDead) && structureValid && !blueprint.isEmpty()) {
            getOrCreateEntityRocket();
        }

        if (pendingAssemblerCoords != null) {
            TileEntity te = worldObj.getTileEntity(pendingAssemblerCoords[0], pendingAssemblerCoords[1], pendingAssemblerCoords[2]);
            if (te instanceof TileEntityModuleAssembler assembler) {
                moduleAssembler = assembler;
            }
            pendingAssemblerCoords = null;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
    }

    // ==================== NBT ====================

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("shouldRender", shouldRender);
        nbt.setTag("blueprint", blueprint.serializeNBT());

        if (moduleAssembler != null) {
            nbt.setInteger("assemblerX", moduleAssembler.xCoord);
            nbt.setInteger("assemblerY", moduleAssembler.yCoord);
            nbt.setInteger("assemblerZ", moduleAssembler.zCoord);
        }

        nbt.setBoolean("hasAssembler", hasAssembler);
        nbt.setInteger("facing", currentFacing.getIndex());
        nbt.setInteger("placedFacing", placedFacing.ordinal());
        nbt.setInteger("destination", destination);
        nbt.setString("pendingSchematicName", pendingSchematicName);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        blueprint = RocketBlueprint.deserializeNBT(nbt.getCompoundTag("blueprint"), RocketPartRegistry.instance());

        shouldRender = nbt.getBoolean("shouldRender");
        hasAssembler = nbt.getBoolean("hasAssembler");
        destination = nbt.getInteger("destination");
        pendingSchematicName = nbt.getString("pendingSchematicName");

        if (nbt.hasKey("assemblerX")) {
            pendingAssemblerCoords = new int[]{nbt.getInteger("assemblerX"), nbt.getInteger("assemblerY"), nbt.getInteger("assemblerZ")};
        }
        if (nbt.hasKey("facing")) currentFacing = ExtendedFacing.byIndex(nbt.getInteger("facing"));
        if (nbt.hasKey("placedFacing")) placedFacing = ForgeDirection.getOrientation(nbt.getInteger("placedFacing"));
    }

    public void setDestination(int dim) {
        this.destination = dim;
        markDirty();
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        this.gantryTerminal = terminal;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return gantryTerminal;
    }

    public String getPendingSchematicName() {
        return pendingSchematicName;
    }

    public void setPendingSchematicName(String name) {
        this.pendingSchematicName = name;
    }

    // IRocketControllerTE
    @Override public ForgeDirection getPlacedFacing() { return placedFacing; }
    @Override public void setPlacedFacing(ForgeDirection dir) { this.placedFacing = dir; }
    @Override public boolean isStructureValid() { return structureValid; }
    @Override public ExtendedFacing getCurrentFacing() { return currentFacing; }
}
