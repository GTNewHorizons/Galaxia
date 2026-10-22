package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketDestination;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlightPlanner;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlights;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketWorldData;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.SiloAutomation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;

/**
 * Everything the silo screens show that only the server knows, packed into one NBT tag so a single sync value keeps
 * the whole screen up to date
 */
public final class SiloGuiState {

    /**
     * A silo the rocket can fly to
     *
     * @param destination Where it is
     * @param name        Its name
     * @param body        Localization key of the celestial body it is on
     * @param deltaV      Delta-v needed to get there in m/s
     */
    public record SiloEntry(RocketDestination destination, String name, String body, double deltaV) {}

    /**
     * Fuel in one tank
     *
     * @param name     Display name of the tank module
     * @param fuel     Key of the fuel
     * @param amount   Stored mB
     * @param capacity Capacity in mB
     */
    public record TankEntry(String name, String fuel, int amount, int capacity) {}

    public RocketBuildStatus buildStatus = RocketBuildStatus.IDLE;
    public String problem = "";
    public boolean unloading;
    public String siloName = "";
    public RocketDestination destination = RocketDestination.NONE;
    public String destinationName = "";

    public boolean automationEnabled;
    public boolean returnArrivals;
    public SiloAutomation.Status automationStatus = SiloAutomation.Status.DISABLED;
    public final int[] requestAmount = new int[SiloAutomation.MAX_REQUESTS];
    public final int[] requestDelivered = new int[SiloAutomation.MAX_REQUESTS];
    public final int[] requestInFlight = new int[SiloAutomation.MAX_REQUESTS];

    public double availableDeltaV;
    public double requiredDeltaV;
    public final List<TankEntry> tanks = new ArrayList<>();
    public int cargoSlots;
    public int cargoUsed;

    public int inputBuses;
    public int inputHatches;
    public int outputBuses;
    public int assemblers;
    public boolean hasTerminal;

    public final List<SiloEntry> silos = new ArrayList<>();

    /**
     * Collects the state on the server
     *
     * @param silo The silo the screen belongs to
     *
     * @return The packed state
     */
    public static NBTTagCompound write(TileEntitySilo silo) {
        NBTTagCompound tag = new NBTTagCompound();
        int dimension = silo.getWorldObj().provider.dimensionId;

        tag.setInteger(
            "status",
            silo.getBuildStatus()
                .ordinal());
        tag.setString("problem", silo.getLastProblem());
        tag.setBoolean("unloading", silo.isUnloading());
        tag.setString("name", silo.getSiloName());
        tag.setTag(
            "destination",
            silo.getDestination()
                .serializeNBT());

        SiloAutomation automation = silo.getAutomation();
        tag.setBoolean("autoEnabled", automation.isEnabled());
        tag.setBoolean("returnArrivals", automation.isReturnArrivals());
        tag.setInteger(
            "autoStatus",
            automation.getStatus()
                .ordinal());
        int[] amounts = new int[SiloAutomation.MAX_REQUESTS];
        int[] delivered = new int[SiloAutomation.MAX_REQUESTS];
        int[] inFlight = new int[SiloAutomation.MAX_REQUESTS];
        for (int i = 0; i < SiloAutomation.MAX_REQUESTS; i++) {
            SiloAutomation.Request request = automation.getRequests()
                .get(i);
            amounts[i] = request.getAmount();
            delivered[i] = request.getDelivered();
            inFlight[i] = request.getInFlight();
        }
        tag.setIntArray("reqAmount", amounts);
        tag.setIntArray("reqDelivered", delivered);
        tag.setIntArray("reqInFlight", inFlight);

        RocketBlueprint rocket = silo.getBuiltBlueprint();
        NBTTagList tanks = new NBTTagList();
        int slots = 0;
        int used = 0;
        for (RocketPartInstance part : rocket.getParts()) {
            if (part.def() instanceof FuelTankPartDef tank) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("name", tank.name());
                entry.setString(
                    "fuel",
                    tank.fuel()
                        .key());
                entry.setInteger(
                    "amount",
                    silo.getLoadout()
                        .getFuel(part));
                entry.setInteger("capacity", tank.fuelCapacity());
                tanks.appendTag(entry);
            }
            if (part.def() instanceof StoragePartDef) {
                for (ItemStack stack : silo.getLoadout()
                    .getCargo(part)) {
                    slots++;
                    if (stack != null) used++;
                }
            }
        }
        tag.setTag("tanks", tanks);
        tag.setInteger("cargoSlots", slots);
        tag.setInteger("cargoUsed", used);

        if (!rocket.isEmpty()) {
            RocketAssembly assembly = rocket.analyze();
            tag.setDouble("availableDv", assembly.getDeltaV(silo.getLoadout()));
        }
        RocketDestination destination = silo.getDestination();
        if (destination.isSet()) {
            tag.setDouble("requiredDv", RocketFlightPlanner.requiredDeltaV(dimension, destination.dimension()));
        }

