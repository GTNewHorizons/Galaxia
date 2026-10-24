package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;

/** A Gantry node bound to one exact physical controller, resolved again after unload. */
public class TileEntityGantryTerminal extends TileEntityGantry {

    private TileEntity connectedController;
    private Binding binding;
    private TerminalMode mode = TerminalMode.BOTH;

    private enum ControllerKind {
        SILO,
        ASSEMBLER,
        PLATFORM
    }

    private record Binding(UUID id, ControllerKind kind, int x, int y, int z) {}

    public UUID terminalId() {
        return nodeId();
    }

    public TerminalMode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        sync();
    }

    @Nullable
    public int[] getConsumerPosition() {
        TileEntity controller = controller();
        return controller == null ? null : new int[] { controller.xCoord, controller.yCoord, controller.zCoord };
    }

    public void connectSilo(TileEntitySilo silo) {
        bind(silo, ControllerKind.SILO);
    }

    public void connectAssembler(TileEntityModuleAssembler assembler) {
        bind(assembler, ControllerKind.ASSEMBLER);
    }

    public void connectPlatform(TileEntityTransportPlatform pad) {
        bind(pad, ControllerKind.PLATFORM);
    }

    private void bind(TileEntity controller, ControllerKind kind) {
        var identity = ((IRocketControllerTE) controller).machineIdentity();
        connectedController = controller;
        binding = new Binding(identity.id(), kind, controller.xCoord, controller.yCoord, controller.zCoord);
        syncProjection();
    }

    @Nullable
    public TileEntity controller() {
        if (binding == null) return null;
        if (worldObj == null) return connectedController;
        if (!worldObj.blockExists(binding.x(), binding.y(), binding.z())) return null;
        TileEntity tile = worldObj.getTileEntity(binding.x(), binding.y(), binding.z());
        if (!(tile instanceof IRocketControllerTE controller) || tile.isInvalid()
            || !controller.machineIdentity()
                .id()
                .equals(binding.id())
            || !matchesKind(tile)) {
            return null;
        }
        connectedController = tile;
        return tile;
    }

    private boolean matchesKind(TileEntity tile) {
        return switch (binding.kind()) {
            case SILO -> tile instanceof TileEntitySilo;
            case ASSEMBLER -> tile instanceof TileEntityModuleAssembler;
            case PLATFORM -> tile instanceof TileEntityTransportPlatform;
        };
    }

    @Nullable
    public TileEntityTransportPlatform getPlatform() {
        TileEntity tile = controller();
        return tile instanceof TileEntityTransportPlatform pad ? pad : null;
    }

    @Nullable
    public IRocketControllerTE.MachineIdentity controllerIdentity() {
        TileEntity tile = controller();
        return tile instanceof IRocketControllerTE controller ? controller.machineIdentity() : null;
    }

    @Nullable
    public TileEntitySilo getSilo() {
        TileEntity tile = controller();
        return tile instanceof TileEntitySilo silo ? silo : null;
    }

    @Nullable
    public TileEntityModuleAssembler getAssembler() {
        TileEntity tile = controller();
        return tile instanceof TileEntityModuleAssembler assembler ? assembler : null;
    }

    public boolean matchesTarget(Rocket.TerminalTarget target) {
        var identity = controllerIdentity();
        return identity != null && nodeId().equals(target.terminalId())
            && identity.id()
                .equals(target.controllerId())
            && dimension() == target.dimension()
            && xCoord == target.x()
            && yCoord == target.y()
            && zCoord == target.z();
    }

    /** Pure admission checks do not move a module or consume resources. */
    public boolean canReceive(Rocket rocket, Rocket.Module module, UUID workId, Rocket.Holder receiver) {
        if (!mode.delivers) return false;
        TileEntity tile = controller();
        if (!(tile instanceof IRocketControllerTE controller) || !controller.isStructureValid()
            || !controller.machineIdentity()
                .belongsTo(rocket.teamId())
            || !controller.machineIdentity()
                .id()
                .equals(receiver.id()))
            return false;
        if (tile instanceof TileEntityModuleAssembler assembler) {
            var request = assembler.acceptedWork();
            return receiver.kind() == Rocket.HolderKind.ASSEMBLER && request != null
                && request.workId()
                    .equals(workId)
                && request.vehicleId()
                    .equals(rocket.id())
                && request.moduleId()
                    .equals(module.id())
                && request.definitionId() == module.part()
                    .def()
                    .id()
                && request.operation() == TileEntityModuleAssembler.Operation.REPAIR;
        }
        return tile instanceof TileEntitySilo silo && receiver.kind() == Rocket.HolderKind.SILO_BUFFER
            && silo.acceptGantryDelivery(rocket, module, workId);
    }

    /** The receiver acknowledges the exact claim before the manifest changes custody. */
    public boolean acceptDelivery(Rocket rocket, Rocket.Module module, UUID workId, Rocket.Holder receiver) {
        if (!canReceive(rocket, module, workId, receiver)) return false;
        TileEntityModuleAssembler assembler = getAssembler();
        return assembler == null || assembler.receiveAssignedModule(workId, rocket.id(), module.id());
    }

    public void sync() {
        syncProjection();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("mode", mode.ordinal());
        if (binding != null) tag.setTag("controller", encodeBinding());
    }

    private NBTTagCompound encodeBinding() {
        var tag = new NBTTagCompound();
        tag.setString(
            "id",
            binding.id()
                .toString());
        tag.setString(
            "kind",
            binding.kind()
                .name());
        tag.setInteger("x", binding.x());
        tag.setInteger("y", binding.y());
        tag.setInteger("z", binding.z());
        return tag;
    }

    private void readBinding(NBTTagCompound tag) {
        binding = null;
        connectedController = null;
        if (tag.hasKey("controller")) {
            var in = new NbtReader(tag, "gantryTerminal").compound("controller");
            binding = new Binding(
                UUID.fromString(in.string("id")),
                in.enumValue(ControllerKind.class, "kind"),
                in.integer("x"),
                in.integer("y"),
                in.integer("z"));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readMode(tag);
        readBinding(tag);
    }

    private void readMode(NBTTagCompound tag) {
        int ordinal = tag.getInteger("mode");
        TerminalMode[] modes = TerminalMode.values();
        if (ordinal < 0 || ordinal >= modes.length) throw new IllegalStateException("Invalid Gantry terminal mode");
        mode = modes[ordinal];
    }

    @Override
    public Packet getDescriptionPacket() {
        var packet = (S35PacketUpdateTileEntity) super.getDescriptionPacket();
        packet.func_148857_g()
            .setInteger("mode", mode.ordinal());
        if (binding != null) packet.func_148857_g()
            .setTag("controller", encodeBinding());
        return packet;
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        readMode(packet.func_148857_g());
        readBinding(packet.func_148857_g());
    }
}
