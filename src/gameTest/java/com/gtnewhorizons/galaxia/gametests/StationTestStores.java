package com.gtnewhorizons.galaxia.gametests;

import java.math.BigInteger;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETransformer;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;
import gregtech.common.tileentities.storage.MTESuperTank;

/**
 * Stand-ins for the station's fluid and energy stores, which are all large multiblocks: a GT super tank holds the
 * station's fluids and a GT transformer marks its energy store
 */
final class StationTestStores {

    private StationTestStores() {}

    /** Lets rooms take super tanks and transformers as stores, with an empty energy store */
    static void register() {
        StationAttachmentRegistry.register(MTESuperTank.class, TANK);
        StationAttachmentRegistry.register(MTETransformer.class, BATTERY);
        batteryStored = 0;
    }

    /** A GT super tank stands in for the station fluid stores, which are all multiblocks */
    static final IFluidStorageHandler<MTEDigitalTankBase> TANK = new IFluidStorageHandler<>() {

        @Override
        public BlockPos getPosition(MTEDigitalTankBase tank) {
            IGregTechTileEntity base = tank.getBaseMetaTileEntity();
            return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
        }

        @Override
        public void tick(MTEDigitalTankBase tank) {}

        @Override
        public boolean isReady(MTEDigitalTankBase tank) {
            return true;
        }

        @Override
        public void onAttached(MTEDigitalTankBase tank, StationGraph graph) {}

        @Override
        public void onDetached(MTEDigitalTankBase tank, StationGraph graph) {}

        @Override
        public void markDirty(MTEDigitalTankBase tank) {}

        @Override
        public long getFluidStored(MTEDigitalTankBase tank) {
            return tank.getFluidAmount();
        }

        @Override
        public long getFluidCapacity(MTEDigitalTankBase tank) {
            return tank.getRealCapacity();
        }

        @Override
        public List<FluidStack> getAllFluids(MTEDigitalTankBase tank) {
            return tank.getFluid() == null ? List.of() : List.of(tank.getFluid());
        }

        @Override
        public FluidStack drainFluid(MTEDigitalTankBase tank, FluidStack resource, boolean doDrain) {
            if (tank.getFluid() == null || !tank.getFluid()
                .isFluidEqual(resource)) return null;
            return tank.drain(resource.amount, doDrain);
        }

        @Override
        public long fillFluid(MTEDigitalTankBase tank, FluidStack resource, boolean doFill) {
            return tank.fill(resource, doFill);
        }

        @Override
        public ResourceFilter<FluidKey> getFluidFilter(MTEDigitalTankBase tank) {
            return ResourceFilter.forFluids();
        }
    };

    /** Energy held by the stand-in energy store */
    static long batteryStored;
    private static final long BATTERY_CAPACITY = 1_000_000_000L;

    /**
     * A GT transformer stands in for the station energy stores, the LSC is a large multiblock. The energy is kept here,
     * the transformer only marks where the store stands
     */
    static final IEnergyHandler<MTETransformer> BATTERY = new IEnergyHandler<>() {

        @Override
        public BlockPos getPosition(MTETransformer store) {
            IGregTechTileEntity base = store.getBaseMetaTileEntity();
            return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
        }

        @Override
        public void tick(MTETransformer store) {}

        @Override
        public boolean isReady(MTETransformer store) {
            return true;
        }

        @Override
        public void onAttached(MTETransformer store, StationGraph graph) {}

        @Override
        public void onDetached(MTETransformer store, StationGraph graph) {}

        @Override
        public void markDirty(MTETransformer store) {}

        @Override
        public BigInteger getEnergyStored(MTETransformer store) {
            return BigInteger.valueOf(batteryStored);
        }

        @Override
        public BigInteger getEnergyCapacity(MTETransformer store) {
            return BigInteger.valueOf(BATTERY_CAPACITY);
        }

        @Override
        public long getPassiveDrain(MTETransformer store) {
            return 0;
        }

        @Override
        public long getInputRate(MTETransformer store) {
            return 0;
        }

        @Override
        public long getOutputRate(MTETransformer store) {
            return 0;
        }

        @Override
        public long drawEnergy(MTETransformer store, long amount) {
            long drawn = Math.min(amount, batteryStored);
            batteryStored -= drawn;
            return drawn;
        }

        @Override
        public long addEnergy(MTETransformer store, long amount) {
            long added = Math.min(amount, BATTERY_CAPACITY - batteryStored);
            batteryStored += added;
            return added;
        }
    };
}
