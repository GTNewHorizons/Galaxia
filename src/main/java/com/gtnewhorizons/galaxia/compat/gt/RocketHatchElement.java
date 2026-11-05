package com.gtnewhorizons.galaxia.compat.gt;

import java.util.Arrays;
import java.util.List;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.IGTHatchAdder;

/**
 * GT hatches the rocket multiblocks accept, wired into {@link RocketHatches} of the host
 */
public enum RocketHatchElement implements IHatchElement<IRocketHatchHost> {

    InputBus(MTEHatchInputBus.class) {

        @Override
        public IGTHatchAdder<? super IRocketHatchHost> adder() {
            return (host, tile, casing) -> host.getHatches()
                .addInputBus(tile, casing);
        }

        @Override
        public long count(IRocketHatchHost host) {
            return host.getHatches()
                .inputBusCount();
        }
    },
    InputHatch(MTEHatchInput.class) {

        @Override
        public IGTHatchAdder<? super IRocketHatchHost> adder() {
            return (host, tile, casing) -> host.getHatches()
                .addInputHatch(tile, casing);
        }

        @Override
        public long count(IRocketHatchHost host) {
            return host.getHatches()
                .inputHatchCount();
        }
    },
    OutputBus(MTEHatchOutputBus.class) {

        @Override
        public IGTHatchAdder<? super IRocketHatchHost> adder() {
            return (host, tile, casing) -> host.getHatches()
                .addOutputBus(tile, casing);
        }

        @Override
        public long count(IRocketHatchHost host) {
            return host.getHatches()
                .outputBusCount();
        }
    };

    private final List<? extends Class<? extends IMetaTileEntity>> mteClasses;

    @SafeVarargs
    RocketHatchElement(Class<? extends IMetaTileEntity>... mteClasses) {
        this.mteClasses = Arrays.asList(mteClasses);
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClasses;
    }
}
