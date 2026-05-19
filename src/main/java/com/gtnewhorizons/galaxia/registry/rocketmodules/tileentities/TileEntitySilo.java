package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildOrder;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public class TileEntitySilo extends GalaxiaMultiblockBase<TileEntitySilo>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE {

    private EntityRocket entityRocket;
    public boolean shouldRender = true;

    private int destination = -1;
    private final IntValue.Dynamic selectedDim = new IntValue.Dynamic(() -> destination, v -> {
        destination = v;
        GALAXIA_NETWORK.sendToServer(new DestinationSetPacket(xCoord, yCoord, zCoord, v));
    });

    private String pendingSchematicName = "";

    private TileEntityGantryTerminal gantryTerminal;
    public TileEntityModuleAssembler moduleAssembler;
    private int[] pendingAssemblerCoords;
    public boolean hasAssembler = false;

    public ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    private ForgeDirection placedFacing = ForgeDirection.NORTH;

    public static final int SILO_DEFAULT_X_OFFSET = 0;
    public static final int SILO_DEFAULT_Y_OFFSET = 1;
    public static final int SILO_DEFAULT_Z_OFFSET = 2;

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** What the player has drawn in the editor. Mutable only while canEdit(). */
    private RocketBlueprint designBlueprint = new RocketBlueprint();

    /**
     * Immutable snapshot created at order time. Drives the assembler's work list.
     * Null when not in ASSEMBLING state.
     */
    private RocketBuildOrder currentBuildOrder = null;

    /**
     * Accumulated result of confirmed deliveries. Only updated in receiveModulePart.
     * Becomes authoritative source for the EntityRocket once READY.
     */
    private RocketBlueprint assembledBlueprint = new RocketBlueprint();

    private RocketBuildStatus buildStatus = RocketBuildStatus.IDLE;

    public RocketBlueprint getDesignBlueprint() {
        return designBlueprint;
    }

    public RocketBuildOrder getCurrentBuildOrder() {
        return currentBuildOrder;
    }

    /**
     * Returns the assembled blueprint only when the rocket is actually built or
     * launched. Returns an empty blueprint otherwise so callers can't render a
     * half-built rocket.
     */
    public RocketBlueprint getBuiltBlueprint() {
        return buildStatus == RocketBuildStatus.READY || buildStatus == RocketBuildStatus.LAUNCHED ? assembledBlueprint
            : new RocketBlueprint();
    }

    public RocketBuildStatus getBuildStatus() {
        return buildStatus;
    }

    /**
     * Called by the editor UI on close. Only applies the new design when editing
     * is allowed — prevents the open editor from overwriting a live build order.
     */
    public void setDesignBlueprint(RocketBlueprint bp) {
        if (!buildStatus.canEdit()) return;
        this.designBlueprint = bp != null ? bp.copy() : new RocketBlueprint();
        updateBuildStatusAfterEdit();
        sync();
    }

    /**
     * Creates an immutable build order from the current design and enqueues all parts
     * to the assembler at once. The assembler immediately begins work on any part it
     * has in stock and holds the rest until stock is replenished — no serial
     * request-per-delivery loop.
     */
    public void orderModules() {
        if (worldObj.isRemote) return;

        System.out.println("[SILO] orderModules() called - Status: " + buildStatus);

        if (designBlueprint.isEmpty()) {
            System.out.println("[SILO] cannot order - empty blueprint");
            return;
        }

        if (buildStatus == RocketBuildStatus.IDLE) {
            buildStatus = RocketBuildStatus.DESIGNED;
        }

        if (!buildStatus.canOrder()) {
            System.out.println("[SILO] cannot order - wrong status");
            return;
        }

        updateLinkedAssembler();
        System.out.println(
            "[SILO] hasAssembler=" + hasAssembler
                + ", moduleAssembler="
                + (moduleAssembler != null)
                + ", gantryTerminal="
                + (gantryTerminal != null));

        if (!hasAssembler || moduleAssembler == null || gantryTerminal == null) {
            System.out.println("[SILO] No assembler or gantry terminal connected");
            return;
        }

        boolean graphValid = gantryTerminal.checkValidGraph() && moduleAssembler.checkValidGraph();
        System.out.println("[SILO] Gantry graph valid: " + graphValid);

        if (!graphValid) {
            System.out.println("[SILO] Gantry graph is invalid");
            return;
        }

        RocketAssembly analysis = designBlueprint.analyze();
        System.out.println("[SILO] Blueprint viable: " + analysis.viable());

        if (!analysis.viable()) {
            System.out.println("[SILO] Blueprint not viable");
            return;
        }

        this.currentBuildOrder = new RocketBuildOrder(designBlueprint);
        this.buildStatus = RocketBuildStatus.ASSEMBLING;
        this.assembledBlueprint.clear();

        System.out.println("[SILO] Ordering " + currentBuildOrder.totalCount() + " parts...");

        for (RocketPartInstance part : currentBuildOrder.getParts()) {
            System.out.println(
                "[SILO] Requesting production: " + part.def()
                    .name());
            GantryAPI.requestProduction(part.copy(), moduleAssembler, this);
        }

        sync();
        System.out.println("[SILO] Order completed successfully!");
    }

    public void updateBuildStatusAfterEdit() {
        if (designBlueprint.isEmpty()) {
            buildStatus = RocketBuildStatus.IDLE;
        } else {
            buildStatus = RocketBuildStatus.DESIGNED;
        }
        sync();
    }

    /**
     * Called by TileEntityGantryTerminal when a part physically arrives at the silo.
     * This is the only place that writes to assembledBlueprint.
     */
    public boolean receiveModulePart(RocketPartInstance part) {
        if (currentBuildOrder == null) return false;

        boolean accepted = currentBuildOrder.markDelivered(part);
        if (accepted) {
            assembledBlueprint.addPart(part.copy());

            if (currentBuildOrder.isComplete()) {
                buildStatus = RocketBuildStatus.READY;
                currentBuildOrder = null;
            }
            sync();
        }
        return accepted;
    }

    private static final IStructureDefinition<TileEntitySilo> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntitySilo>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
            StructureUtility.transpose(
                new String[][] {
                    { "  T  ", "     ", "T   T", "     ", "  T  " },
                    { "  T  ", "     ", "T   T", "     ", "  T  " },
                    { "  C  ", "     ", "C   C", "     ", "  C  " },
                    { " CCC ", "C   C", "C   C", "C   C", " CCC " },
                    { " C~C ", "CCCCC", "CCCCC", "CCCCC", " CCC " }
                }))
        // spotless:on
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((silo, te) -> {
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
            case SOUTH -> new int[] { localX, localY, -localZ };
            case NORTH -> new int[] { -localX, localY, localZ };
            case EAST -> new int[] { -localZ, localY, -localX };
            case WEST -> new int[] { localZ, localY, localX };
            default -> new int[] { localX, localY, -localZ };
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
                markDirty();
                return;
            }
        }

        moduleAssembler = null;
        hasAssembler = false;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return RocketEditorUI.build(data, syncManager, settings);
    }

    private ParentWidget<?> buildBuildPage(Flow moduleRow) {
        return new ParentWidget<>().size(240, 160)
            .child(moduleRow)
            .child(
                new ButtonWidget<>().size(220, 30)
                    .pos(10, 80)
                    .overlay(
                        IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.return_modules"))
                            .alignment(Alignment.Center))
                    .syncHandler(
                        new InteractionSyncHandler().setOnMousePressed(
                            md -> { if (md.mouseButton == 0 && !worldObj.isRemote) returnModules(); })));
    }

    private ParentWidget<?> buildLaunchPage(Flow destRow, PosGuiData data) {
        return new ParentWidget<>().size(240, 160)
            .child(destRow)
            .child(
                new ButtonWidget<>().size(220, 30)
                    .pos(10, 120)
                    .overlay(
                        IKey.dynamic(
                            () -> (EnumChatFormatting.GREEN)
                                + StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.enter_rocket")
                                + EnumChatFormatting.RESET)
                            .alignment(Alignment.CENTER))
                    .tooltipAutoUpdate(true)
                    .syncHandler(
                        new InteractionSyncHandler().setOnMousePressed(
                            md -> { if (md.mouseButton == 0 && !worldObj.isRemote) enterRocket(data); })));
    }

    private ParentWidget<?> buildSchematicPage(StringSyncValue nameSync, PosGuiData data) {
        return new ParentWidget<>().size(240, 160)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.schematic_text"))
                    .asWidget()
                    .pos(10, 40))
            .child(
                new TextFieldWidget().size(220, 30)
                    .pos(10, 60)
                    .setMaxLength(64)
                    .value(nameSync)
                    .autoUpdateOnChange(true))
            .child(
                new ButtonWidget<>().size(220, 30)
                    .pos(10, 120)
                    .overlay(
                        IKey.str(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("galaxia.gui.rocket_silo.builder.schematic_save")
                                + EnumChatFormatting.RESET)
                            .alignment(Alignment.CENTER))
                    .syncHandler(
                        new InteractionSyncHandler().setOnMousePressed(
                            md -> {
                                if (md.mouseButton == 0 && !worldObj.isRemote) captureSchematic(data.getPlayer());
                            })));
    }

    private ToggleButton createDestinationButton(BasePlanet dim) {
        return new ToggleButton().size(48, 20)
            .overlay(
                IKey.str(
                    dim.getPlanetEnum()
                        .getName()))
            .valueWrapped(
                selectedDim,
                dim.getPlanetEnum()
                    .getId());
    }

    private void enterRocket(PosGuiData data) {
        if (!buildStatus.canLaunch() || assembledBlueprint.isEmpty()) return;

        EntityRocket rocket = getOrCreateEntityRocket();
        if (rocket == null) return;

        rocket.setBlueprint(assembledBlueprint.copy());
        rocket.setDestination(destination);

        shouldRender = false;
        buildStatus = RocketBuildStatus.LAUNCHED;
        sync();

        rocket.interactFirst(data.getPlayer());
        if (!rocket.shouldRender()) rocket.launch();
    }

    public void returnModules() {
        if (moduleAssembler == null || worldObj.isRemote) return;

        // TODO: physically return modules via gantry rather than resetting state
        this.designBlueprint.clear();
        this.assembledBlueprint.clear();
        this.currentBuildOrder = null;
        this.buildStatus = RocketBuildStatus.IDLE;
        this.shouldRender = true;
        sync();
    }

    public void captureSchematic(EntityPlayer player) {
        if (worldObj.isRemote || designBlueprint.isEmpty()) return;
        ItemStack schematic = ItemRocketSchematic.captureFromSilo(this, pendingSchematicName);
        if (schematic != null) {
            player.inventory.addItemStackToInventory(schematic);
        }
    }

    private EntityRocket getOrCreateEntityRocket() {
        if (entityRocket != null && !entityRocket.isDead) return entityRocket;

        entityRocket = new EntityRocket(worldObj);
        int[] offset = getRotatedOffset(
            SILO_DEFAULT_X_OFFSET,
            SILO_DEFAULT_Y_OFFSET,
            SILO_DEFAULT_Z_OFFSET,
            currentFacing);
        entityRocket.setPosition(xCoord + offset[0] + 0.5, yCoord + offset[1], zCoord + offset[2] + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
        return entityRocket;
    }

    public void sync() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        if (shouldRender && (entityRocket == null || entityRocket.isDead)
            && structureValid
            && buildStatus == RocketBuildStatus.READY) {
            getOrCreateEntityRocket();
        }

        if (pendingAssemblerCoords != null) {
            TileEntity te = worldObj
                .getTileEntity(pendingAssemblerCoords[0], pendingAssemblerCoords[1], pendingAssemblerCoords[2]);
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

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setBoolean("shouldRender", shouldRender);

        // Build state — all three layers persisted independently
        nbt.setInteger("buildStatus", buildStatus.ordinal());
        nbt.setTag("designBlueprint", designBlueprint.serializeNBT());
        nbt.setTag("assembledBlueprint", assembledBlueprint.serializeNBT());

        if (currentBuildOrder != null) {
            nbt.setTag("buildOrder", currentBuildOrder.serializeNBT());
        }

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

        shouldRender = nbt.getBoolean("shouldRender");

        if (nbt.hasKey("buildStatus")) {
            int ordinal = nbt.getInteger("buildStatus");
            RocketBuildStatus[] values = RocketBuildStatus.values();
            buildStatus = ordinal >= 0 && ordinal < values.length ? values[ordinal] : RocketBuildStatus.IDLE;
        }

        designBlueprint = RocketBlueprint
            .deserializeNBT(nbt.getCompoundTag("designBlueprint"), RocketPartRegistry.instance());
        assembledBlueprint = RocketBlueprint
            .deserializeNBT(nbt.getCompoundTag("assembledBlueprint"), RocketPartRegistry.instance());

        if (nbt.hasKey("buildOrder")) {
            currentBuildOrder = RocketBuildOrder
                .deserializeNBT(nbt.getCompoundTag("buildOrder"), RocketPartRegistry.instance());
        }

        hasAssembler = nbt.getBoolean("hasAssembler");
        destination = nbt.getInteger("destination");
        pendingSchematicName = nbt.getString("pendingSchematicName");

        if (nbt.hasKey("assemblerX")) {
            pendingAssemblerCoords = new int[] { nbt.getInteger("assemblerX"), nbt.getInteger("assemblerY"),
                nbt.getInteger("assemblerZ") };
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

    @Override
    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    @Override
    public void setPlacedFacing(ForgeDirection dir) {
        this.placedFacing = dir;
    }

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }
}
