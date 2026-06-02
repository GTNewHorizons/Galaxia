package com.gtnewhorizons.galaxia.compat.gt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import lombok.Getter;
import lombok.Setter;

public class MTEHatchStationMaintenance extends MTEHatchMaintenance {

    public static final int ID = 23050;

    @Getter
    @Setter
    private StationGraph graph;

    @Getter
    @Setter
    private int priority;

    @Getter
    @Setter
    private ResourceFilter<ItemStackWrapper> itemFilter = ResourceFilter.forItems();

    @Getter
    @Setter
    private ResourceFilter<FluidKey> fluidFilter = ResourceFilter.forFluids();

    public MTEHatchStationMaintenance(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEHatchStationMaintenance(int aID, String aName, String aNameRegional, int aTier, boolean aAuto) {
        super(aID, aName, aNameRegional, aTier, aAuto);
    }

    public MTEHatchStationMaintenance(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures,
        boolean aAuto) {
        super(aName, aTier, aDescription, aTextures, aAuto);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchStationMaintenance(mName, mTier, mDescriptionArray, mTextures, true);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && graph != null
            && graph.getController() == null
            && aTick % 100L == 0L) {
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mCrowbar = mSolderingTool = false;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("priority", priority);
        NBTTagList filterList = new NBTTagList();
        for (String entry : itemFilter.serialize()) {
            filterList.appendTag(new net.minecraft.nbt.NBTTagString(entry));
        }
        aNBT.setTag("itemFilter", filterList);
        NBTTagList fluidFilterList = new NBTTagList();
        for (String entry : fluidFilter.serialize()) {
            fluidFilterList.appendTag(new net.minecraft.nbt.NBTTagString(entry));
        }
        aNBT.setTag("fluidFilter", fluidFilterList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        priority = aNBT.getInteger("priority");
        if (aNBT.hasKey("itemFilter")) {
            NBTTagList filterList = aNBT.getTagList("itemFilter", Constants.NBT.TAG_STRING);
            List<String> entries = new ArrayList<>();
            for (int i = 0; i < filterList.tagCount(); i++) {
                entries.add(filterList.getStringTagAt(i));
            }
            itemFilter.load(entries);
        }
        if (aNBT.hasKey("fluidFilter")) {
            NBTTagList fluidFilterList = aNBT.getTagList("fluidFilter", Constants.NBT.TAG_STRING);
            List<String> fluidEntries = new ArrayList<>();
            for (int i = 0; i < fluidFilterList.tagCount(); i++) {
                fluidEntries.add(fluidFilterList.getStringTagAt(i));
            }
            fluidFilter.load(fluidEntries);
        }
    }
}
