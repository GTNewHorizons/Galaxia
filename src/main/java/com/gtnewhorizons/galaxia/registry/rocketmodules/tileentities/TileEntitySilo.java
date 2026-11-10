package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaCasingTextures;
import com.gtnewhorizons.galaxia.compat.gt.IRocketHatchHost;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatchElement;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatches;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildOrder;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketDestination;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlight;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlightPlanner;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlights;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketLoadout;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.MachineTerminals;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

import gregtech.api.util.GTStructureUtility;
import lombok.Getter;
import lombok.Setter;

/**
 * Builds, fuels, loads and launches rockets. Modules arrive over the gantry from Module Assemblers, fuel comes from
 * input hatches, cargo from input buses, and cargo of rockets landing here leaves through output buses
 */
public class TileEntitySilo extends GalaxiaMultiblockBase<TileEntitySilo>
    implements IGuiHolder<PosGuiData>, IRocketHatchHost {

    /** Rocket position relative to the controller in structure (a, b, c) coordinates */
    private static final int[] ROCKET_OFFSET_ABC = { 0, -1, 3 };
    /** Fuel pumped from the input hatches per tick, in mB */
    private static final int FUEL_PER_TICK = 1_000;
    /** Items moved per cargo transfer */
    private static final int ITEMS_PER_TRANSFER = 64;
    /** Ticks between cargo transfers and automation checks */
    private static final int TRANSFER_INTERVAL = 10;
    /** Automation launches a partly loaded rocket once nothing could be loaded for this long */
    private static final int LOADING_TIMEOUT = 200;
    /** Wait after a refused automated launch */
    private static final int LAUNCH_RETRY = 100;

    public static final String PROBLEM_PREFIX = "galaxia.silo.problem.";

    private EntityRocket entityRocket;
    public boolean shouldRender = true;

    @Getter
    private String siloName = "";

    @Getter
    private RocketDestination destination = RocketDestination.NONE;

    @Getter
    @Setter
    private String pendingSchematicName = "";

    private final MachineTerminals terminals = new MachineTerminals();

    @Getter
    private final RocketHatches hatches = new RocketHatches();

    @Getter
    private final SiloAutomation automation = new SiloAutomation();

    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** What the player has drawn in the editor. Mutable only while canEdit(). */
    @Getter
    private RocketBlueprint designBlueprint = new RocketBlueprint();

    /**
     * Immutable snapshot created at order time. Drives the assembler's work list.
     * Null when not in ASSEMBLING state.
     */
    @Getter
    private RocketBuildOrder currentBuildOrder = null;

    /**
     * Accumulated result of confirmed deliveries. Only updated in receiveModulePart.
     * Becomes authoritative source for the EntityRocket once READY.
     */
    private RocketBlueprint assembledBlueprint = new RocketBlueprint();

    /** Fuel and cargo of the rocket standing in the silo */
    @Getter
    private RocketLoadout loadout = new RocketLoadout();

    @Getter
    private RocketBuildStatus buildStatus = RocketBuildStatus.IDLE;

    /** The rocket landed here and its cargo is still being unloaded */
    @Getter
    private boolean unloading = false;

    /** Lang key of the last reason a launch or order failed, empty when fine */
    @Getter
    private String lastProblem = "";

    private int transferTimer = 0;
    /** Ticks until automation tries again after a launch was refused, avoids loading the destination every check */
    private int launchCooldown = 0;

    /**
     * Returns the assembled blueprint only when the rocket is actually built or
     * launched. Returns an empty blueprint otherwise so callers can't render a
     * half-built rocket.
     */
    public RocketBlueprint getBuiltBlueprint() {
        return buildStatus == RocketBuildStatus.READY ? assembledBlueprint : new RocketBlueprint();
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
     * Creates an immutable build order from the current design and asks the assemblers on the gantry network for
     * every part at once. Each part goes to the assembler that has it in stock, or the least busy one otherwise
     *
     * @return Boolean : True => the order was placed
     */
    public boolean orderModules() {
        if (worldObj.isRemote) return false;

        if (designBlueprint.isEmpty()) return fail("empty_blueprint");
        if (buildStatus == RocketBuildStatus.IDLE) buildStatus = RocketBuildStatus.DESIGNED;
        if (!buildStatus.canOrder()) return fail("busy");
        TileEntityGantryTerminal gantryTerminal = getGantryTerminal();
        if (gantryTerminal == null) return fail("no_terminal");
        if (!gantryTerminal.getMode().delivers) return fail("terminal_input_only");

        RocketAssembly analysis = designBlueprint.analyze();
        if (!analysis.viable()) return fail("not_viable");

        // Check every part has somewhere to come from before promising anything
        List<TileEntityGantryTerminal> providers = new ArrayList<>();
        for (RocketPartInstance part : designBlueprint.getParts()) {
            TileEntityGantryTerminal provider = GantryAPI.findProvider(gantryTerminal, part.def());
            if (provider == null) return fail("no_assembler");
            providers.add(provider);
        }

        this.currentBuildOrder = new RocketBuildOrder(designBlueprint);
        this.buildStatus = RocketBuildStatus.ASSEMBLING;
        this.assembledBlueprint.clear();
        this.loadout.clear();

        List<RocketPartInstance> parts = designBlueprint.getParts();
        for (int i = 0; i < parts.size(); i++) {
            providers.get(i)
                .getAssembler()
                .requestDelivery(parts.get(i), gantryTerminal);
        }

        lastProblem = "";
        sync();
        return true;
    }

    private boolean fail(String problem) {
        String key = PROBLEM_PREFIX + problem;
        if (!key.equals(lastProblem)) {
            lastProblem = key;
            sync();
        }
        return false;
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
     * This is the only place that writes to assembledBlueprint. Parts nobody ordered any more are sent straight
     * back to an assembler
     *
     * @return Boolean : True => the part was taken off the gantry
     */
    public boolean receiveModulePart(RocketPartInstance part) {
        if (currentBuildOrder != null && currentBuildOrder.markDelivered(part)) {
            // Modules arrive in whatever order the assemblers finish them, a part may not touch any delivered one yet.
            // The design was checked when it was ordered, so the part goes in as it is
            assembledBlueprint.addPartUnchecked(part);

            if (currentBuildOrder.isComplete()) {
                buildStatus = RocketBuildStatus.READY;
                currentBuildOrder = null;
                loadout.clear();
            }
            sync();
            return true;
        }
        TileEntityGantryTerminal gantryTerminal = getGantryTerminal();
        return GantryAPI.send(part, gantryTerminal, GantryAPI.findReturnPoint(gantryTerminal));
    }

    private static final IStructureDefinition<TileEntitySilo> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntitySilo>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
                new String[][]{
                    {"         ","         ","         ","         ","         ","         ","         ","         ","  DDDDD  ","   C C   ","  EE~EE  ","  EEEEE  "},
                    {"         ","         ","         ","         ","         ","         ","         ","         "," D     D ","         "," EAAAAAE "," EAAAAAE "},
                    {" C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," D     D "," C     C ","EEAAAAAEE","EEAAAAAEE"},
                    {"CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","TB     BT","EEAAAAAEE","EEAAAAAEE"},
                    {" D     D "," C     C "," C     C "," C     C "," D     D "," C     C "," C     C "," C     C "," D     D "," C     C ","EEAAAAAEE","EEAAAAAEE"},
                    {" D     D ","         ","         ","         "," D     D ","         ","         ","         "," D     D ","         "," EAAAAAE "," EAAAAAE "},
                    {"  DDBDD  ","   CBC   ","   CBC   ","   CBC   ","  DDBDD  ","   CBC   ","   CBC   ","   CBC   ","  DDBDD  ","   DBD   ","  EEEEE  ","  EEEEE  "},
                    {"    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    T    ","   EEE   ","   EEE   "}
                })
        // spotless:on
        .addElement('A', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_CASING.get(), 0))
        .addElement('B', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_ASSEMBLING_CASING.get(), 0))
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_FRAMEBOX.get(), 0))
        .addElement('D', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_REINFORCEMENT.get(), 0))
        .addElement(
            'E',
            GTStructureUtility.<TileEntitySilo>buildHatchAdder()
                .anyOf(RocketHatchElement.InputBus, RocketHatchElement.InputHatch, RocketHatchElement.OutputBus)
                .casingIndex(GalaxiaCasingTextures.LAUNCHPAD_SHEETING)
                .hint(1)
                .buildAndChain(StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((silo, te) -> {
            if (te instanceof TileEntityGantryTerminal terminal) {
                silo.setGantryTerminal(terminal);
                terminal.connectSilo(silo);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
        .build();

    public TileEntitySilo() {
        super();
    }

    @Override
    public IStructureDefinition<TileEntitySilo> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    // Mirroring is handy to fit the gantry terminals to an existing rail layout
    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return HORIZONTAL_MIRRORABLE;
    }

    @Override
    protected boolean checkStructure() {
        if (worldObj != null && !worldObj.isRemote) {
            hatches.clear();
            terminals.clear();
        }
        return super.checkStructure();
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        terminals.add(terminal);
    }

    /** The terminal the gantry rails are attached to, null when the silo has none */
    public TileEntityGantryTerminal getGantryTerminal() {
        return terminals.best();
    }

    @Override
    protected int getControllerOffsetX() {
        return 4;
    }

    @Override
    protected int getControllerOffsetY() {
        return 10;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    protected void onStructureFormed() {
        super.onStructureFormed();
        shouldRender = true;
        if (siloName.isEmpty()) siloName = "Silo " + xCoord + " " + zCoord;
        RocketFlights.registerSilo(worldObj.provider.dimensionId, getPos(), siloName);
    }

    @Override
    protected void onStructureDisformed() {
        super.onStructureDisformed();
        shouldRender = false;
    }

    @Override
    protected void onFacingChanged() {
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
        entityRocket = null;
    }

    /**
     * Offset from the controller to the block the rocket stands on, following the silo's rotation and mirroring
     *
     * @return x, y, z offset in world coordinates
     */
    public int[] getRocketOffset() {
        int[] offset = new int[3];
        currentFacing.getWorldOffset(ROCKET_OFFSET_ABC, offset);
        return offset;
    }

    public BlockPos getPos() {
        return new BlockPos(xCoord, yCoord, zCoord);
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.SILO_CONTROLLER.get();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return RocketEditorUI.build(data, syncManager, settings);
    }

    public void setSiloName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.equals(siloName)) return;
        siloName = trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
        if (structureValid) RocketFlights.registerSilo(worldObj.provider.dimensionId, getPos(), siloName);
        sync();
    }

    public void setDestination(RocketDestination destination) {
        this.destination = destination == null ? RocketDestination.NONE : destination;
        sync();
    }

    // Launching

    /**
     * Works out whether the rocket in the silo can reach its destination right now
     *
     * @return The plan, or null with {@link #getLastProblem()} explaining why not
     */
    public RocketFlightPlanner.FlightPlan planFlight() {
        if (!buildStatus.canLaunch() || assembledBlueprint.isEmpty()) return failPlan("no_rocket");
        if (unloading) return failPlan("unloading");
        if (!destination.isSet()) return failPlan("no_destination");

        int dimension = worldObj.provider.dimensionId;
        RocketAssembly assembly = assembledBlueprint.analyze();
        if (!RocketFlightPlanner
            .canLiftOff(assembly.stages(), loadout, RocketFlightPlanner.surfaceGravity(dimension))) {
            return failPlan("too_heavy");
        }

        RocketFlightPlanner.FlightPlan plan = RocketFlightPlanner
            .plan(assembly, loadout, RocketFlightPlanner.requiredDeltaV(dimension, destination.dimension()));
        if (!plan.reachable()) return failPlan("not_enough_delta_v");

        if (destination.isSilo()) {
            if (destination.dimension() == dimension && destination.silo()
                .equals(getPos())) return failPlan("destination_self");
            if (RocketFlights.isReserved(destination)) return failPlan("destination_busy");
            TileEntitySilo target = RocketFlights.loadSilo(destination.dimension(), destination.silo());
            if (target == null) return failPlan("destination_missing");
            if (!target.canReceiveRocket()) return failPlan("destination_busy");
        }

        lastProblem = "";
        return plan;
    }

    private RocketFlightPlanner.FlightPlan failPlan(String problem) {
        fail(problem);
        return null;
    }

    /**
     * Sends the rocket off without a crew. Only silos can receive uncrewed rockets
     *
     * @return Boolean : True => the rocket is on its way
     */
    public boolean launchUncrewed() {
        if (worldObj.isRemote) return false;
        if (entityRocket != null && entityRocket.hasPassengers()) return fail("crew_aboard");
        if (!destination.isSilo()) return fail("uncrewed_needs_silo");
        RocketFlightPlanner.FlightPlan plan = planFlight();
        if (plan == null) return false;

        RocketFlight flight = createFlight(plan);
        automation.onLaunched(cargoOf(flight.getBlueprint(), flight.getLoadout()));
        RocketFlights.dispatch(flight);

        if (entityRocket != null && !entityRocket.isDead) entityRocket.launchUncrewed();
        entityRocket = null;
        onRocketLaunched();
        return true;
    }

    /**
     * Called when a crewed rocket ignites. Plans the flight and hands the rocket's state over to the entity
     *
     * @param rocket The rocket entity carrying the crew
     *
     * @return Boolean : True => the launch may go ahead
     */
    public boolean beginCrewedLaunch(EntityRocket rocket) {
        RocketFlightPlanner.FlightPlan plan = planFlight();
        if (plan == null) return false;

        rocket.prepareFlight(createFlight(plan));
        if (rocket == entityRocket) entityRocket = null;
        onRocketLaunched();
        return true;
    }

    /** Builds the flight record of what survives the burn */
    private RocketFlight createFlight(RocketFlightPlanner.FlightPlan plan) {
        RocketAssembly assembly = assembledBlueprint.analyze();
        List<RocketPartInstance> survivors = plan.survivingParts(assembly);
        RocketBlueprint arriving = new RocketBlueprint();
        arriving.setName(assembledBlueprint.getName());
        for (RocketPartInstance part : survivors) arriving.addPartUnchecked(part);

        RocketLoadout remaining = plan.remaining();
        remaining.retainOnly(survivors);

        int dimension = worldObj.provider.dimensionId;
        return new RocketFlight(
            UUID.randomUUID(),
            arriving,
            remaining,
            dimension,
            getPos(),
            destination,
            RocketFlights.now() + RocketFlightPlanner.travelTicks(dimension, destination.dimension()));
    }

    public void onRocketLaunched() {
        if (worldObj.isRemote) return;

        this.assembledBlueprint.clear();
        this.loadout.clear();
        this.currentBuildOrder = null;
        this.buildStatus = designBlueprint.isEmpty() ? RocketBuildStatus.IDLE : RocketBuildStatus.DESIGNED;
        this.shouldRender = true;
        sync();
    }

    // Receiving

    /**
     * Checks that the pad is free for a landing rocket
     *
     * @return Boolean : True => nothing is built, being built or standing here
     */
    public boolean canReceiveRocket() {
        return structureValid && buildStatus.canEdit() && currentBuildOrder == null && assembledBlueprint.isEmpty();
    }

    /**
     * Puts a rocket that flew here onto the pad. Its cargo is unloaded into the output buses
     *
     * @param flight The arriving rocket
     */
    public void receiveRocket(RocketFlight flight) {
        assembledBlueprint = flight.getBlueprint()
            .copy();
        designBlueprint = flight.getBlueprint()
            .copy();
        loadout = flight.getLoadout()
            .copy();
        currentBuildOrder = null;
        buildStatus = RocketBuildStatus.READY;
        unloading = loadout.hasCargo();
        shouldRender = true;
        if (!unloading && automation.isReturnArrivals()) returnModules();
        sync();
    }

    /**
     * Lets a landing rocket entity become the rocket standing in this silo
     *
     * @param rocket The rocket that touched down on the pad
     */
    public void adoptRocketEntity(EntityRocket rocket) {
        if (entityRocket != null && entityRocket != rocket && !entityRocket.isDead) entityRocket.setDead();
        entityRocket = rocket;
    }

    /**
     * Called when a rocket this silo launched reaches its destination
     *
     * @param flight The rocket that landed
     */
    public void onFlightLanded(RocketFlight flight) {
        automation.onLanded(cargoOf(flight.getBlueprint(), flight.getLoadout()));
        markDirty();
    }

    private static List<ItemStack> cargoOf(RocketBlueprint blueprint, RocketLoadout loadout) {
        List<ItemStack> cargo = new ArrayList<>();
        for (RocketPartInstance part : blueprint.getParts()) {
            if (!(part.def() instanceof StoragePartDef)) continue;
            for (ItemStack stack : loadout.getCargo(part)) {
                if (stack != null) cargo.add(stack.copy());
            }
        }
        return cargo;
    }

    // Crew

    public void enterRocket(PosGuiData data) {
        if (!buildStatus.canLaunch() || assembledBlueprint.isEmpty()) return;

        boolean isNew = (entityRocket == null || entityRocket.isDead);
        EntityRocket rocket = getOrCreateEntityRocket();
        if (rocket == null) return;

        rocket.setBlueprint(assembledBlueprint);
        rocket.setTargetSilo(this);
        rocket.initializeSeats();

        if (isNew) {
            worldObj.spawnEntityInWorld(rocket);
        }

        sync();
        rocket.interactFirst(data.getPlayer());
    }

    /**
     * Sends every module of the rocket, built or still on order, back to an assembler. Cargo has to be taken out
     * first so nothing is destroyed by accident
     *
     * @return Boolean : True => the silo is empty again
     */
    public boolean returnModules() {
        if (worldObj.isRemote) return false;
        if (loadout.hasCargo()) return fail("cargo_aboard");
        TileEntityGantryTerminal gantryTerminal = getGantryTerminal();
        if (gantryTerminal == null) return fail("no_terminal");

        for (var assembler : GantryAPI.findAssemblers(gantryTerminal)) {
            assembler.cancelDeliveries(gantryTerminal);
        }
        TileEntityGantryTerminal target = GantryAPI.findReturnPoint(gantryTerminal);
        if (target == null && !assembledBlueprint.isEmpty()) return fail("no_assembler");
        for (RocketPartInstance part : assembledBlueprint.getParts()) {
            GantryAPI.send(part, gantryTerminal, target);
        }

        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
        entityRocket = null;
        this.assembledBlueprint.clear();
        this.loadout.clear();
        this.currentBuildOrder = null;
        this.unloading = false;
        this.buildStatus = designBlueprint.isEmpty() ? RocketBuildStatus.IDLE : RocketBuildStatus.DESIGNED;
        this.shouldRender = true;
        lastProblem = "";
        sync();
        return true;
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
        int[] offset = getRocketOffset();
        entityRocket.setPosition(xCoord + offset[0] + 0.5, yCoord + offset[1], zCoord + offset[2] + 0.5);
        return entityRocket;
    }

    public void sync() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    // Ticking

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        if (shouldRender && (entityRocket == null || entityRocket.isDead)
            && structureValid
            && buildStatus == RocketBuildStatus.READY) {
            EntityRocket rocket = getOrCreateEntityRocket();
            rocket.setBlueprint(assembledBlueprint);
            rocket.setTargetSilo(this);
            worldObj.spawnEntityInWorld(rocket);
        }

        if (!structureValid || buildStatus != RocketBuildStatus.READY) {
            if (structureValid && ++transferTimer >= TRANSFER_INTERVAL) {
                transferTimer = 0;
                tickAutomation();
            }
            return;
        }

        pumpFuel();
        if (++transferTimer >= TRANSFER_INTERVAL) {
            transferTimer = 0;
            if (unloading) unloadCargo();
            else loadCargo();
            tickAutomation();
        }
    }

    /** Fills every tank from input hatches holding its fuel */
    private void pumpFuel() {
        int budget = FUEL_PER_TICK;
        boolean changed = false;
        for (RocketPartInstance part : assembledBlueprint.getParts()) {
            if (budget <= 0) break;
            if (!(part.def() instanceof FuelTankPartDef tank)) continue;
            int stored = loadout.getFuel(part);
            int missing = tank.fuelCapacity() - stored;
            Fluid fluid = tank.fuel()
                .fluid();
            if (missing <= 0 || fluid == null) continue;
            int drained = hatches.drainFluid(worldObj, new FluidStack(fluid, Math.min(missing, budget)));
            if (drained <= 0) continue;
            loadout.setFuel(part, stored + drained);
            budget -= drained;
            changed = true;
        }
        if (changed) markDirty();
    }

    /**
     * Moves items from the input buses into the cargo holds. Under automation only requested items are loaded, and
     * no more than are still missing at the destination
     */
    private void loadCargo() {
        int budget = ITEMS_PER_TRANSFER;
        boolean automated = automation.isEnabled();
        for (RocketPartInstance part : assembledBlueprint.getParts()) {
            if (budget <= 0 || !(part.def() instanceof StoragePartDef)) continue;
            ItemStack[] slots = loadout.getCargo(part);
            int before = budget;
            budget -= fillHold(slots, budget, automated);
            if (budget != before) {
                automation.setIdleTicks(0);
                markDirty();
            }
        }
    }

    /**
     * Takes items from the input buses into one hold, never more than fits. Under automation only what requests
     * still miss is taken
     *
     * @return Number of items moved
     */
    private int fillHold(ItemStack[] slots, int budget, boolean automated) {
        int moved = 0;
        for (ItemStack candidate : hatches.peekItems(worldObj)) {
            if (moved >= budget) break;
            int wanted = Math.min(budget - moved, freeSpaceFor(slots, candidate));
            if (automated) wanted = Math.min(wanted, missingForRequest(candidate));
            if (wanted <= 0) continue;

            for (ItemStack stack : hatches.extractItems(
                worldObj,
                s -> candidate.isItemEqual(s) && ItemStack.areItemStackTagsEqual(candidate, s),
                wanted)) {
                moved += stack.stackSize;
                insert(slots, stack);
            }
        }
        return moved;
    }

    /** How many more of an item automation still wants on board */
    private int missingForRequest(ItemStack stack) {
        SiloAutomation.Request request = automation.findOpenRequest(stack);
        if (request == null) return 0;
        int aboard = 0;
        for (ItemStack cargo : cargoOf(assembledBlueprint, loadout)) {
            if (request.matches(cargo)) aboard += cargo.stackSize;
        }
        return request.remaining() - aboard;
    }

    private static int freeSpaceFor(ItemStack[] slots, ItemStack stack) {
        int limit = Math.min(stack.getMaxStackSize(), 64);
        int free = 0;
        for (ItemStack slot : slots) {
            if (slot == null) free += limit;
            else if (slot.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(slot, stack))
                free += limit - slot.stackSize;
        }
        return free;
    }

    private static void insert(ItemStack[] slots, ItemStack stack) {
        int limit = Math.min(stack.getMaxStackSize(), 64);
        for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
            ItemStack slot = slots[i];
            if (slot == null || !slot.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(slot, stack)) continue;
            int move = Math.min(limit - slot.stackSize, stack.stackSize);
            slot.stackSize += move;
            stack.stackSize -= move;
        }
        for (int i = 0; i < slots.length && stack.stackSize > 0; i++) {
            if (slots[i] != null) continue;
            int move = Math.min(limit, stack.stackSize);
            slots[i] = stack.copy();
            slots[i].stackSize = move;
            stack.stackSize -= move;
        }
    }

    /** Empties the holds of a landed rocket into the output buses */
    private void unloadCargo() {
        int budget = ITEMS_PER_TRANSFER;
        for (RocketPartInstance part : assembledBlueprint.getParts()) {
            if (!(part.def() instanceof StoragePartDef)) continue;
            ItemStack[] slots = loadout.getCargo(part);
            for (int i = 0; i < slots.length && budget > 0; i++) {
                if (slots[i] == null) continue;
                ItemStack moving = slots[i].copy();
                moving.stackSize = Math.min(moving.stackSize, budget);
                int before = moving.stackSize;
                hatches.storeItem(worldObj, moving);
                int stored = before - moving.stackSize;
                if (stored <= 0) continue;
                budget -= stored;
                slots[i].stackSize -= stored;
                if (slots[i].stackSize <= 0) slots[i] = null;
                markDirty();
            }
        }

        if (!loadout.hasCargo()) {
            unloading = false;
            if (automation.isReturnArrivals()) returnModules();
            sync();
        }
    }

    /**
     * Factorio-style schedule: keep building the schematic, load what is still requested and launch whenever the
     * holds are full, every request is aboard, or loading has stalled. Stops once everything has been delivered
     */
    private void tickAutomation() {
        SiloAutomation.Status status = evaluateAutomation();
        if (status != automation.getStatus()) {
            automation.setStatus(status);
            sync();
        }
    }

    private SiloAutomation.Status evaluateAutomation() {
        if (!automation.isEnabled()) return SiloAutomation.Status.DISABLED;
        if (!automation.hasRequests()) return SiloAutomation.Status.NO_REQUESTS;
        if (automation.isComplete()) return SiloAutomation.Status.COMPLETE;
        if (!destination.isSilo()) return SiloAutomation.Status.NO_DESTINATION;

        if (buildStatus == RocketBuildStatus.READY) return tickReadyRocket();
        if (buildStatus == RocketBuildStatus.ASSEMBLING) return SiloAutomation.Status.BUILDING;

        // Nothing left to load, the rest is already on its way
        if (automation.totalRemaining() <= 0) return SiloAutomation.Status.IN_FLIGHT;

        RocketBlueprint schematic = automation.getSchematicBlueprint();
        if (schematic == null || schematic.isEmpty()) return SiloAutomation.Status.NO_SCHEMATIC;
        if (!schematic.analyze()
            .viable()) return SiloAutomation.Status.INVALID_SCHEMATIC;

        designBlueprint = schematic.copy();
        buildStatus = RocketBuildStatus.DESIGNED;
        return orderModules() ? SiloAutomation.Status.BUILDING : SiloAutomation.Status.NO_ASSEMBLER;
    }

    private SiloAutomation.Status tickReadyRocket() {
        if (unloading) return SiloAutomation.Status.LOADING;

        RocketAssembly assembly = assembledBlueprint.analyze();
        double required = RocketFlightPlanner.requiredDeltaV(worldObj.provider.dimensionId, destination.dimension());
        boolean tanksFull = loadout.getTotalFuel(assembledBlueprint)
            >= RocketLoadout.getFuelCapacity(assembledBlueprint);
        boolean enoughFuel = RocketFlightPlanner.plan(assembly, loadout, required)
            .reachable();
        if (!enoughFuel) {
            return tanksFull ? SiloAutomation.Status.NOT_ENOUGH_DELTA_V : SiloAutomation.Status.FUELING;
        }

        boolean holdsFull = true;
        boolean anything = false;
        for (RocketPartInstance part : assembledBlueprint.getParts()) {
            if (!(part.def() instanceof StoragePartDef)) continue;
            for (ItemStack stack : loadout.getCargo(part)) {
                if (stack == null) holdsFull = false;
                else anything = true;
            }
        }

        boolean allAboard = true;
        for (SiloAutomation.Request request : automation.getRequests()) {
            if (request.getTemplate() != null && missingForRequest(request.getTemplate()) > 0) allAboard = false;
        }

        automation.setIdleTicks(automation.getIdleTicks() + TRANSFER_INTERVAL);
        boolean stalled = anything && automation.getIdleTicks() >= LOADING_TIMEOUT;
        if (!(holdsFull || allAboard || stalled)) return SiloAutomation.Status.LOADING;

        if (launchCooldown > 0) {
            launchCooldown -= TRANSFER_INTERVAL;
            return automation.getStatus();
        }
        if (!launchUncrewed()) {
            launchCooldown = LAUNCH_RETRY;
            return lastProblem.endsWith("destination_busy") ? SiloAutomation.Status.DESTINATION_BUSY
                : SiloAutomation.Status.NOT_ENOUGH_DELTA_V;
        }
        automation.setIdleTicks(0);
        return SiloAutomation.Status.IN_FLIGHT;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
    }

    /** Called by the block when it is broken, so other silos stop offering it as a destination */
    public void onBroken() {
        if (worldObj.isRemote) return;
        RocketFlights.unregisterSilo(worldObj.provider.dimensionId, getPos());
        TileEntityGantryTerminal gantryTerminal = getGantryTerminal();
        if (gantryTerminal != null) {
            for (var assembler : GantryAPI.findAssemblers(gantryTerminal)) {
                assembler.cancelDeliveries(gantryTerminal);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setBoolean("shouldRender", shouldRender);

        nbt.setInteger("buildStatus", buildStatus.ordinal());
        nbt.setTag("designBlueprint", designBlueprint.serializeNBT());
        nbt.setTag("assembledBlueprint", assembledBlueprint.serializeNBT());
        nbt.setTag("loadout", loadout.serializeNBT());
        nbt.setBoolean("unloading", unloading);

        if (currentBuildOrder != null) {
            nbt.setTag("buildOrder", currentBuildOrder.serializeNBT());
        }

        nbt.setString("siloName", siloName);
        nbt.setTag("destination", destination.serializeNBT());
        nbt.setString("pendingSchematicName", pendingSchematicName);
        nbt.setTag("automation", automation.serializeNBT());
        nbt.setString("lastProblem", lastProblem);
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
        loadout = RocketLoadout.deserializeNBT(nbt.getCompoundTag("loadout"));
        unloading = nbt.getBoolean("unloading");

        currentBuildOrder = nbt.hasKey("buildOrder")
            ? RocketBuildOrder.deserializeNBT(nbt.getCompoundTag("buildOrder"), RocketPartRegistry.instance())
            : null;

        siloName = nbt.getString("siloName");
        destination = RocketDestination.deserializeNBT(nbt.getCompoundTag("destination"));
        pendingSchematicName = nbt.getString("pendingSchematicName");
        if (nbt.hasKey("automation")) automation.deserializeNBT(nbt.getCompoundTag("automation"));
        lastProblem = nbt.getString("lastProblem");
    }
}