        tag.setInteger(
            "inputBuses",
            silo.getHatches()
                .inputBusCount());
        tag.setInteger(
            "inputHatches",
            silo.getHatches()
                .inputHatchCount());
        tag.setInteger(
            "outputBuses",
            silo.getHatches()
                .outputBusCount());
        tag.setBoolean("hasTerminal", silo.getGantryTerminal() != null);
        tag.setInteger(
            "assemblers",
            GantryAPI.findAssemblers(silo.getGantryTerminal())
                .size());

        NBTTagList siloList = new NBTTagList();
        for (RocketWorldData.SiloRecord record : RocketFlights.getSilos()) {
            if (record.dimension() == dimension && record.pos()
                .equals(silo.getPos())) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag(
                "destination",
                RocketDestination.ofSilo(record.dimension(), record.pos())
                    .serializeNBT());
            entry.setString("name", record.name());
            entry.setString("body", bodyKey(record.dimension()));
            entry.setDouble("dv", RocketFlightPlanner.requiredDeltaV(dimension, record.dimension()));
            siloList.appendTag(entry);
            if (destination.isSilo() && destination.dimension() == record.dimension()
                && destination.silo()
                    .equals(record.pos())) {
                tag.setString("destinationName", record.name());
            }
        }
        tag.setTag("silos", siloList);
        return tag;
    }

    /** Lang key of the body a dimension belongs to */
    public static String bodyKey(int dimension) {
        return GalaxiaCelestialAPI.findByDimension(dimension)
            .map(CelestialObject::nameKey)
            .orElse("galaxia.silo.destination.unknown_body");
    }

    /**
     * Unpacks the state on the client
     *
     * @param tag The tag written by {@link #write(TileEntitySilo)}
     */
    public void read(NBTTagCompound tag) {
        if (tag == null) return;
        int status = tag.getInteger("status");
        buildStatus = status >= 0 && status < RocketBuildStatus.values().length ? RocketBuildStatus.values()[status]
            : RocketBuildStatus.IDLE;
        problem = tag.getString("problem");
        unloading = tag.getBoolean("unloading");
        siloName = tag.getString("name");
        destination = RocketDestination.deserializeNBT(tag.getCompoundTag("destination"));
        destinationName = tag.getString("destinationName");

        automationEnabled = tag.getBoolean("autoEnabled");
        returnArrivals = tag.getBoolean("returnArrivals");
        int autoStatus = tag.getInteger("autoStatus");
        automationStatus = autoStatus >= 0 && autoStatus < SiloAutomation.Status.values().length
            ? SiloAutomation.Status.values()[autoStatus]
            : SiloAutomation.Status.DISABLED;
        copy(tag.getIntArray("reqAmount"), requestAmount);
        copy(tag.getIntArray("reqDelivered"), requestDelivered);
        copy(tag.getIntArray("reqInFlight"), requestInFlight);

        availableDeltaV = tag.getDouble("availableDv");
        requiredDeltaV = tag.getDouble("requiredDv");
        tanks.clear();
        NBTTagList tankList = tag.getTagList("tanks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tankList.tagCount(); i++) {
            NBTTagCompound entry = tankList.getCompoundTagAt(i);
            tanks.add(
                new TankEntry(
                    entry.getString("name"),
                    entry.getString("fuel"),
                    entry.getInteger("amount"),
                    entry.getInteger("capacity")));
        }
        cargoSlots = tag.getInteger("cargoSlots");
        cargoUsed = tag.getInteger("cargoUsed");

        inputBuses = tag.getInteger("inputBuses");
        inputHatches = tag.getInteger("inputHatches");
        outputBuses = tag.getInteger("outputBuses");
        assemblers = tag.getInteger("assemblers");
        hasTerminal = tag.getBoolean("hasTerminal");

        silos.clear();
        NBTTagList siloList = tag.getTagList("silos", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < siloList.tagCount(); i++) {
            NBTTagCompound entry = siloList.getCompoundTagAt(i);
            silos.add(
                new SiloEntry(
                    RocketDestination.deserializeNBT(entry.getCompoundTag("destination")),
                    entry.getString("name"),
                    entry.getString("body"),
                    entry.getDouble("dv")));
        }
    }

    private static void copy(int[] from, int[] to) {
        for (int i = 0; i < to.length; i++) to[i] = i < from.length ? from[i] : 0;
    }

    /** Destination encoded for the string sync value, empty for none */
    public static String encode(RocketDestination destination) {
        if (!destination.isSet()) return "";
        if (!destination.isSilo()) return Integer.toString(destination.dimension());
        BlockPos pos = destination.silo();
        return destination.dimension() + ":" + pos.x() + ":" + pos.y() + ":" + pos.z();
    }

    public static RocketDestination decode(String value) {
        if (value == null || value.isEmpty()) return RocketDestination.NONE;
        try {
            String[] parts = value.split(":");
            int dimension = Integer.parseInt(parts[0]);
            if (parts.length < 4) return RocketDestination.ofDimension(dimension);
            return RocketDestination.ofSilo(
                dimension,
                new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException e) {
            return RocketDestination.NONE;
        }
    }
}
