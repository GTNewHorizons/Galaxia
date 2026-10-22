package com.gtnewhorizons.galaxia.registry.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.gt.handlers.GTHatchEnergySourceHandler;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;

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
        int previousFluidAmount = fluids.getFluidAmount();
        if (!recipe.isRecipeInputEqual(true, new FluidStack[] { fluids.getFluid() }, current)) return;
        long charge = 0;
        boolean consumed = false;
        for (int i = 0; i < current.length; i++) {
            int remaining = current[i] == null ? 0 : current[i].stackSize;
            if (before[i] != null && before[i].stackSize > remaining) {
                ItemStack consumedStack = before[i];
                consumedStack.stackSize -= remaining;
                charge += GTModHandler.getElectricItemCharge(consumedStack)
                    .map(stats -> stats[0] * consumedStack.stackSize)
                    .orElse(0L);
                consumed = true;
            }
            inputs.setStackInSlot(i, remaining > 0 ? current[i] : null);
        }
        int remainingFluid = fluids.getFluidAmount();
        if (previousFluidAmount > remainingFluid) consumed = true;
        if (remainingFluid == 0) fluids.setFluid(null);
        if (!consumed) return;
        job = new Job();
        job.chassis = recipe.getMetadata(CHASSIS);
        job.charge = charge;
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

    /**
     * Started recipes have consumed their ingredients. Only unused inputs are returned when the controller is broken.
     */
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
        return new BodyStationGui(this).build(data, syncManager, settings);
    }

    public static void registerGuiFactory() {
        BodyStationGui.registerFactory();
    }

    Kind kind() {
        return kind;
    }

    int processingProgress() {
        return job == null ? 0 : job.progress;
    }

    int processingDuration() {
        return job == null ? 0 : job.duration;
    }

    boolean operationFailed() {
        return operationFailed;
    }
}
