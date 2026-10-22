package com.gtnewhorizons.galaxia.compat.gt.computer;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.ofHatchAdderOptional;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.gt5.computer.ComputerContent;
import com.gtnewhorizons.galaxia.gt5.computer.MTEInformationPort;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.ComputerDataProcessor;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Physical Computer hardware. Structures share ordinary GT power and fluid handling. */
public final class MTEComputer extends MTEEnhancedMultiBlockBase<MTEComputer> {

    public enum Kind {
        CONTROLLER,
        CLUSTER,
        RADIATOR
    }

    private static IStructureDefinition<MTEComputer> structure;
    private final Kind kind;
    private final List<MTEInformationPort> ports = new ArrayList<>();
    private final List<ComputerContent.RackTier> racks = new ArrayList<>();
    private UUID machineId = UUID.randomUUID();
    private UUID registeredTeam;
    private CelestialObjectKey registeredBody;
    private ComputerDataProcessor processor;
    private long processedAtTick = Long.MIN_VALUE;
    private long remainingPaidCapacity;
    private long lastProcessed;

    public MTEComputer(int id, String name, String regionalName, Kind kind) {
        super(id, name, regionalName);
        this.kind = kind;
    }

    private MTEComputer(String name, Kind kind) {
        super(name);
        this.kind = kind;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity base) {
        return new MTEComputer(mName, kind);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public IStructureDefinition<MTEComputer> getStructureDefinition() {
        if (structure == null) {
            structure = IStructureDefinition.<MTEComputer>builder()
                .addShape(
                    "controller",
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addShape(
                    "cluster",
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC", "CCC", "CCC" }, { "C~C", "CRC", "CRC", "CRC", "CCC" },
                            { "CCC", "CCC", "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofHatchAdderOptional(MTEComputer::addComputerHatch, 48, 1, ComputerContent.hardware, 3))
                .addElement(
                    'R',
                    ofChain(
                        onElementPass(
                            m -> m.racks.add(ComputerContent.RackTier.EV),
                            ofBlock(ComputerContent.hardware, 0)),
                        onElementPass(
                            m -> m.racks.add(ComputerContent.RackTier.IV),
                            ofBlock(ComputerContent.hardware, 1)),
                        onElementPass(
                            m -> m.racks.add(ComputerContent.RackTier.LUV),
                            ofBlock(ComputerContent.hardware, 2)),
                        ofBlock(net.minecraft.init.Blocks.air, 0)))
                .build();
        }
        return structure;
    }

    private String shape() {
        return kind == Kind.CLUSTER ? "cluster" : "controller";
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(shape(), stack, hintsOnly, 1, 1, 0);
    }

    @Override
    public void checkMachine(IGregTechTileEntity base, ItemStack stack, List<StructureError> errors) {
        disconnectPorts();
        racks.clear();
        if (!checkPiece(shape(), 1, 1, 0, errors)) return;
        if (kind != Kind.RADIATOR && mEnergyHatches.isEmpty())
            errors.add(StructureErrors.of("galaxia.computer.error.energy"));
        if (kind != Kind.CONTROLLER && (mInputHatches.isEmpty() || mOutputHatches.isEmpty()))
            errors.add(StructureErrors.of("galaxia.computer.error.coolant"));
        if (kind == Kind.CLUSTER && (racks.isEmpty() || ports.size() != 1))
            errors.add(StructureErrors.of("galaxia.computer.error.cluster"));
        if (kind == Kind.CONTROLLER && (ports.isEmpty() || ports.size() > 8))
            errors.add(StructureErrors.of("galaxia.computer.error.ports"));
    }

    private boolean addComputerHatch(IGregTechTileEntity tile, int texture) {
        if (kind != Kind.RADIATOR && tile.getMetaTileEntity() instanceof MTEInformationPort port) {
            if (port.controller() != null && port.controller() != this && port.controller().mMachine) return false;
            port.updateTexture(texture);
            port.attach(this);
            ports.add(port);
            return true;
        }
        if (kind != Kind.RADIATOR && addEnergyInputToMachineList(tile, texture)) return true;
        return kind != Kind.CONTROLLER
            && (addInputHatchToMachineList(tile, texture) || addOutputHatchToMachineList(tile, texture));
    }

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        super.onPostTick(base, tick);
        if (!base.isServerSide()) return;
        if (kind != Kind.CLUSTER) lastProcessed = 0L;
        if (kind == Kind.CONTROLLER) tickController(base);
        else if (kind == Kind.RADIATOR && mMachine && isAllowedToWork()) tickRadiator(base);
        long worldTick = base.getWorld()
            .getTotalWorldTime();
        base.setActive(
            kind == Kind.CLUSTER ? processedAtTick == worldTick || processedAtTick == worldTick - 1L
                : lastProcessed > 0L);
    }

    private void tickController(IGregTechTileEntity base) {
        UUID team = GTTeamsCompat.getTeam(base.getOwnerUuid());
        CelestialObjectKey body = GalaxiaCelestialAPI.findByDimension(base.getWorld().provider.dimensionId)
            .map(b -> b.key())
            .orElse(null);
        if (team == null || body == null) {
            detachRuntime();
            return;
        }
        if (!team.equals(registeredTeam) || !body.equals(registeredBody)) {
            detachRuntime();
            registeredTeam = team;
            registeredBody = body;
            processor = new ComputerDataProcessor(
                machineId,
                team,
                body,
                SatelliteDataType.PROSPECTING,
                SatelliteDataType.PROCESSED_PROSPECTING);
        }
        Set<MTEComputer> clusters = linkedClusters(team);
        boolean running = mMachine && isAllowedToWork() && !clusters.isEmpty() && payEnergy(32L);
        processor.setProcessingCapacity(
            running ? clusters.stream()
                .mapToLong(MTEComputer::capacity)
                .sum() : 0L);
        SatelliteNetworkService.registerEndpoint(team, processor);
        if (running) {
            lastProcessed = processor.process(requested -> {
                long completed = 0L;
                for (MTEComputer cluster : clusters) {
                    completed += cluster.process(requested - completed, this);
                    if (completed >= requested) break;
                }
                return completed;
            });
        }
    }

    private Set<MTEComputer> linkedClusters(UUID team) {
        Set<MTEComputer> clusters = new java.util.LinkedHashSet<>();
        for (MTEInformationPort port : ports) {
            if (!port.isValid() || port.controller() != this) continue;
            MTEInformationPort peer = port.peer();
            if (peer != null && peer.controller() instanceof MTEComputer cluster
                && cluster.kind == Kind.CLUSTER
                && cluster.mMachine
                && cluster.isAllowedToWork()
                && team.equals(
                    GTTeamsCompat.getTeam(
                        cluster.getBaseMetaTileEntity()
                            .getOwnerUuid())))
                clusters.add(cluster);
        }
        return clusters;
    }

    private long process(long requested, MTEComputer controller) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        long tick = base.getWorld()
            .getTotalWorldTime();
        if (requested <= 0L || !mMachine || !isAllowedToWork() || ports.size() != 1) return 0L;
        MTEInformationPort peer = ports.get(0)
            .peer();
        if (peer == null || peer.controller() != controller) return 0L;
        if (processedAtTick != tick) {
            long capacity = capacity();
            long eu = racks.stream()
                .mapToLong(ComputerContent.RackTier::euPerTick)
                .sum();
            int coolant = racks.stream()
                .mapToInt(ComputerContent.RackTier::coolantPerTick)
                .sum();
            if (capacity <= 0L
                || !convertCoolant(ComputerContent.chilled(coolant), ComputerContent.neutral(coolant), eu)) return 0L;
            processedAtTick = tick;
            remainingPaidCapacity = capacity;
            lastProcessed = 0L;
        }
        long completed = Math.min(remainingPaidCapacity, requested);
        remainingPaidCapacity -= completed;
        lastProcessed += completed;
        base.setActive(true);
        return completed;
    }

    private long capacity() {
        return racks.stream()
            .mapToLong(ComputerContent.RackTier::capacityDeciKb)
            .sum();
    }

    private void tickRadiator(IGregTechTileEntity base) {
        if (!base.getWorld()
            .canBlockSeeTheSky(base.getXCoord(), base.getYCoord() + 2, base.getZCoord())) return;
        int amount = GalaxiaCelestialAPI.machineEnvironment(base.getWorld())
            .map(environment -> (int) Math.round(20.0D * environment.radiatorEfficiency()))
            .orElse(0);
        if (amount > 0 && convertCoolant(ComputerContent.neutral(amount), ComputerContent.chilled(amount), 0L))
            lastProcessed = amount;
    }

    private boolean convertCoolant(FluidStack input, FluidStack output, long eu) {
        if (!depleteInput(input, true) || storedEnergy() < eu) return false;
        FluidEjectionHelper admission = new FluidEjectionHelper(getOutputHatches(), true);
        if (admission.ejectStack(output.copy()) != output.amount) return false;
        depleteInput(input);
        drainEnergyInput(eu);
        admission.commit();
        updateSlots();
        return true;
    }

    private long storedEnergy() {
        return mEnergyHatches.stream()
            .filter(hatch -> hatch.isValid())
            .mapToLong(
                hatch -> hatch.getBaseMetaTileEntity()
                    .getStoredEU())
            .sum();
    }

    private boolean payEnergy(long eu) {
        return storedEnergy() >= eu && drainEnergyInput(eu);
    }

    private void detachRuntime() {
        if (registeredTeam != null) SatelliteNetworkService.unregisterEndpoint(registeredTeam, machineId);
        processor = null;
        registeredTeam = null;
        registeredBody = null;
    }

    private void disconnectPorts() {
        for (MTEInformationPort port : ports) if (port.controller() == this) port.attach(null);
        ports.clear();
    }

    @Override
    public void onUnload() {
        detachRuntime();
        disconnectPorts();
        mMachine = false;
        remainingPaidCapacity = 0L;
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        detachRuntime();
        disconnectPorts();
        super.onRemoval();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setString("computerId", machineId.toString());
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        if (tag.hasKey("computerId")) machineId = UUID.fromString(tag.getString("computerId"));
        detachRuntime();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(
            translateToLocal(
                "galaxia.computer.type." + kind.name()
                    .toLowerCase(java.util.Locale.ROOT)))
            .addInfo(
                translateToLocal(
                    "galaxia.computer.description." + kind.name()
                        .toLowerCase(java.util.Locale.ROOT)))
            .beginStructureBlock(3, 3, kind == Kind.CLUSTER ? 5 : 3, false)
            .addController(translateToLocal("galaxia.computer.controller_position"))
            .addStructureInfo(
                translateToLocal(
                    "galaxia.computer.structure." + kind.name()
                        .toLowerCase(java.util.Locale.ROOT)))
            .toolTipFinisher();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        return new ITexture[] { TextureFactory.of(Textures.BlockIcons.MACHINE_CASING_SOLID_STEEL) };
    }
}
