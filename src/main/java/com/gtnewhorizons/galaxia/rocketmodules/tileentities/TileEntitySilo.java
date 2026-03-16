package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.CapsuleModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.CapsuleRequiredValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.EngineToTankRatioValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.IRocketValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.ModulesFitInCoreValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.SingleRocketCoreValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.TierMatchesDestinationValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.ValidationResult;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.WeightLimitValidator;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public class TileEntitySilo extends TileEntity implements IGuiHolder<PosGuiData> {

    private EntityRocket entityRocket;
    private RocketAssembly assembly;
    // Modules currently in the rendering stack
    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;
    // Validation rules for rocket systems
    private final List<IRocketValidator> validators = Arrays.asList(
        new CapsuleRequiredValidator(),
        new EngineToTankRatioValidator(),
        new WeightLimitValidator(),
        new TierMatchesDestinationValidator(),
        new SingleRocketCoreValidator(),
        new ModulesFitInCoreValidator());
    private int destination = 0;
    private final IntValue.Dynamic selectedDim = new IntValue.Dynamic(() -> destination, v -> {
        destination = v;
        GALAXIA_NETWORK.sendToServer(new DestinationSetPacket(xCoord, yCoord, zCoord, v));
    });

    private TileEntityGantryTerminal gantryTerminal;
    private TileEntityModuleAssembler moduleAssembler;
    private int[] pendingTerminalCoords;
    private int[] pendingAssemblerCoords;
    private boolean hasAssembler = false;

    /**
     * Updates the linked module assembler by searching endpoint terminals of linked
     * gantry
     */
    public void updateLinkedAssembler() {
        if (worldObj.isRemote) return;
        // If no gantry terminal, no graph to check
        if (gantryTerminal == null) {
            moduleAssembler = null;
            hasAssembler = false;
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
            return;
        }
        // If not a valid graph, cannot walk it
        if (!gantryTerminal.checkValidGraph()) {
            moduleAssembler = null;
            hasAssembler = false;
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
            return;
        }
        // Iterate through endpoints to find one with a linked assembler
        List<TileEntityGantryTerminal> endpoints = GantryAPI.findEndpointTerminals(gantryTerminal);
        for (TileEntityGantryTerminal terminal : endpoints) {
            TileEntityModuleAssembler testAssembler = terminal.getAssembler();
            if (testAssembler != null) {
                moduleAssembler = testAssembler;
                hasAssembler = true;
                if (worldObj != null) {
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                }
                return;
            }
        }
        moduleAssembler = null;
        hasAssembler = false;
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * The UI builder for the tile entity
     *
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be
     *                    registered
     * @param settings    settings which apply to the whole ui and not just this
     *                    panel
     * @return The ModularPanel to display as UI
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_silo_main")
            .size(240, 160);

        if (!hasAssembler) {
            return panel.child(
                IKey.str("§cNo Module Assembler linked.")
                    .asWidget()
                    .pos(10, 35));
        }

        panel.child(
            new PageButton(0, tabController).size(120, 28)
                .pos(0, -28)
                .overlay(IKey.str("Build Rocket")))
            .child(
                new PageButton(1, tabController).size(120, 28)
                    .pos(120, -28)
                    .overlay(IKey.str("Launch Rocket")));

        // Title
        panel.child(
            IKey.str("§lRocket Silo")
                .asWidget()
                .pos(8, 8));
        // Module addition buttons
        Flow moduleRow = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            moduleRow.child(createModuleButton(m, moduleAssembler));
        }

        Flow destRow = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);

        for (BasePlanet dim : SolarSystemRegistry.getAllPlanets()) {
            destRow.child(createDestinationButton(dim));
        }

        // Builder Page
        panel.child(
            new PagedWidget<>().controller(tabController)
                .addPage(
                    new ParentWidget<>().size(240, 160)
                        .child(moduleRow)
                        .child(
                            new ButtonWidget<>().size(220, 30)
                                .pos(10, 80)
                                .overlay(
                                    IKey.str("Return Modules")
                                        .alignment(Alignment.Center))
                                .tooltip(t -> t.addLine("Disassemble Rocket"))
                                .syncHandler(
                                    new InteractionSyncHandler().setOnMousePressed(
                                        md -> {
                                            if (md.mouseButton == 0 && !worldObj.isRemote)
                                                returnModules(moduleAssembler);
                                        }))))
                // Launch Page
                .addPage(
                    new ParentWidget<>().size(240, 160)
                        .child(destRow)
                        .child(
                            new ButtonWidget<>().size(220, 30)
                                .pos(10, 120)
                                .overlay(
                                    IKey.str("§aEnter Rocket")
                                        .alignment(Alignment.CENTER))
                                .tooltipDynamic(t -> {
                                    // Flag to indicate validity of rocket launching
                                    boolean validFlag = true;
                                    getAssembly().updateDestination(destination);
                                    if (getAssembly().getModules()
                                        .isEmpty()) {
                                        t.addLine("§7Add some modules first");
                                        return;
                                    }
                                    for (IRocketValidator v : validators) {
                                        ValidationResult r = v.validate(getAssembly());
                                        if (!r.valid()) t.addLine("§c" + r.message());
                                        validFlag = false;
                                    }
                                    if (!validFlag) return;
                                })
                                .tooltipAutoUpdate(true)
                                .syncHandler(
                                    new InteractionSyncHandler().setOnMousePressed(
                                        md -> {
                                            if (md.mouseButton == 0 && !worldObj.isRemote) enterRocket(data);
                                        })))));

        return panel;

    }

    /**
     * Creates the button for adding a module
     *
     * @param module    The Rocket module this button is responsible for
     * @param assembler The Module Assembler this is linked to
     * @return ButtonWidget to add to the panel
     */
    private ButtonWidget<?> createModuleButton(RocketModule module, TileEntityModuleAssembler assembler) {
        return new ButtonWidget<>().size(48, 20)
            .overlay(IKey.str(module.getName()))
            .tooltip(t -> t.add("§7" + String.format("%.1fm | %.0fkg", module.getHeight(), module.getWeight())))
            .syncHandler(
                new InteractionSyncHandler().setOnMousePressed(
                    md -> {
                        if (md.mouseButton == 0 && hasRemaining(module.getId(), assembler))
                            requestModule(module.getId(), assembler);
                    }));
    }

    /**
     * Creates the button for selecting a dimension to travel to
     *
     * @param dim The planet to add an option for
     *
     * @return ButtonWidget to add to the panel
     */
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

    /**
     * Enters the rocket and starts launch cycle (cycle = GO currently)
     *
     * @param data The data from the GUI
     */
    private void enterRocket(PosGuiData data) {
        if (getAssembly().getModules()
            .stream()
            .noneMatch(m -> m instanceof CapsuleModule)) return;
        EntityRocket rocket = getEntityRocket();
        if (rocket == null || rocket.isDead) return;
        rocket.setCapsuleIndex(getFirstCapsuleIndex());
        rocket.setDesination(destination);
        data.getPlayer()
            .mountEntity(rocket);
        if (!rocket.shouldRender()) rocket.launch();
    }

    /**
     * Requests a new module to the silo from an assembler, and removes from
     * assembler map
     *
     * @param id        The module ID to add
     * @param assembler The linked Module Assembler
     */
    public void requestModule(int id, TileEntityModuleAssembler assembler) {
        if (worldObj.isRemote) return;
        assembler.removeModule(id);
        assembler.sendModule(id, this);
        assembly = null;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Receives a new module and adds it to the current render stack
     *
     * @param id The module ID to add
     *
     * @return Boolean : True => Successful reception
     */
    public boolean receiveModule(int id) {
        modules.add(id);
        assembly = null;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return true;
    }

    /**
     * Checks to see if the linked assembler has the module requested
     *
     * @param id        The ID of the module to check
     * @param assembler The linked assembler to check from
     * @return Boolean : True -> has the module
     */
    public boolean hasRemaining(int id, TileEntityModuleAssembler assembler) {
        if (assembler == null) return false;
        return assembler.moduleMap.getOrDefault(id, 0) > 0;
    }

    public void setDesination(int dim) {
        this.destination = dim;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return this.gantryTerminal;
    }

    /**
     * Returns the modules back to the linked assembler. Injects a module into the
     * linked terminal with a return direction
     *
     * @param assembler The Module Assembler tile entity
     */
    public void returnModules(TileEntityModuleAssembler assembler) {
        if (worldObj.isRemote) return;
        for (int id : modules) {
            GantryAPI.injectModule(ModuleRegistry.fromId(id), assembler, this, true);
        }
        modules.clear();

        assembly = null;
        sync();
    }

    public void sync() {
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Gets the RocketAssmebly for this silo or creates a new one
     *
     * @return RocketAssembly
     */
    public RocketAssembly getAssembly() {
        if (assembly == null) assembly = new RocketAssembly(getModules());
        return assembly;
    }

    /**
     * Gets the first capsule index from the modules list
     *
     * @return The index of the first capsule
     */
    public int getFirstCapsuleIndex() {
        List<RocketModule> list = getAssembly().getModules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CapsuleModule) return i;
        }
        return -1;
    }

    /**
     * Starts the launch sequence and updates states
     */
    public void launch() {
        modules.clear();
        shouldRender = true;
        entityRocket = null;
        assembly = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * Spawns the rocket (used to switch from being a TE to an actual entity)
     */
    private void spawnRocket() {
        entityRocket = new EntityRocket(worldObj);
        entityRocket.bindSilo(this);
        entityRocket.setPosition(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
    }

    /**
     * Getter for the rocket entity
     *
     * @return Rocket entity
     */
    public EntityRocket getEntityRocket() {
        return entityRocket;
    }

    /**
     * Gets all modules in the current stack
     *
     * @return ArrayList of modules
     */
    public ArrayList<Integer> getModules() {
        return new ArrayList<>(modules);
    }

    /**
     * Gets the number of modules in the stack
     *
     * @return Number of modules in stack
     */
    public int getNumModules() {
        return modules.size();
    }

    /**
     * Updates the entity once conditions met
     */
    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (shouldRender && (entityRocket == null || entityRocket.isDead)) {
                spawnRocket();
            }

            if (pendingTerminalCoords != null) {
                TileEntity te = worldObj
                    .getTileEntity(pendingTerminalCoords[0], pendingTerminalCoords[1], pendingTerminalCoords[2]);

                if (te instanceof TileEntityGantryTerminal terminal) {
                    gantryTerminal = terminal;
                    updateLinkedAssembler();
                }

                pendingTerminalCoords = null;
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
    }

    /**
     * Invalidation method based on entity state
     */
    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
    }

    /**
     * Returns rendering bounding box
     *
     * @return Bounding box
     */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    /**
     * Gets the max render distance squared
     *
     * @return Max RDS
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    /**
     * Writes TE data to NBT taq
     *
     * @param nbt Tag to write to NBT
     */
    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("shouldRender", shouldRender);
        // gantry
        if (gantryTerminal != null) {
            nbt.setInteger("terminalX", gantryTerminal.xCoord);
            nbt.setInteger("terminalY", gantryTerminal.yCoord);
            nbt.setInteger("terminalZ", gantryTerminal.zCoord);
        }
        // assembler
        if (moduleAssembler != null) {
            nbt.setInteger("assemblerX", moduleAssembler.xCoord);
            nbt.setInteger("assemblerY", moduleAssembler.yCoord);
            nbt.setInteger("assemblerZ", moduleAssembler.zCoord);
        }
        // modules list
        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("type", type);
            list.appendTag(entry);
        }
        nbt.setTag("modules", list);
        nbt.setBoolean("hasAssembler", hasAssembler);

    }

    /**
     * Reads from NBT tag and updates TE state
     *
     * @param nbt Tag to read from
     */
    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        shouldRender = nbt.getBoolean("shouldRender");

        modules.clear();
        NBTTagList list = nbt.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            modules.add(
                list.getCompoundTagAt(i)
                    .getInteger("type"));
        }
        assembly = null;

        // Get Gantry Terminal
        if (nbt.hasKey("terminalX")) {
            pendingTerminalCoords = new int[] { nbt.getInteger("terminalX"), nbt.getInteger("terminalY"),
                nbt.getInteger("terminalZ") };
        }

        // Get Module Assembler
        if (nbt.hasKey("assemblerX")) {
            pendingAssemblerCoords = new int[] { nbt.getInteger("assemblerX"), nbt.getInteger("assemblerY"),
                nbt.getInteger("assemblerZ") };
        }
        hasAssembler = nbt.getBoolean("hasAssembler");

    }

    public void setGantryTerminal(TileEntityGantryTerminal teg) {
        this.gantryTerminal = teg;
        updateLinkedAssembler();
    }

    /**
     * Description packet method used for server side syncing
     *
     * @return The update packet
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    /**
     * Receiver for the packet
     *
     * @param net The NetworkManager the packet came from
     * @param pkt The packet
     */
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
