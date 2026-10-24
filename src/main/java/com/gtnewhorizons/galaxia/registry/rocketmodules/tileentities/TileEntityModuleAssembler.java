package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaCasingTextures;
import com.gtnewhorizons.galaxia.compat.gt.IRocketHatchHost;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatchElement;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatches;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.MachineTerminals;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTStructureUtility;

/** Supplied manufacture and repair of one exactly assigned module at a time. */
public class TileEntityModuleAssembler extends GalaxiaMultiblockBase<TileEntityModuleAssembler> implements
    IGuiHolder<PosGuiData>, IRocketControllerTE, IRocketHatchHost, IInventory, IFluidHandler, IEnergyConnected {

    private final MachineIdentity identity = new MachineIdentity();
    private final SupplyLimits limits;
    private final ItemStack[] inputs;
    private final FluidTank[] tanks;
    private final MachineTerminals terminals = new MachineTerminals();
    private final RocketHatches hatches = new RocketHatches();
    private WorkRequest accepted;
    private UUID acceptedTeam;
    private int remainingTicks;
    private boolean moduleReceived;
    private long energy;
    private Map<InventoryKey, Long> escrow = Map.of();
    private WorkRequest acknowledged;

    public TileEntityModuleAssembler() {
        this(
            new SupplyLimits(
                ConfigMachines.moduleAssembler.itemSlots,
                ConfigMachines.moduleAssembler.fluidTanks,
                ConfigMachines.moduleAssembler.fluidCapacity,
                ConfigMachines.moduleAssembler.energyCapacity,
                ConfigMachines.moduleAssembler.maxVoltage));
    }

    public TileEntityModuleAssembler(@Nonnull SupplyLimits limits) {
        this.limits = limits;
        inputs = new ItemStack[limits.itemSlots()];
        tanks = new FluidTank[limits.fluidTanks()];
        for (int i = 0; i < tanks.length; i++) tanks[i] = new FluidTank(limits.fluidCapacity());
    }

    public record SupplyLimits(int itemSlots, int fluidTanks, int fluidCapacity, long euCapacity, long maxVoltage) {

        public SupplyLimits {
            if (itemSlots < 0 || fluidTanks < 0 || fluidCapacity < 0 || euCapacity < 0 || maxVoltage < 0) {
                throw new IllegalArgumentException("Machine supply limits must be nonnegative");
            }
        }
    }

    public enum Operation {
        MANUFACTURE,
        REPAIR
    }

    public enum Admission {

        ACCEPTED,
        ALREADY_ACCEPTED,
        BUSY,
        WRONG_TEAM,
        INVALID_MACHINE,
        MISSING_MATERIALS,
        CONFLICT;

        public boolean accepted() {
            return this == ACCEPTED || this == ALREADY_ACCEPTED;
        }
    }

    /** Server-authored bill frozen when work is accepted. It is never supplied by a GUI packet. */
    public record WorkRequest(@Nonnull UUID workId, @Nonnull UUID vehicleId, @Nonnull UUID moduleId, int definitionId,
        @Nonnull Operation operation, @Nonnull Map<ItemStackWrapper, Long> items, @Nonnull Map<FluidKey, Long> fluids,
        long euPerTick, int workTicks) {

        public WorkRequest {
            items = Map.copyOf(items);
            fluids = Map.copyOf(fluids);
            if (definitionId < 0 || euPerTick <= 0
                || workTicks <= 0
                || (items.isEmpty() && fluids.isEmpty())
                || items.values()
                    .stream()
                    .anyMatch(amount -> amount <= 0)
                || fluids.values()
                    .stream()
                    .anyMatch(amount -> amount <= 0)) {
                throw new IllegalArgumentException(
                    "Work requires a definition and positive supplied bill, power and time");
            }
        }

        @Nonnull
        public static WorkRequest fromBill(@Nonnull UUID workId, @Nonnull UUID vehicleId, @Nonnull UUID moduleId,
            int definitionId, @Nonnull Operation operation, @Nonnull RocketPartDef.WorkBill bill) {
            Map<ItemStackWrapper, Long> items = new LinkedHashMap<>();
            Map<FluidKey, Long> fluids = new LinkedHashMap<>();
            bill.inputs()
                .forEach((key, amount) -> {
                    if (key instanceof ItemStackWrapper item) items.put(item, amount);
                    else if (key instanceof FluidKey fluid) fluids.put(fluid, amount);
                    else throw new IllegalArgumentException("Unsupported work resource");
                });
            return new WorkRequest(
                workId,
                vehicleId,
                moduleId,
                definitionId,
                operation,
                items,
                fluids,
                bill.euPerTick(),
                bill.ticks());
        }
    }

    @Override
    public MachineIdentity machineIdentity() {
        return identity;
    }

    public UUID controllerId() {
        return identity.id();
    }

    @Override
    public RocketHatches getHatches() {
        return hatches;
    }

    /** Inspects and reserves the entire exact bill without yielding to another machine or tick. */
    public Admission tryAccept(@Nonnull UUID requestingTeam, @Nonnull WorkRequest request) {
        if (!identity.belongsTo(requestingTeam)) return Admission.WRONG_TEAM;
        if (accepted != null && accepted.workId()
            .equals(request.workId())) {
            return accepted.equals(request) && GTTeamsCompat.teamOwns(requestingTeam, acceptedTeam)
                ? Admission.ALREADY_ACCEPTED
                : Admission.CONFLICT;
        }
        if (acknowledged != null && acknowledged.workId()
            .equals(request.workId())) return Admission.CONFLICT;
        if (accepted != null) return Admission.BUSY;
        if (!structureValid || (worldObj != null && worldObj.isRemote) || limits.euCapacity() < request.euPerTick()) {
            return Admission.INVALID_MACHINE;
        }
        pullSupplies();
        int[] itemRemoval = new int[inputs.length];
        int[] fluidRemoval = new int[tanks.length];
        Map<InventoryKey, Long> reserved = new LinkedHashMap<>();
        for (var material : request.items()
            .entrySet()) {
            long needed = material.getValue();
            for (int i = 0; i < inputs.length && needed > 0; i++) {
                if (!matches(inputs[i], material.getKey())) continue;
                int take = (int) Math.min(needed, inputs[i].stackSize - itemRemoval[i]);
                itemRemoval[i] += take;
                needed -= take;
            }
            if (needed != 0) return Admission.MISSING_MATERIALS;
        }
        for (var material : request.fluids()
            .entrySet()) {
            long needed = material.getValue();
            for (int i = 0; i < tanks.length && needed > 0; i++) {
                FluidStack stored = tanks[i].getFluid();
                if (stored == null || !FluidKey.of(stored)
                    .equals(material.getKey())) continue;
                int take = (int) Math.min(needed, stored.amount - fluidRemoval[i]);
                fluidRemoval[i] += take;
                needed -= take;
            }
            if (needed != 0) return Admission.MISSING_MATERIALS;
        }
        for (int i = 0; i < inputs.length; i++) {
            if (itemRemoval[i] == 0) continue;
            reserved.merge(ItemStackWrapper.of(inputs[i]), (long) itemRemoval[i], Long::sum);
            inputs[i].stackSize -= itemRemoval[i];
            if (inputs[i].stackSize == 0) inputs[i] = null;
        }
        for (int i = 0; i < tanks.length; i++) {
            if (fluidRemoval[i] == 0) continue;
            FluidStack stored = tanks[i].getFluid();
            reserved.merge(FluidKey.of(stored), (long) fluidRemoval[i], Long::sum);
            stored.amount -= fluidRemoval[i];
            if (stored.amount == 0) tanks[i].setFluid(null);
        }
        escrow = Map.copyOf(reserved);
        accepted = request;
        acceptedTeam = requestingTeam;
        remainingTicks = request.workTicks();
        moduleReceived = request.operation() == Operation.MANUFACTURE;
        markDirty();
        return Admission.ACCEPTED;
    }

    private static boolean matches(@Nullable ItemStack actual, ItemStackWrapper required) {
        if (actual == null) return false;
        if (required.equals(ItemStackWrapper.of(actual))) return true;
        return required.nbt() == null && !actual.hasTagCompound()
            && GTOreDictUnificator.isInputStackEqual(actual, required.toItemStack());
    }

    @Nullable
    public WorkRequest acceptedWork() {
        return accepted;
    }

    /** The Gantry calls this only after the exact module's incoming transfer is accepted. */
    public boolean receiveAssignedModule(UUID workId, UUID vehicleId, UUID moduleId) {
        if (accepted == null || !accepted.workId()
            .equals(workId)
            || !accepted.vehicleId()
                .equals(vehicleId)
            || !accepted.moduleId()
                .equals(moduleId))
            return false;
        moduleReceived = true;
        markDirty();
        return true;
    }

    @Nullable
    public WorkRequest completedOutput() {
        return accepted != null && remainingTicks == 0 ? accepted : null;
    }

    /** Frees the output slot only after the return recipient has acknowledged custody. */
    public boolean acknowledgeOutput(UUID workId) {
        if (acknowledged != null && acknowledged.workId()
            .equals(workId)) return true;
        if (completedOutput() == null || !accepted.workId()
            .equals(workId)) return false;
        acknowledged = accepted;
        accepted = null;
        acceptedTeam = null;
        escrow = Map.of();
        moduleReceived = false;
        markDirty();
        return true;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj != null && !worldObj.isRemote) {
            pullSupplies();
            tickWork(
                Galaxia.proxy.serverRuntime()
                    .rockets());
        }
    }

    private void pullSupplies() {
        if (!structureValid || worldObj == null || worldObj.isRemote) return;
        hatches.transferInputItemsTo(worldObj, this);
        hatches.transferInputFluidsTo(worldObj, this);
    }

    /** Paid work pauses with its vehicle while a destroyed holder awaits explicit recovery. */
    public void tickWork(Rocket.Registry registry) {
        if (!structureValid || accepted == null
            || !moduleReceived
            || remainingTicks == 0
            || energy < accepted.euPerTick()) return;
        Rocket rocket = registry.find(accepted.vehicleId());
        if (rocket == null || rocket.status() == Rocket.Status.INCOMPLETE) return;
        energy -= accepted.euPerTick();
        remainingTicks--;
        markDirty();
    }

    /** Commits any fully paid output, then releases only this destroyed controller's actual custody. */
    public void destroyController(Rocket.Registry registry) {
        WorkRequest output = completedOutput();
        Rocket rocket = output == null ? null : registry.find(output.vehicleId());
        if (rocket != null && output.operation() == Operation.MANUFACTURE) {
            var slot = rocket.buildSlots()
                .stream()
                .filter(
                    candidate -> candidate.moduleId()
                        .equals(output.moduleId())
                        && candidate.requestId()
                            .equals(output.workId())
                        && candidate.part()
                            .def()
                            .id() == output.definitionId())
                .findFirst()
                .orElse(null);
            var claim = rocket.workFor(output.moduleId());
            if (slot != null && claim != null
                && claim.workId()
                    .equals(output.workId())
                && claim.machine()
                    .controllerId()
                    .equals(controllerId())
                && rocket.modules()
                    .stream()
                    .noneMatch(
                        module -> module.id()
                            .equals(output.moduleId()))) {
                rocket.acceptProduced(
                    Rocket.Module.produced(
                        rocket.id(),
                        slot,
                        100,
                        new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, controllerId())));
            }
        } else if (rocket != null && output.operation() == Operation.REPAIR) {
            rocket.completeRepair(output.moduleId(), output.workId(), controllerId());
        }
        GantryAPI.dropModules(registry, new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, controllerId()), this);
        for (Rocket vehicle : registry.all()) vehicle.workOwnerDestroyed(controllerId());
        accepted = null;
        acceptedTeam = null;
        acknowledged = null;
        escrow = Map.of();
        remainingTicks = 0;
        moduleReceived = false;
        structureValid = false;
        terminals.clear();
        markDirty();
    }

    public long storedEU() {
        return energy;
    }

    @Override
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (!inputEnergyFrom(side) || voltage <= 0 || amperage <= 0 || voltage > limits.maxVoltage()) return 0;
        long packets = Math.min(amperage, (limits.euCapacity() - energy) / voltage);
        if (packets > 0) {
            energy += packets * voltage;
            markDirty();
        }
        return packets;
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return limits.euCapacity() > 0;
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(byte colorization) {
        return -1;
    }

    @Override
    public int getSizeInventory() {
        return inputs.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inputs[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (inputs[slot] == null || amount <= 0) return null;
        ItemStack result = inputs[slot].splitStack(Math.min(amount, inputs[slot].stackSize));
        if (inputs[slot].stackSize == 0) inputs[slot] = null;
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack result = inputs[slot];
        inputs[slot] = null;
        markDirty();
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (stack != null
            && (stack.stackSize < 0 || stack.stackSize > Math.min(getInventoryStackLimit(), stack.getMaxStackSize()))) {
            throw new IllegalArgumentException("Input stack exceeds slot capacity");
        }
        inputs[slot] = stack;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "tile.module_assembler_controller.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64
            && identity.canUse(player, TeamAction.MANAGE_INVENTORY);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        int remaining = resource.amount;
        for (FluidTank tank : tanks) {
            if (tank.getFluid() != null && !tank.getFluid()
                .isFluidEqual(resource)) continue;
            FluidStack portion = resource.copy();
            portion.amount = remaining;
            remaining -= tank.fill(portion, doFill);
            if (remaining == 0) break;
        }
        if (doFill && remaining != resource.amount) markDirty();
        return resource.amount - remaining;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        FluidStack result = resource.copy();
        result.amount = 0;
        for (FluidTank tank : tanks) {
            FluidStack stored = tank.getFluid();
            if (stored == null || !stored.isFluidEqual(resource)) continue;
            int amount = Math.min(resource.amount - result.amount, stored.amount);
            result.amount += amount;
            if (doDrain) {
                stored.amount -= amount;
                if (stored.amount == 0) tank.setFluid(null);
            }
            if (result.amount == resource.amount) break;
        }
        if (doDrain && result.amount > 0) markDirty();
        return result.amount == 0 ? null : result;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        for (FluidTank tank : tanks) {
            if (tank.getFluid() == null) continue;
            FluidStack requested = tank.getFluid()
                .copy();
            requested.amount = maxDrain;
            return drain(from, requested, doDrain);
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return tanks.length > 0;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return tanks.length > 0;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        FluidTankInfo[] info = new FluidTankInfo[tanks.length];
        for (int i = 0; i < tanks.length; i++) info[i] = tanks[i].getInfo();
        return info;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        identity.writeToNBT(tag);
        tag.setLong("energy", energy);
        NBTTagList itemTags = new NBTTagList();
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) continue;
            NBTTagCompound item = inputs[i].writeToNBT(new NBTTagCompound());
            item.setInteger("slot", i);
            itemTags.appendTag(item);
        }
        tag.setTag("inputs", itemTags);
        NBTTagList fluidTags = new NBTTagList();
        for (FluidTank tank : tanks) fluidTags.appendTag(tank.writeToNBT(new NBTTagCompound()));
        tag.setTag("tanks", fluidTags);
        if (accepted != null) {
            tag.setTag("accepted", writeRequest(accepted));
            tag.setString("acceptedTeam", acceptedTeam.toString());
            tag.setInteger("remainingTicks", remainingTicks);
            tag.setBoolean("moduleReceived", moduleReceived);
            tag.setTag("escrow", writeResources(escrow));
        }
        if (acknowledged != null) tag.setTag("acknowledged", writeRequest(acknowledged));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        identity.readFromNBT(tag);
        NbtReader in = new NbtReader(tag, "moduleAssembler");
        energy = in.longValue("energy");
        if (energy < 0 || energy > limits.euCapacity())
            throw new IllegalStateException("Stored EU exceeds machine capacity");
        java.util.Arrays.fill(inputs, null);
        NBTTagList itemTags = in.compounds("inputs");
        for (int i = 0; i < itemTags.tagCount(); i++) {
            NBTTagCompound item = itemTags.getCompoundTagAt(i);
            int slot = new NbtReader(item, "moduleAssembler.inputs").integer("slot", 0, inputs.length - 1);
            inputs[slot] = ItemStack.loadItemStackFromNBT(item);
            if (inputs[slot] == null) throw new IllegalStateException("Unknown stored input item");
        }
        NBTTagList fluidTags = in.compounds("tanks");
        if (fluidTags.tagCount() != tanks.length) throw new IllegalStateException("Machine tank configuration changed");
        for (int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(fluidTags.getCompoundTagAt(i));
        accepted = tag.hasKey("accepted") ? readRequest(in.compound("accepted")) : null;
        acceptedTeam = accepted == null ? null : UUID.fromString(in.string("acceptedTeam"));
        remainingTicks = accepted == null ? 0 : in.integer("remainingTicks", 0, accepted.workTicks());
        moduleReceived = accepted != null && in.bool("moduleReceived");
        escrow = accepted == null ? Map.of() : readResources(in.compounds("escrow"));
        acknowledged = tag.hasKey("acknowledged") ? readRequest(in.compound("acknowledged")) : null;
    }

    private static NBTTagCompound writeRequest(WorkRequest request) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "work",
            request.workId()
                .toString());
        tag.setString(
            "vehicle",
            request.vehicleId()
                .toString());
        tag.setString(
            "module",
            request.moduleId()
                .toString());
        tag.setInteger("definition", request.definitionId());
        tag.setString(
            "operation",
            request.operation()
                .name());
        Map<InventoryKey, Long> bill = new LinkedHashMap<>();
        bill.putAll(request.items());
        bill.putAll(request.fluids());
        tag.setTag("bill", writeResources(bill));
        tag.setLong("euPerTick", request.euPerTick());
        tag.setInteger("ticks", request.workTicks());
        return tag;
    }

    private static WorkRequest readRequest(NbtReader in) {
        Map<InventoryKey, Long> bill = readResources(in.compounds("bill"));
        return WorkRequest.fromBill(
            UUID.fromString(in.string("work")),
            UUID.fromString(in.string("vehicle")),
            UUID.fromString(in.string("module")),
            in.integer("definition"),
            in.enumValue(Operation.class, "operation"),
            new RocketPartDef.WorkBill(bill, in.longValue("euPerTick"), in.integer("ticks")));
    }

    private static NBTTagList writeResources(Map<InventoryKey, Long> resources) {
        NBTTagList list = new NBTTagList();
        resources.forEach((key, amount) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("key", InventoryKeyState.encode(key));
            entry.setLong("amount", amount);
            list.appendTag(entry);
        });
        return list;
    }

    private static Map<InventoryKey, Long> readResources(NBTTagList list) {
        Map<InventoryKey, Long> resources = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NbtReader in = new NbtReader(list.getCompoundTagAt(i), "moduleAssembler.resources");
            InventoryKey key = InventoryKeyState.decode(
                in.compound("key")
                    .tag());
            long amount = in.longValue("amount");
            if (amount <= 0 || resources.put(key, amount) != null)
                throw new IllegalStateException("Invalid resource escrow");
        }
        return Map.copyOf(resources);
    }

    public int getAssemblyTicksRemaining() {
        return remainingTicks;
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        terminals.add(terminal);
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return terminals.best();
    }

    @Override
    protected boolean checkStructure() {
        if (worldObj != null && !worldObj.isRemote) {
            hatches.clear();
            terminals.clear();
        }
        return super.checkStructure();
    }

    @Override
    public IStructureDefinition<TileEntityModuleAssembler> getStructureDefinition() {
        return Structure.DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return 1;
    }

    @Override
    protected int getControllerOffsetY() {
        return 1;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 4;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get();
    }

    @Override
    public ForgeDirection getPlacedFacing() {
        return currentFacing.getDirection();
    }

    @Override
    public void setPlacedFacing(ForgeDirection dir) {
        setFacing(dir);
    }

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }

    public boolean checkValidGraph() {
        var terminal = getGantryTerminal();
        return terminal != null && terminal.checkValidGraph();
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) markStructureDirty();
        BooleanSyncValue formed = new BooleanSyncValue(() -> structureValid, _ -> {});
        syncManager.syncValue("assemblerStructureValid", formed);
        LongSyncValue power = new LongSyncValue(this::storedEU);
        IntSyncValue remaining = new IntSyncValue(this::getAssemblyTicksRemaining, _ -> {});
        BooleanSyncValue busy = new BooleanSyncValue(() -> accepted != null, _ -> {});
        syncManager.syncValue("power", power);
        syncManager.syncValue("remaining", remaining);
        syncManager.syncValue("busy", busy);
        int columns = 9;
        int slotSize = 18;
        int inputY = 58;
        int tankY = inputY + ((inputs.length + columns - 1) / columns) * slotSize + 8;
        int playerY = tankY + ((tanks.length + columns - 1) / columns) * slotSize + 18;
        ModularPanel panel = ModularPanel.defaultPanel("galaxia:module_assembler", 220, playerY + 96)
            .bindPlayerInventory()
            .child(
                IKey.lang("tile.module_assembler_controller.name")
                    .asWidget()
                    .pos(8, 6))
            .childIf(
                !formed.getBoolValue(),
                () -> IKey.lang("galaxia.gui.module_assembler.not_formed")
                    .asWidget()
                    .pos(8, 20))
            .child(
                IKey.lang(
                    "galaxia.gui.module_assembler.energy",
                    () -> new Object[] { power.getLongValue(), limits.euCapacity() })
                    .asWidget()
                    .pos(8, 32))
            .child(
                IKey.dynamic(
                    () -> busy.getBoolValue()
                        ? IKey
                            .lang(
                                remaining.getIntValue() == 0 ? "galaxia.gui.module_assembler.output_ready"
                                    : "galaxia.gui.module_assembler.work_remaining",
                                remaining.getIntValue())
                            .get()
                        : IKey.lang("galaxia.gui.module_assembler.idle")
                            .get())
                    .asWidget()
                    .pos(8, 44));
        InvWrapper inventory = new InvWrapper(this);
        for (int i = 0; i < inputs.length; i++) {
            panel.child(
                new ItemSlot().slot(new ModularSlot(inventory, i))
                    .pos(8 + (i % columns) * slotSize, inputY + (i / columns) * slotSize));
        }
        for (int i = 0; i < tanks.length; i++) {
            panel.child(
                new FluidSlot().syncHandler(
                    new FluidSlotSyncHandler(tanks[i]).canDrainSlot(false)
                        .canFillSlot(false))
                    .pos(8 + (i % columns) * slotSize, tankY + (i / columns) * slotSize));
        }
        return panel;
    }

    private static final class Structure {

        private static final IStructureDefinition<TileEntityModuleAssembler> DEFINITION = StructureDefinition
            .<TileEntityModuleAssembler>builder()
            .addShape(
                "main",
                new String[][] { { "CCC", "CCC", "CCC" }, { "C C", "T T", "C C" }, { "C C", "C C", "C C" },
                    { "C C", "C C", "C C" }, { "CCC", "C~C", "CCC" } })
            .addElement(
                'C',
                GTStructureUtility.<TileEntityModuleAssembler>buildHatchAdder()
                    .anyOf(RocketHatchElement.InputBus, RocketHatchElement.InputHatch)
                    .casingIndex(GalaxiaCasingTextures.RUSTY_PANEL)
                    .hint(1)
                    .buildAndChain(StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
            .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((assembler, te) -> {
                if (!(te instanceof TileEntityGantryTerminal terminal)) return false;
                assembler.setGantryTerminal(terminal);
                terminal.connectAssembler(assembler);
                return true;
            }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
                StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
            .build();
    }
}
