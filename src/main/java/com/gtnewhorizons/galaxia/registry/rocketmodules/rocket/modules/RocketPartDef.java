package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;

/** Authored module properties. Concrete resources and durability belong to the produced module. */
public record RocketPartDef(int id, @Nonnull String nameKey, int width, int height, double dryMassKg,
    @Nullable String assetFolder, @Nonnull Kind kind, int seats, int decouplerStage, @Nonnull Storage storage,
    @Nullable Engine engine, @Nullable WorkBill manufactureBill, @Nullable WorkBill repairBill, int coreColor,
    @Nullable FuelTankFrame tankFrame, @Nullable RocketFuel fuel) {

    private static final String MODULE_DOMAIN = "textures/model/modules/";

    public RocketPartDef {
        if (id < 0 || nameKey.isBlank() || width <= 0 || height <= 0 || seats < 0 || decouplerStage < 0) {
            throw new IllegalArgumentException("Invalid module identity, shape or role data");
        }
        positive(dryMassKg, "Dry mass");
        if ((kind == Kind.ENGINE) != (engine != null)) {
            throw new IllegalArgumentException("Only an engine module has propulsion settings");
        }
        if (coreColor < -1 || coreColor > 0xFFFFFF) throw new IllegalArgumentException("Invalid module core color");
        if (tankFrame != null && (kind != Kind.FUEL_TANK || fuel == null))
            throw new IllegalArgumentException("A fuel tank frame requires its fuel");
    }

    public RocketPartDef(int id, String nameKey, int width, int height, double dryMassKg, @Nullable String assetFolder,
        Kind kind, int seats, int decouplerStage, Storage storage, @Nullable Engine engine,
        @Nullable WorkBill manufactureBill, @Nullable WorkBill repairBill) {
        this(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            storage,
            engine,
            manufactureBill,
            repairBill,
            -1,
            null,
            null);
    }

    public String name() {
        if (tankFrame != null) return StatCollector
            .translateToLocalFormatted("galaxia.rocket_part.fuel_tank", tankFrame.label, fuel.name());
        if (fuel != null && kind == Kind.ENGINE)
            return StatCollector.translateToLocalFormatted("galaxia.rocket_part.engine", fuel.name());
        return StatCollector.translateToLocal(nameKey);
    }

    public RocketPartDef withFuel(RocketFuel fuel, @Nullable FuelTankFrame frame) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            storage,
            engine,
            manufactureBill,
            repairBill,
            fuel.color(),
            frame,
            fuel);
    }

    @Nonnull
    public static RocketPartDef structural(int id, @Nonnull String nameKey, int width, int height, double dryMassKg,
        @Nullable String assetFolder) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            Kind.STRUCTURAL,
            0,
            0,
            new Storage(0, 0, Map.of()),
            null,
            null,
            null);
    }

    @Nonnull
    public RocketPartDef withStorage(@Nonnull Storage replacement) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            replacement,
            engine,
            manufactureBill,
            repairBill,
            coreColor,
            tankFrame,
            fuel);
    }

    @Nonnull
    public RocketPartDef withEngine(@Nonnull Engine replacement) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            storage,
            replacement,
            manufactureBill,
            repairBill,
            coreColor,
            tankFrame,
            fuel);
    }

    @Nonnull
    public RocketPartDef withManufactureBill(@Nonnull WorkBill replacement) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            storage,
            engine,
            replacement,
            repairBill,
            coreColor,
            tankFrame,
            fuel);
    }

    @Nonnull
    public RocketPartDef withRepairBill(@Nonnull WorkBill replacement) {
        return new RocketPartDef(
            id,
            nameKey,
            width,
            height,
            dryMassKg,
            assetFolder,
            kind,
            seats,
            decouplerStage,
            storage,
            engine,
            manufactureBill,
            replacement,
            coreColor,
            tankFrame,
            fuel);
    }

    @Nullable
    public ResourceLocation modelLocation() {
        return assetFolder == null ? null : LocationGalaxia(MODULE_DOMAIN + assetFolder + "/model.obj");
    }

    @Nullable
    public ResourceLocation textureLocation() {
        return assetFolder == null ? null : LocationGalaxia(MODULE_DOMAIN + assetFolder + "/texture.png");
    }

    public enum Kind {

        CAPSULE,
        FUEL_TANK,
        ENGINE,
        DECOUPLER,
        LANDER,
        RIDER,
        CARGO,
        STRUCTURAL;

        public double wearMultiplier() {
            var config = ConfigRocket.wear;
            return switch (this) {
                case CAPSULE -> config.capsuleMultiplier;
                case FUEL_TANK -> config.tankMultiplier;
                case ENGINE -> config.engineMultiplier;
                case CARGO -> config.cargoMultiplier;
                case LANDER, RIDER -> config.cabinMultiplier;
                case DECOUPLER, STRUCTURAL -> config.structureMultiplier;
            };
        }
    }

    /** The path is measured in its local attractor frames, with disjoint hazardous portions. */
    public double wearPercent(double distanceMeters, double beltDistanceMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0
            || !Double.isFinite(beltDistanceMeters)
            || beltDistanceMeters < 0
            || beltDistanceMeters > distanceMeters) throw new IllegalArgumentException("Invalid travel exposure");
        double effectiveDistance = distanceMeters + (ConfigRocket.wear.asteroidBeltMultiplier - 1) * beltDistanceMeters;
        return effectiveDistance / 1000 / ConfigRocket.wear.kilometresPerPercent * kind.wearMultiplier();
    }

    public record Storage(int itemSlots, long freightFluidCapacity, @Nonnull Map<FluidKey, Long> propellantCapacities) {

        public Storage {
            propellantCapacities = Map.copyOf(propellantCapacities);
            if (itemSlots < 0 || freightFluidCapacity < 0
                || propellantCapacities.values()
                    .stream()
                    .anyMatch(amount -> amount <= 0)) {
                throw new IllegalArgumentException(
                    "Storage capacities must be nonnegative, with positive propellant compartments");
            }
        }
    }

    public record Engine(double thrustNewtons, double exhaustVelocityMps,
        @Nonnull Map<FluidKey, Propellant> propellants) {

        public Engine {
            positive(thrustNewtons, "Thrust");
            positive(exhaustVelocityMps, "Exhaust velocity");
            propellants = Map.copyOf(propellants);
        }
    }

    public record Propellant(double volumeParts, double kilogramsPerUnit) {

        public Propellant {
            positive(volumeParts, "Mixture volume parts");
            positive(kilogramsPerUnit, "Propellant density");
        }
    }

    /** A supplied processing bill. Null on the definition means the operation is unsupported. */
    public record WorkBill(@Nonnull Map<InventoryKey, Long> inputs, long euPerTick, int ticks) {

        public WorkBill {
            inputs = Map.copyOf(inputs);
            if (euPerTick <= 0 || ticks <= 0
                || inputs.values()
                    .stream()
                    .anyMatch(amount -> amount <= 0)) {
                throw new IllegalArgumentException("Work requires positive power, time and input amounts");
            }
        }

        public NBTTagCompound serializeNBT() {
            var out = new NBTTagCompound();
            out.setTag("inputs", InventoryKeyState.encodeAmounts(inputs));
            out.setLong("euPerTick", euPerTick);
            out.setInteger("ticks", ticks);
            return out;
        }

        public static WorkBill deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "moduleWorkBill");
            var inputs = InventoryKeyState.decodeAmounts(in, "inputs");
            return new WorkBill(inputs, in.longValue("euPerTick"), in.integer("ticks"));
        }
    }

    private static void positive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException(label + " must be finite and positive");
    }
}
