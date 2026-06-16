package com.gtnewhorizons.galaxia.compat.gt;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class MTEStationPlugMulti extends MTEStationPlug {

    public static final int ID = 25000;

    private int amperes;

    public MTEStationPlugMulti(int aID, String aName, String aNameRegional, int aTier, int amperes) {
        super(aID, aName, aNameRegional, aTier);
        this.amperes = amperes;
    }

    public MTEStationPlugMulti(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures, int amperes) {
        super(aName, aTier, aDescription, aTextures);
        this.amperes = amperes;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEStationPlugMulti(mName, mTier, mDescriptionArray, mTextures, amperes);
    }

    @Override
    public long maxAmperesOut() {
        return amperes;
    }

    @Override
    public long maxEUStore() {
        return 512L + GTValues.V[mTier] * 4L * amperes;
    }

}
