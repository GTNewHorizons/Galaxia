package com.gtnewhorizons.galaxia.registry.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.MathUtils;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.gt.handlers.GTHatchEnergySourceHandler;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTStructureUtility;

/** Owns physical dock processing and powered capsule life support. Content supplies the recipes. */
public abstract class TileEntityBodyStation extends GalaxiaMultiblockBase<TileEntityBodyStation>
    implements IGuiHolder<PosGuiData>, IInventory, IFluidHandler {

    private static final int INPUT_SLOTS = 6;
    private static final long ENERGY_CAPACITY = 1_000_000;
    private static final GTHatchEnergySourceHandler ENERGY_SOURCE = new GTHatchEnergySourceHandler();
    private static final double INTERIOR_BOUNDARY_TOLERANCE = .0001;
    private static final double DOOR_APPROACH_RANGE = 1.5;
    private static final int FLUID_CAPACITY = 16_000;
    private static final int PANEL_WIDTH = 244;
    private static final int PANEL_HEIGHT = 256;
    private static final int PANEL_PADDING = 8;
    private static final int INVENTORY_BOTTOM = 7;
    private static final int CONTROL_WIDTH = PANEL_WIDTH - 2 * PANEL_PADDING;
    private static final int CONTROL_HEIGHT = 112;
    private static final int SELECTION_LIST_HEIGHT = 96;
    private static final int BODY_ROW_HEIGHT = 24;
    private static final int BODY_BUTTON_WIDTH = 166;
    private static final int RENAME_BUTTON_WIDTH = 58;
    private static final int NAME_PANEL_WIDTH = 228;
    private static final int NAME_PANEL_HEIGHT = 100;
    private static final int NAME_BUTTON_WIDTH = 100;
    private static final int NAME_BUTTON_HEIGHT = 22;
    private static final int NAME_ACTION_Y = 62;
    private static final int BODY_ID_PREFIX_LENGTH = 8;
    private static final int DOCK_CONTENT_Y = 58;
    private static final int DOCK_CONTENT_HEIGHT = 84;
    private static final int DOCK_GRID_COLUMNS = 3;
    private static final int DOCK_SLOT_PITCH = 20;
    private static final int DOCK_GRID_Y = 26;
    private static final int DOCK_BODY_X = 156;
    private static final int DOCK_BODY_Y = 10;
    private static final int DOCK_BODY_WIDTH = 48;
    private static final int DOCK_BODY_HEIGHT = 64;
    private static final int DOCK_HINT_Y = 146;
    private static final int DOCK_ENERGY_BAR_Y = 32;
    private static final int DOCK_ENERGY_BAR_HEIGHT = 4;
    private static final int DOCK_STATUS_Y = 40;
    private static final int CAPSULE_STATUS_Y = 36;

    /** Atlas regions in assembly order. The torso region also includes the neck. */
    private enum AssemblyPart {

        LEFT_ARM(4, 18, 8, 26),
        TORSO(14, 16, 20, 26),
        RIGHT_ARM(36, 18, 8, 26),
        LEFT_LEG(14, 44, 8, 20),
        RIGHT_LEG(26, 44, 8, 20),
        HEAD(16, 0, 16, 16);

        private final int x;
        private final int y;
        private final int width;
        private final int height;

        AssemblyPart(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final RecipeMetadataKey<AndroidBodies.Chassis> CHASSIS = SimpleRecipeMetadataKey
        .create(AndroidBodies.Chassis.class, "galaxia_android_chassis");

    public static final RecipeMap<RecipeMapBackend> MANUFACTURING_RECIPES = RecipeMapBuilder
        .of("galaxia.android.recipe.manufacture")
        .maxIO(INPUT_SLOTS, 0, 1, 0)
        .minInputs(1, 0)
        .build();
    public static final RecipeMap<RecipeMapBackend> REPAIR_RECIPES = RecipeMapBuilder
        .of("galaxia.android.recipe.repair")
        .maxIO(INPUT_SLOTS, 0, 1, 0)
        .minInputs(1, 0)
        .build();

    public enum Kind {

        DOCK("android_dock", "galaxia.android.station.dock"),
        CAPSULE("control_capsule", "galaxia.android.station.capsule");

        public final String blockName;
        private final String text;
        private IStructureDefinition<TileEntityBodyStation> structure;

        Kind(String blockName, String text) {
            this.blockName = blockName;
            this.text = text;
        }

        private IStructureDefinition<TileEntityBodyStation> structure() {
            if (structure == null) {
                String[][] shape = { { "PPP", "PDP", "PDP", "PPP" }, { "GGG", "G-G", "G-G", "PPP" },
                    { "GGG", "GGG", "G~G", "PEP" } };
                structure = StructureDefinition.<TileEntityBodyStation>builder()
                    .addShape("main", shape)
                    .addElement('P', StructureUtility.ofBlock(GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(), 0))
                    .addElement('G', StructureUtility.ofBlock(GalaxiaBlocksEnum.SPACE_STATION_GLASS.get(), 0))
                    .addElement(
                        'E',
                        GTStructureUtility.ofHatchAdder((tile, hatch, casing) -> tile.addEnergyInput(hatch), 0, 1))
                    .addElement(
                        'D',
                        GalaxiaStructureUtility.ofBlockWithMeta(
                            GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                            0,
                            (tile,
                                meta) -> BlockAirlockDoor.getOrientation(meta) == BlockAirlockDoor.orientationForAxis(
                                    tile.getCurrentFacing()
                                        .getRelativeBackInWorld()),
                            tile -> BlockAirlockDoor.encodeMeta(
                                false,
                                BlockAirlockDoor.orientationForAxis(
                                    tile.getCurrentFacing()
                                        .getRelativeBackInWorld()))))
                    .build();
            }
            return structure;
        }
    }

    public static class Dock extends TileEntityBodyStation {

        public Dock() {
            super(Kind.DOCK);
        }
    }

    public static class Capsule extends TileEntityBodyStation {

        public Capsule() {
            super(Kind.CAPSULE);
        }
    }

    private final Kind kind;
    private UUID stationId = UUID.randomUUID();
    private UUID ownerTeam;
    private long energy;
    private boolean powered;
    private boolean operationFailed;
    private Job job;
    private BlockPos energyHatchPosition;
    private final ItemStackHandler inputs = new ItemStackHandler(INPUT_SLOTS) {

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };
    private final FluidTank fluids = new FluidTank(FLUID_CAPACITY);

    protected TileEntityBodyStation(Kind kind) {
        this.kind = kind;
    }

    @Nonnull
    public AndroidBodies.Anchor anchor() {
        return new AndroidBodies.Anchor(stationId, worldObj.provider.dimensionId, xCoord, yCoord, zCoord);
    }

    public boolean isCapsule() {
        return kind == Kind.CAPSULE;
    }

    public BodyState captureOccupant(EntityPlayer player) {
        AxisAlignedBB interior = interior();
        return BodyState.capture(player)
            .restingAt((interior.minX + interior.maxX) / 2, interior.minY, (interior.minZ + interior.maxZ) / 2);
    }

    private AxisAlignedBB interior() {
        ForgeDirection back = getCurrentFacing().getRelativeBackInWorld();
        int x = xCoord - back.offsetX;
        int z = zCoord - back.offsetZ;
        return AxisAlignedBB.getBoundingBox(x, yCoord, z, x + 1, yCoord + 2, z + 1);
    }

    public boolean contains(Entity occupant) {
        return occupant.worldObj == worldObj && contains(occupant.boundingBox);
    }

    public enum ConnectionStatus {
        NONE,
        CAPSULE_READY,
        CAPSULE_OFFLINE,
        DOCK_CONNECTED,
        DOCK_OFFLINE,
        WRONG_DOCK
    }

    public ConnectionStatus connectionStatus(EntityPlayer player) {
        if (player.isDead || !contains(player) || !canOperate(player)) return ConnectionStatus.NONE;
        boolean android = AndroidControl.isAndroid(player);
        if (isCapsule()) {
            if (android) return ConnectionStatus.NONE;
            return isPowered() ? ConnectionStatus.CAPSULE_READY : ConnectionStatus.CAPSULE_OFFLINE;
        }
        if (!android) return ConnectionStatus.NONE;
        var body = AndroidControl.bodies()
            .session(player.getUniqueID())
            .flatMap(
                session -> AndroidControl.bodies()
                    .body(session.bodyId()))
            .orElseThrow();
        if (!body.dock()
            .equals(anchor())) return ConnectionStatus.WRONG_DOCK;
        return isPowered() ? ConnectionStatus.DOCK_CONNECTED : ConnectionStatus.DOCK_OFFLINE;
    }

    private boolean contains(AxisAlignedBB bounds) {
        AxisAlignedBB interior = interior();
        return bounds.minX >= interior.minX - INTERIOR_BOUNDARY_TOLERANCE
            && bounds.maxX <= interior.maxX + INTERIOR_BOUNDARY_TOLERANCE
            && bounds.minY >= interior.minY - INTERIOR_BOUNDARY_TOLERANCE
            && bounds.maxY <= interior.maxY + INTERIOR_BOUNDARY_TOLERANCE
            && bounds.minZ >= interior.minZ - INTERIOR_BOUNDARY_TOLERANCE
            && bounds.maxZ <= interior.maxZ + INTERIOR_BOUNDARY_TOLERANCE;
    }

    private boolean contains(BodyState state) {
        return state.dimension() == worldObj.provider.dimensionId && contains(
            AxisAlignedBB.getBoundingBox(
                state.x() - .3,
                state.y(),
                state.z() - .3,
                state.x() + .3,
                state.y() + 1.8,
                state.z() + .3));
    }

    BodyState manufacturedBodyState() {
        AxisAlignedBB interior = interior();
        ForgeDirection front = getCurrentFacing().getRelativeBackInWorld()
            .getOpposite();
        float yaw = (float) Math.toDegrees(Math.atan2(-front.offsetX, front.offsetZ));
        return BodyState
            .androidAt(
                worldObj.provider.dimensionId,
                (interior.minX + interior.maxX) / 2,
                interior.minY,
                (interior.minZ + interior.maxZ) / 2,
                ConfigMachines.android.maxHealth)
            .withRotation(yaw, 0);
    }

    private boolean hasInactiveOccupant(AndroidBodies bodies) {
        if (isCapsule() && bodies.isCapsuleOccupied(anchor())) return true;
        UUID owner = ownerTeam();
        return owner != null && bodies.bodies(owner)
            .stream()
            .anyMatch(body -> !bodies.isControlled(body.id()) && contains(body.state()));
    }

    boolean canManufacture() {
        return structureValid && !hasInactiveOccupant(AndroidControl.bodies())
            && worldObj.getEntitiesWithinAABB(EntityLivingBase.class, interior())
                .isEmpty();
    }

    private void updateDoors() {
        ForgeDirection back = getCurrentFacing().getRelativeBackInWorld();
        int x = xCoord - back.offsetX * 2, z = zCoord - back.offsetZ * 2;
        AxisAlignedBB approach = AxisAlignedBB.getBoundingBox(x, yCoord, z, x + 1, yCoord + 2, z + 1)
            .expand(DOOR_APPROACH_RANGE, 0, DOOR_APPROACH_RANGE);
        boolean open = !structureValid || ((!isCapsule() || !hasInactiveOccupant(AndroidControl.bodies()))
            && worldObj.getEntitiesWithinAABB(EntityPlayer.class, approach)
                .stream()
                .anyMatch(value -> value instanceof EntityPlayer player && !player.isDead && canOperate(player)));
        BlockAirlockDoor door = (BlockAirlockDoor) GalaxiaBlocksEnum.AIRLOCK_DOOR.get();
        for (int dy = 0; dy < 2; dy++) {
            if (worldObj.getBlock(x, yCoord + dy, z) == door && door.isOpen(worldObj, x, yCoord + dy, z) != open)
                door.setOpen(worldObj, x, yCoord + dy, z, open);
        }
    }

    public boolean isPowered() {
        return structureValid && powered;
    }

    @Nullable
    public UUID ownerTeam() {
        if (ownerTeam == null || worldObj == null || worldObj.isRemote) return ownerTeam;
        UUID resolved = GTTeamsCompat.resolveTeamId(ownerTeam);
        if (resolved != null && !resolved.equals(ownerTeam)) {
            ownerTeam = resolved;
            markDirty();
        }
        return resolved;
    }

    public ItemStackHandler inputInventory() {
        return inputs;
    }

    public long storedEnergy() {
        return energy;
    }

    public long energyCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    protected boolean checkStructure() {
        energyHatchPosition = null;
        return super.checkStructure() && energyHatchPosition != null;
    }

    private boolean addEnergyInput(IGregTechTileEntity tile) {
        if (!(tile.getMetaTileEntity() instanceof MTEHatchEnergy)) return false;
        energyHatchPosition = new BlockPos(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        return true;
    }

    @Nullable
    private MTEHatchEnergy energyInput() {
        if (energyHatchPosition != null && energyHatchPosition.getTE(worldObj) instanceof IGregTechTileEntity tile
            && !tile.isDead()
            && tile.getMetaTileEntity() instanceof MTEHatchEnergy hatch) return hatch;
        return null;
    }

    private long inputVoltage() {
        MTEHatchEnergy hatch = energyInput();
        return hatch == null ? 0 : hatch.maxEUInput();
    }

    public void onPlaced(EntityPlayer player) {
        ownerTeam = GTTeamsCompat.getTeam(player);
        markDirty();
    }

    public boolean canOperate(EntityPlayer player) {
        return GTTeamsCompat.hasPermission(ownerTeam(), player, TeamAction.CONTROL_ANDROID);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote) return;
        MTEHatchEnergy hatch = energyInput();
        if (structureValid && hatch != null) {
            long received = ENERGY_SOURCE.drawEnergy(hatch, ENERGY_CAPACITY - energy);
            if (received > 0) {
                energy += received;
                markDirty();
            }
        }
        boolean wasPowered = powered;
        int upkeep = isCapsule() ? ConfigMachines.android.capsuleEuPerTick : ConfigMachines.android.dockEuPerTick;
        powered = structureValid && hatch != null && ownerTeam() != null && useEnergy(upkeep);
        if (!powered) {
            if (wasPowered) AndroidControl.stationLost(anchor());
        } else if (!isCapsule()) {
            AndroidBodies bodies = AndroidControl.bodies();
            if (job == null) startJob(bodies);
            if (job != null) advanceJob(bodies);
            chargeDockedBody(bodies);
        }
        updateDoors();
        if (wasPowered != powered) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private boolean useEnergy(long amount) {
        if (amount < 0 || energy < amount) return false;
        energy -= amount;
        if (amount > 0) markDirty();
        return true;
    }

    @Nullable
    private AndroidBodies.AndroidBody dockedBody(AndroidBodies bodies, UUID target) {
        UUID owner = ownerTeam();
        if (owner == null) return null;
        return bodies.bodies(owner)
            .stream()
            .filter(
                body -> target == null || body.id()
                    .equals(target))
            .filter(
                body -> body.dock()
                    .equals(anchor()) && !body.armed()
                    && !bodies.isControlled(body.id()))
            .filter(
                body -> body.state()
                    .health() > 0 && contains(body.state()))
            .findFirst()
            .orElse(null);
    }

    private void chargeDockedBody(AndroidBodies bodies) {
        AndroidBodies.AndroidBody body = dockedBody(bodies, null);
        if (body == null) return;
        long rate = ConfigMachines.android.chargeEuPerTick * body.chassis()
            .chargeMultiplier();
        long amount = Math.min(Math.min(rate, inputVoltage()), body.capacity() - body.battery());
        if (amount > 0 && useEnergy(amount)) bodies.setBattery(body.id(), body.battery() + amount);
    }

    private ItemStack[] inputStacks() {
        ItemStack[] stacks = new ItemStack[INPUT_SLOTS];
        for (int i = 0; i < stacks.length; i++) stacks[i] = inputs.getStackInSlot(i);
        return stacks;
    }

    @Nullable
    private GTRecipe findRecipe(RecipeMap<RecipeMapBackend> map, @Nullable AndroidBodies.Chassis chassis) {
        return map.findRecipeQuery()
            .items(inputStacks())
            .fluids(fluids.getFluid())
            .voltage(inputVoltage())
            .findAll()
            .filter(recipe -> recipe.getMetadata(CHASSIS) != null)
            .filter(recipe -> chassis == null || recipe.getMetadata(CHASSIS) == chassis)
            .findFirst()
            .orElse(null);
    }

    private void startJob(AndroidBodies bodies) {
        AndroidBodies.AndroidBody body = dockedBody(bodies, null);
        boolean repairing = body != null && body.state()
            .health()
            < body.state()
                .maxHealth();
        if (!repairing && !canManufacture()) return;
        GTRecipe recipe = findRecipe(
            repairing ? REPAIR_RECIPES : MANUFACTURING_RECIPES,
            repairing ? body.chassis() : null);
        if (recipe == null || recipe.mDuration <= 0 || recipe.mEUt < 0 || energy < recipe.mEUt) return;
        ItemStack[] current = inputStacks();
        ItemStack[] before = new ItemStack[current.length];
        for (int i = 0; i < current.length; i++) before[i] = current[i] == null ? null : current[i].copy();
        FluidStack previousFluid = fluids.getFluid() == null ? null
            : fluids.getFluid()
                .copy();
        if (!recipe.isRecipeInputEqual(true, new FluidStack[] { fluids.getFluid() }, current)) return;
        ItemStackHandler escrow = new ItemStackHandler(INPUT_SLOTS);
        boolean consumed = false;
        for (int i = 0; i < current.length; i++) {
            int remaining = current[i] == null ? 0 : current[i].stackSize;
            if (before[i] != null && before[i].stackSize > remaining) {
                ItemStack held = before[i].copy();
                held.stackSize -= remaining;
                escrow.setStackInSlot(i, held);
                consumed = true;
            }
            inputs.setStackInSlot(i, remaining > 0 ? current[i] : null);
        }
        FluidStack heldFluid = null;
        if (previousFluid != null) {
            int remaining = fluids.getFluidAmount();
            if (previousFluid.amount > remaining) {
                heldFluid = previousFluid.copy();
                heldFluid.amount -= remaining;
                consumed = true;
            }
            if (remaining == 0) fluids.setFluid(null);
        }
        if (!consumed) return;
        job = new Job();
        job.chassis = recipe.getMetadata(CHASSIS);
        for (int slot = 0; slot < escrow.getSlots(); slot++) {
            ItemStack stack = escrow.getStackInSlot(slot);
            if (stack != null) job.charge += GTModHandler.getElectricItemCharge(stack)
                .map(stats -> stats[0] * stack.stackSize)
                .orElse(0L);
        }
        job.id = UUID.randomUUID();
        job.target = repairing ? body.id() : null;
        job.health = repairing ? Math.min(
            body.state()
                .maxHealth(),
            body.state()
                .health() + ConfigMachines.android.repairHealth)
            : 0;
        job.duration = recipe.mDuration;
        job.euPerTick = recipe.mEUt;
        job.items = escrow;
        job.fluid = heldFluid;
        operationFailed = false;
        markDirty();
    }

    private void advanceJob(AndroidBodies bodies) {
        if (job.euPerTick > inputVoltage()) return;
        UUID owner = ownerTeam();
        if (owner == null) return;
        AndroidBodies.AndroidBody target = job.target == null ? null : dockedBody(bodies, job.target);
        if (job.target != null && target == null) return;
        if (job.target == null && !bodies.wasManufactured(job.id) && !canManufacture()) return;
        if (job.progress < job.duration) {
            if (!useEnergy(job.euPerTick)) return;
            job.progress++;
            markDirty();
        }
        if (job.progress < job.duration) return;
        try {
            if (target == null) AndroidControl.manufacture(owner, anchor(), job.id, job.chassis, job.charge);
            else if (target.state()
                .health() < job.health) bodies.repair(target.id(), job.health);
            job = null;
            operationFailed = false;
            markDirty();
        } catch (IOException ex) {
            if (!operationFailed) Galaxia.LOG.error("Android station operation could not be committed", ex);
            operationFailed = true;
        }
    }

    /** Started recipes have consumed their escrow. Only unused inputs are returned when the controller is broken. */
    public List<ItemStack> removeInputs() {
        List<ItemStack> dropped = new ArrayList<>();
        for (int i = 0; i < inputs.getSlots(); i++) {
            ItemStack stack = inputs.extractItem(i, Integer.MAX_VALUE, false);
            if (stack != null) dropped.add(stack);
        }
        job = null;
        markDirty();
        return dropped;
    }

    @Override
    protected void onStructureDisformed() {
        powered = false;
        if (worldObj != null && !worldObj.isRemote) AndroidControl.stationLost(anchor());
    }

    @Override
    public void onMachineBlockUpdate() {
        super.onMachineBlockUpdate();
        mCheckTimer = 0;
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) AndroidControl.stationLost(anchor());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) AndroidControl.stationLost(anchor());
        super.onChunkUnload();
    }

    @Override
    public IStructureDefinition<TileEntityBodyStation> getStructureDefinition() {
        return kind.structure();
    }

    @Override
    protected int getControllerOffsetX() {
        return 1;
    }

    @Override
    protected int getControllerOffsetY() {
        return 2;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 2;
    }

    @Override
    public Block getControllerBlock() {
        return (isCapsule() ? GalaxiaBlocksEnum.CONTROL_CAPSULE : GalaxiaBlocksEnum.ANDROID_DOCK).get();
    }

    @Override
    public String[] getStructureDescription(ItemStack trigger) {
        return new String[] { net.minecraft.util.StatCollector.translateToLocal(kind.text + ".structure") };
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("stationId", stationId.toString());
        if (ownerTeam != null) tag.setString("ownerTeam", ownerTeam.toString());
        tag.setLong("energy", energy);
        tag.setBoolean("powered", powered);
        tag.setTag("inputs", inputs.serializeNBT());
        tag.setTag("fluids", fluids.writeToNBT(new NBTTagCompound()));
        if (job != null) tag.setTag("job", job.write());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("stationId")) stationId = UUID.fromString(tag.getString("stationId"));
        ownerTeam = tag.hasKey("ownerTeam") ? UUID.fromString(tag.getString("ownerTeam")) : null;
        energy = Math.max(0, Math.min(ENERGY_CAPACITY, tag.getLong("energy")));
        powered = tag.getBoolean("powered");
        if (tag.hasKey("inputs")) inputs.deserializeNBT(tag.getCompoundTag("inputs"));
        fluids.readFromNBT(tag.getCompoundTag("fluids"));
        job = tag.hasKey("job") ? Job.read(tag.getCompoundTag("job")) : null;
    }

    private static final class Job {

        private UUID id;
        private UUID target;
        private int duration;
        private int progress;
        private int euPerTick;
        private float health;
        private ItemStackHandler items;
        private FluidStack fluid;
        private AndroidBodies.Chassis chassis;
        private long charge;

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", id.toString());
            tag.setString("chassis", chassis.name());
            tag.setLong("charge", charge);
            if (target != null) tag.setString("target", target.toString());
            tag.setInteger("duration", duration);
            tag.setInteger("progress", progress);
            tag.setInteger("euPerTick", euPerTick);
            tag.setFloat("health", health);
            tag.setTag("items", items.serializeNBT());
            if (fluid != null) tag.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
            return tag;
        }

        private static Job read(NBTTagCompound tag) {
            Job job = new Job();
            job.id = UUID.fromString(tag.getString("id"));
            job.chassis = AndroidBodies.Chassis.valueOf(tag.getString("chassis"));
            job.charge = tag.getLong("charge");
            job.target = tag.hasKey("target") ? UUID.fromString(tag.getString("target")) : null;
            job.duration = tag.getInteger("duration");
            job.progress = tag.getInteger("progress");
            job.euPerTick = tag.getInteger("euPerTick");
            job.health = tag.getFloat("health");
            job.items = new ItemStackHandler(INPUT_SLOTS);
            job.items.deserializeNBT(tag.getCompoundTag("items"));
            job.fluid = tag.hasKey("fluid") ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluid")) : null;
            return job;
        }
    }

    @Override
    public int getSizeInventory() {
        return inputs.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inputs.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return inputs.extractItem(slot, amount, false);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return inputs.extractItem(slot, Integer.MAX_VALUE, false);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inputs.setStackInSlot(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return kind.text + ".title";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64
            && canOperate(player);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return !isCapsule() && stack != null;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        int amount = isCapsule() ? 0 : fluids.fill(resource, doFill);
        if (doFill && amount > 0) markDirty();
        return amount;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || fluids.getFluid() == null || !resource.isFluidEqual(fluids.getFluid())) return null;
        return drain(from, resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int amount, boolean doDrain) {
        FluidStack drained = fluids.drain(amount, doDrain);
        if (doDrain && drained != null) markDirty();
        return drained;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return !isCapsule();
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return !isCapsule();
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { fluids.getInfo() };
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        settings.canInteractWith(player -> player.worldObj.isRemote || isUseableByPlayer(player));
        BooleanSyncValue valid = new BooleanSyncValue(() -> structureValid, value -> {});
        BooleanSyncValue power = new BooleanSyncValue(this::isPowered, value -> {});
        IntSyncValue energyValue = new IntSyncValue(() -> (int) energy, value -> {});
        IntSyncValue progress = new IntSyncValue(() -> job == null ? 0 : job.progress, value -> {});
        IntSyncValue duration = new IntSyncValue(() -> job == null ? 0 : job.duration, value -> {});
        BooleanSyncValue failed = new BooleanSyncValue(() -> operationFailed, value -> {});
        syncManager.syncValue("formed", valid);
        syncManager.syncValue("powered", power);
        syncManager.syncValue("energy", energyValue);
        syncManager.syncValue("progress", progress);
        syncManager.syncValue("duration", duration);
        syncManager.syncValue("failed", failed);
        ModularPanel panel = GalaxiaGuiStyle.panel(kind.blockName, PANEL_WIDTH, PANEL_HEIGHT)
            .child(
                GalaxiaGuiStyle.playerInventory()
                    .leftRel(.5f)
                    .bottom(INVENTORY_BOTTOM));
        panel.child(
            GalaxiaGuiStyle.title(kind.text + ".title")
                .pos(PANEL_PADDING, 6));
        if (!isCapsule()) panel.child(
            GalaxiaGuiStyle
                .bar(() -> (double) energyValue.getIntValue() / ENERGY_CAPACITY, () -> GalaxiaGuiStyle.ACCENT)
                .pos(PANEL_PADDING, DOCK_ENERGY_BAR_Y)
                .size(CONTROL_WIDTH, DOCK_ENERGY_BAR_HEIGHT));
        panel.child(
            IKey.dynamic(
                () -> IKey.lang("galaxia.android.station.energy", energyValue.getIntValue(), ENERGY_CAPACITY)
                    .get())
                .asWidget()
                .color(GalaxiaGuiStyle.TEXT)
                .pos(PANEL_PADDING, 22));
        panel.child(
            IKey.dynamic(
                () -> IKey
                    .lang(
                        !valid.getBoolValue() ? "galaxia.android.station.status.unformed"
                            : !power.getBoolValue() ? "galaxia.android.station.status.unpowered"
                                : failed.getBoolValue() ? "galaxia.android.station.status.failed"
                                    : !isCapsule() && duration.getIntValue() > 0
                                        ? "galaxia.android.station.status.processing"
                                        : "galaxia.android.station.status.ready")
                    .get())
                .asWidget()
                .color(
                    () -> !valid.getBoolValue() || failed.getBoolValue() ? GalaxiaGuiStyle.BAD
                        : !power.getBoolValue() ? GalaxiaGuiStyle.WARN
                            : !isCapsule() && duration.getIntValue() > 0 ? GalaxiaGuiStyle.ACCENT
                                : GalaxiaGuiStyle.GOOD)
                .pos(PANEL_PADDING, isCapsule() ? CAPSULE_STATUS_Y : DOCK_STATUS_Y));
        if (isCapsule()) panel.child(
            new CapsuleControls(data).build(syncManager)
                .pos(PANEL_PADDING, 54));
        else {
            DoubleSupplier fraction = () -> duration.getIntValue() > 0
                ? (double) progress.getIntValue() / duration.getIntValue()
                : 0;
            ParentWidget<?> process = new ParentWidget<>().pos(PANEL_PADDING, DOCK_CONTENT_Y)
                .size(CONTROL_WIDTH, DOCK_CONTENT_HEIGHT)
                .background(EnumTextures.GUI_WELL.getImage());
            process.child(
                IKey.lang("galaxia.android.station.materials")
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(PANEL_PADDING, PANEL_PADDING));
            for (int i = 0; i < INPUT_SLOTS; i++) process.child(
                new ItemSlot().slot(new ModularSlot(inputs, i))
                    .background(EnumTextures.GUI_SLOT.getImage())
                    .pos(
                        PANEL_PADDING + i % DOCK_GRID_COLUMNS * DOCK_SLOT_PITCH,
                        DOCK_GRID_Y + i / DOCK_GRID_COLUMNS * DOCK_SLOT_PITCH));
            ParentWidget<?> assembly = new ParentWidget<>().name("android.dock.progress")
                .pos(DOCK_BODY_X, DOCK_BODY_Y)
                .size(DOCK_BODY_WIDTH, DOCK_BODY_HEIGHT)
                .background(
                    EnumTextures.ANDROID_ASSEMBLY.getImage()
                        .getSubArea(0, 0, 1, .5f)
                        .withColorOverride(GalaxiaGuiStyle.BAR_EDGE));
            AssemblyPart[] parts = AssemblyPart.values();
            for (AssemblyPart part : parts) {
                DoubleSupplier assembledRows = () -> Math
                    .floor(MathUtils.clamp(fraction.getAsDouble() * parts.length - part.ordinal(), 0, 1) * part.height)
                    / part.height;
                assembly.child(
                    new ProgressWidget().value(new DoubleValue.Dynamic(assembledRows, null))
                        .texture(
                            null,
                            EnumTextures.ANDROID_ASSEMBLY.getImage()
                                .getSubArea(
                                    (float) part.x / DOCK_BODY_WIDTH,
                                    (float) (DOCK_BODY_HEIGHT + part.y) / (2 * DOCK_BODY_HEIGHT),
                                    (float) (part.x + part.width) / DOCK_BODY_WIDTH,
                                    (float) (DOCK_BODY_HEIGHT + part.y + part.height) / (2 * DOCK_BODY_HEIGHT))
                                .withColorOverride(GalaxiaGuiStyle.ACCENT),
                            part.height)
                        .direction(ProgressWidget.Direction.UP)
                        .pos(part.x, part.y)
                        .size(part.width, part.height));
            }
            process.child(assembly);
            panel.child(process);
            panel.child(
                IKey.lang("galaxia.android.station.dock.hint")
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(PANEL_PADDING, DOCK_HINT_Y)
                    .width(PANEL_WIDTH - 2 * PANEL_PADDING));
        }
        return panel;
    }

    private record BodyEntry(UUID id, String name, CelestialObjectKey destination, AndroidBodies.Chassis chassis) {}

    public static void registerGuiFactory() {
        GuiManager.registerFactory(CapsuleGuiFactory.INSTANCE);
    }

    private static final class CapsuleGuiData extends PosGuiData {

        private final boolean starmap;
        private final UUID bodyId;

        private CapsuleGuiData(EntityPlayer player, int x, int y, int z, boolean starmap, @Nullable UUID bodyId) {
            super(player, x, y, z);
            this.starmap = starmap;
            this.bodyId = bodyId;
        }
    }

    /** Replaces a capsule container when navigating to the map or back to its destination list. */
    private static final class CapsuleGuiFactory implements UIFactory<CapsuleGuiData> {

        private static final CapsuleGuiFactory INSTANCE = new CapsuleGuiFactory();

        private void open(EntityPlayerMP player, TileEntityBodyStation station, boolean starmap,
            @Nullable UUID bodyId) {
            if (!station.isCapsule() || !station.isUseableByPlayer(player)) return;
            GalacticMapWidget.prepareOpen(player);
            GuiManager.open(
                this,
                new CapsuleGuiData(player, station.xCoord, station.yCoord, station.zCoord, starmap, bodyId),
                player);
        }

        @Override
        @Nonnull
        public String getFactoryName() {
            return "galaxia:capsule_navigation";
        }

        @Override
        public ModularPanel createPanel(CapsuleGuiData data, PanelSyncManager syncManager, UISettings settings) {
            TileEntityBodyStation station = (TileEntityBodyStation) data.getTileEntity();
            return data.starmap ? station.new CapsuleControls(data).buildMap(syncManager)
                : station.buildUI(data, syncManager, settings);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ModularScreen createScreen(CapsuleGuiData data, ModularPanel mainPanel) {
            return GalacticMapWidget.createScreen(mainPanel);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player, CapsuleGuiData data) {
            return player == data.getPlayer() && data.getTileEntity() instanceof TileEntityBodyStation station
                && station.isCapsule()
                && station.isUseableByPlayer(player);
        }

        @Override
        public void writeGuiData(CapsuleGuiData data, PacketBuffer buffer) {
            buffer.writeInt(data.getX());
            buffer.writeInt(data.getY());
            buffer.writeInt(data.getZ());
            buffer.writeBoolean(data.starmap);
            buffer.writeBoolean(data.bodyId != null);
            if (data.bodyId != null) PacketUtil.writeId(buffer, data.bodyId);
        }

        @Override
        @Nonnull
        public CapsuleGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            return new CapsuleGuiData(
                player,
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean() ? PacketUtil.readId(buffer) : null);
        }
    }

    /** One player's capsule navigation and rename draft, scoped to the open container. */
    private final class CapsuleControls {

        private final PosGuiData data;
        private final UUID selectedBody;
        private CelestialObjectKey selectedDestination;
        private UUID renameTarget;
        private String nameDraft = "";
        private IPanelHandler nameEditor;

        private CapsuleControls(PosGuiData data) {
            this.data = data;
            selectedBody = data instanceof CapsuleGuiData capsule ? capsule.bodyId : null;
        }

        private List<AndroidBodies.AndroidBody> ownedBodies() {
            if (worldObj.isRemote || !canOperate(data.getPlayer())) return List.of();
            UUID owner = ownerTeam();
            return owner == null ? List.of()
                : AndroidControl.bodies()
                    .bodies(owner);
        }

        private boolean atSelectedDestination(AndroidBodies.AndroidBody body) {
            return selectedDestination != null && GalaxiaCelestialAPI.findByDimension(
                body.dock()
                    .dimension())
                .map(
                    destination -> destination.key()
                        .equals(selectedDestination))
                .orElse(false);
        }

        private List<BodyEntry> entries() {
            return ownedBodies().stream()
                .filter(this::atSelectedDestination)
                .map(body -> new BodyEntry(body.id(), body.name(), selectedDestination, body.chassis()))
                .sorted(
                    Comparator.comparing(BodyEntry::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BodyEntry::id))
                .toList();
        }

        private ParentWidget<?> build(PanelSyncManager syncManager) {
            InteractionSyncHandler openMap = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || worldObj.isRemote || !(data.getPlayer() instanceof EntityPlayerMP player))
                    return;
                ServerTickTaskQueue
                    .schedule(() -> CapsuleGuiFactory.INSTANCE.open(player, TileEntityBodyStation.this, true, null));
            });
            syncManager.syncValue("android.openMap", openMap);
            StringSyncValue selectionName = new StringSyncValue(
                () -> ownedBodies().stream()
                    .filter(
                        body -> body.id()
                            .equals(selectedBody))
                    .findFirst()
                    .map(
                        body -> body.name()
                            .isEmpty()
                                ? body.id()
                                    .toString()
                                    .substring(0, BODY_ID_PREFIX_LENGTH)
                                : body.name())
                    .orElse(""),
                value -> {});
            GenericSyncValue<CelestialObjectKey, ?> planet = GenericSyncValue.builder(CelestialObjectKey.class)
                .getter(
                    () -> ownedBodies().stream()
                        .filter(
                            body -> body.id()
                                .equals(selectedBody))
                        .flatMap(
                            body -> GalaxiaCelestialAPI.findByDimension(
                                body.dock()
                                    .dimension())
                                .stream())
                        .map(CelestialObject::key)
                        .findFirst()
                        .orElse(null))
                .setter(value -> {})
                .serializer(PacketUtil::writeCelestialObjectKey)
                .deserializer(PacketUtil::readCelestialObjectKey)
                .equalsDefault()
                .copyImmutable()
                .nullable()
                .build();
            syncManager.syncValue("android.selectionName", selectionName);
            syncManager.syncValue("android.selectionPlanet", planet);
            InteractionSyncHandler connect = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || worldObj.isRemote
                    || !(data.getPlayer() instanceof EntityPlayerMP player)
                    || !isUseableByPlayer(player)) return;
                if (selectedBody == null) {
                    player.addChatMessage(new ChatComponentTranslation("galaxia.android.station.select_android"));
                    return;
                }
                if (!contains(player)) {
                    player.addChatMessage(new ChatComponentTranslation("galaxia.android.station.enter_capsule"));
                    return;
                }
                if (!isPowered() || !AndroidControl.activate(player, anchor(), selectedBody)) player
                    .addChatMessage(new ChatComponentTranslation("galaxia.android.station.connection_unavailable"));
            });
            syncManager.syncValue("android.connect", connect);
            ParentWidget<?> controls = new ParentWidget<>().size(CONTROL_WIDTH, CONTROL_HEIGHT);
            controls.child(
                IKey.dynamic(
                    () -> selectionName.getStringValue()
                        .isEmpty()
                            ? IKey.lang("galaxia.android.station.select_android")
                                .get()
                            : selectionName.getStringValue())
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT)
                    .pos(0, 0)
                    .width(CONTROL_WIDTH));
            controls.child(
                IKey.dynamic(
                    () -> planet.getValue() == null ? ""
                        : GalaxiaCelestialAPI.findBodyByKey(planet.getValue())
                            .map(CelestialObject::displayName)
                            .orElse(""))
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(0, 16)
                    .width(CONTROL_WIDTH));
            controls.child(
                GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.change_selection"))
                    .name("android.open_starmap")
                    .pos(0, 40)
                    .size(CONTROL_WIDTH, 20)
                    .syncHandler(openMap));
            controls.child(
                GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.connect"))
                    .name("android.connect")
                    .pos(0, 64)
                    .size(CONTROL_WIDTH, 20)
                    .syncHandler(connect));
            return controls;
        }

        private ModularPanel buildMap(PanelSyncManager syncManager) {
            nameEditor = syncManager.syncedPanel("android.rename", true, this::buildNameEditor);
            GenericSyncValue<CelestialObjectKey, ?> selected = GenericSyncValue.builder(CelestialObjectKey.class)
                .getter(() -> selectedDestination)
                .setter(key -> {
                    if (worldObj.isRemote || key == null
                        || GalaxiaCelestialAPI.findBodyByKey(key)
                            .isPresent()) {
                        selectedDestination = key;
                    }
                })
                .deserializer(PacketUtil::readCelestialObjectKey)
                .serializer(PacketUtil::writeCelestialObjectKey)
                .equalsDefault()
                .copyImmutable()
                .nullable()
                .build()
                .allowC2S();
            syncManager.syncValue("android.destination", selected);
            ParentWidget<?> choices = new ParentWidget<>().name("android.selection")
                .size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT)
                .child(bodyList(syncManager).size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT));
            return GalacticMapWidget
                .buildBodyPicker(syncManager, data.getPlayer(), key -> selected.setValue(key, true, true), choices);
        }

        private DynamicSyncedWidget<?> bodyList(PanelSyncManager syncManager) {
            GenericListSyncHandler<BodyEntry> entries = new GenericListSyncHandler<>(
                this::entries,
                null,
                buffer -> new BodyEntry(
                    new UUID(buffer.readLong(), buffer.readLong()),
                    ByteBufUtils.readUTF8String(buffer),
                    PacketUtil.readCelestialObjectKey(buffer),
                    AndroidBodies.Chassis.valueOf(ByteBufUtils.readUTF8String(buffer))),
                (buffer, entry) -> {
                    buffer.writeLong(
                        entry.id()
                            .getMostSignificantBits());
                    buffer.writeLong(
                        entry.id()
                            .getLeastSignificantBits());
                    ByteBufUtils.writeUTF8String(buffer, entry.name());
                    PacketUtil.writeCelestialObjectKey(buffer, entry.destination());
                    ByteBufUtils.writeUTF8String(
                        buffer,
                        entry.chassis()
                            .name());
                },
                BodyEntry::equals,
                entry -> entry);
            syncManager.syncValue("android.bodies", entries);
            DynamicLinkedSyncHandler<GenericListSyncHandler<BodyEntry>> list = new DynamicLinkedSyncHandler<>(entries)
                .widgetProvider((manager, value) -> {
                    ListWidget<IWidget, ?> rows = new ListWidget<>().size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT);
                    if (value.getValue()
                        .isEmpty())
                        rows.child(
                            IKey.lang("galaxia.android.station.empty")
                                .asWidget()
                                .color(GalaxiaGuiStyle.TEXT)
                                .height(BODY_ROW_HEIGHT));
                    for (BodyEntry entry : value.getValue()) rows.child(bodyRow(manager, entry));
                    return rows;
                });
            syncManager.syncValue("android.bodyList", list);
            return new DynamicSyncedWidget<>().syncHandler(list);
        }

        private ParentWidget<?> bodyRow(PanelSyncManager manager, BodyEntry entry) {
            UUID id = entry.id();
            InteractionSyncHandler operate = manager.getOrCreateSyncHandler(
                "operate_" + id,
                0,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.mouseButton != 0 || worldObj.isRemote
                        || !(data.getPlayer() instanceof EntityPlayerMP operator)
                        || !isUseableByPlayer(operator)
                        || !isPowered()) return;
                    AndroidBodies.AndroidBody body = AndroidControl.bodies()
                        .body(id)
                        .orElse(null);
                    if (body == null || !body.teamId()
                        .equals(ownerTeam()) || !atSelectedDestination(body)) return;
                    ServerTickTaskQueue.schedule(
                        () -> CapsuleGuiFactory.INSTANCE.open(operator, TileEntityBodyStation.this, false, id));
                }));
            InteractionSyncHandler rename = manager.getOrCreateSyncHandler(
                "rename_" + id,
                0,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.mouseButton != 0 || worldObj.isRemote || !isUseableByPlayer(data.getPlayer())) return;
                    AndroidBodies.AndroidBody body = AndroidControl.bodies()
                        .body(id)
                        .orElse(null);
                    if (body == null || !body.teamId()
                        .equals(ownerTeam()) || !atSelectedDestination(body)) return;
                    renameTarget = id;
                    nameDraft = body.name();
                }));
            return new ParentWidget<>().size(CONTROL_WIDTH, BODY_ROW_HEIGHT)
                .setEnabledIf(
                    widget -> entry.destination()
                        .equals(selectedDestination))
                .child(
                    GalaxiaGuiStyle
                        .textButton(
                            IKey.dynamic(
                                () -> IKey.lang(
                                    "galaxia.android.station.body_model",
                                    entry.name()
                                        .isEmpty()
                                            ? IKey.lang(
                                                "galaxia.android.station.body",
                                                id.toString()
                                                    .substring(0, BODY_ID_PREFIX_LENGTH))
                                                .get()
                                            : entry.name(),
                                    IKey.lang(
                                        entry.chassis()
                                            .translationKey())
                                        .get())
                                    .get()))
                        .name("android.body." + id)
                        .size(BODY_BUTTON_WIDTH, 22)
                        .syncHandler(operate))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.rename"))
                        .name("android.rename." + id)
                        .pos(BODY_BUTTON_WIDTH + 4, 0)
                        .size(RENAME_BUTTON_WIDTH, 22)
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            rename.onMousePressed(button);
                            nameEditor.openPanel();
                            return true;
                        }));
        }

        private ModularPanel buildNameEditor(PanelSyncManager syncManager, IPanelHandler handler) {
            StringSyncValue draft = new StringSyncValue(() -> nameDraft, value -> nameDraft = value).allowC2S();
            syncManager.syncValue("android.name", draft);
            InteractionSyncHandler save = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || worldObj.isRemote
                    || renameTarget == null
                    || !isUseableByPlayer(data.getPlayer())) return;
                try {
                    AndroidControl.bodies()
                        .rename(ownerTeam(), renameTarget, nameDraft);
                    handler.closePanel();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("galaxia.android.station.invalid_name"));
                } catch (IOException ex) {
                    Galaxia.LOG.error("Cannot commit android name", ex);
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("galaxia.android.station.rename_failed"));
                }
            });
            syncManager.syncValue("android.saveName", save);
            return GalaxiaGuiStyle.panel("android_rename", NAME_PANEL_WIDTH, NAME_PANEL_HEIGHT)
                .child(
                    GalaxiaGuiStyle.title("galaxia.android.station.rename")
                        .pos(PANEL_PADDING, PANEL_PADDING))
                .child(
                    new TextFieldWidget().name("android.name.input")
                        .setTextColor(GalaxiaGuiStyle.TEXT)
                        .background(EnumTextures.GUI_WELL.getImage())
                        .pos(PANEL_PADDING, 28)
                        .size(NAME_PANEL_WIDTH - 2 * PANEL_PADDING, 20)
                        .setMaxLength(AndroidBodies.MAX_NAME_LENGTH)
                        .autoUpdateOnChange(true)
                        .value(draft))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.save"))
                        .name("android.name.save")
                        .pos(PANEL_PADDING, NAME_ACTION_Y)
                        .size(NAME_BUTTON_WIDTH, NAME_BUTTON_HEIGHT)
                        .syncHandler(save))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.cancel"))
                        .pos(NAME_PANEL_WIDTH - PANEL_PADDING - NAME_BUTTON_WIDTH, NAME_ACTION_Y)
                        .size(NAME_BUTTON_WIDTH, NAME_BUTTON_HEIGHT)
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            handler.closePanel();
                            return true;
                        }));
        }
    }
}
