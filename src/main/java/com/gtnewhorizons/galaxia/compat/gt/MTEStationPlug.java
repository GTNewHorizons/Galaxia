package com.gtnewhorizons.galaxia.compat.gt;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import lombok.Getter;
import lombok.Setter;

public class MTEStationPlug extends MTEHatchDynamo {

    public static final int ID = 23500;

    @Getter
    @Setter
    private StationGraph graph;

    public MTEStationPlug(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEStationPlug(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEStationPlug(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || graph == null) return;
        long threshold = maxEUStore() / 2;
        if (getEUVar() >= threshold) return;
        long needed = Math.min(maxEUStore() - getEUVar(), GTValues.V[mTier] * maxAmperesOut());

        if (graph == null) return;
        long drawn = graph.drawEnergy(needed);
        if (drawn > 0) {
            setEUVar(getEUVar() + drawn);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
    }
}
