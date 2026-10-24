package com.gtnewhorizons.galaxia.compat.gt;

import java.util.Arrays;
import java.util.List;

import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputHatch;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputHatch;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.IGTHatchAdder;

public enum StationHatchElement implements IHatchElement<TileStationBase<?>> {

    Energy(MTEStationPlug.class, MTEStationPlugMulti.class) {

        @Override
        public long count(TileStationBase<?> t) {
            return t.getStationPlugs()
                .size();
        }
    },
    /** Station buses and hatches, which reach the station's storage from the room's wall too */
    StationPort(MTEHatchStationInputBus.class, MTEHatchStationOutputBus.class, MTEHatchStationInputHatch.class,
        MTEHatchStationOutputHatch.class) {

        @Override
        public long count(TileStationBase<?> t) {
            return 0;
        }

        @Override
        public IGTHatchAdder<? super TileStationBase<?>> adder() {
            return (room, hatch, casing) -> matchesHatch(hatch.getMetaTileEntity());
        }
    },
    /** Energy hatches in the wall take energy from cables outside into the station */
    EnergyInput(MTEHatchEnergy.class) {

        @Override
        public long count(TileStationBase<?> t) {
            return 0;
        }

        @Override
        public IGTHatchAdder<? super TileStationBase<?>> adder() {
            return (room, hatch, casing) -> room instanceof TileStation station && station.addEnergyInput(hatch);
        }
    };

    private final List<? extends Class<? extends IMetaTileEntity>> mteClasses;

    @SafeVarargs
    StationHatchElement(Class<? extends IMetaTileEntity>... mteClasses) {
        this.mteClasses = Arrays.asList(mteClasses);
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClasses;
    }

    @Override
    public IGTHatchAdder<? super TileStationBase<?>> adder() {
        return TileStationBase::addStationPlug;
    }
}
