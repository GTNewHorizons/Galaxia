package com.gtnewhorizons.galaxia.compat.gt;

import java.util.Arrays;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.IGTHatchAdder;

public enum StationHatchElement implements IHatchElement<TileStationBase<?>> {

    Energy(MTEStationPlug.class, MTEStationPlugMulti.class) {

        @Override
        public long count(TileStationBase<?> t) {
            return t.getStationPlugs()
                .size();
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
