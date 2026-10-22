package com.gtnewhorizons.galaxia.gt5.computer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Physical connector shared by a controller and its independently supplied clusters. */
public final class MTEInformationPort extends MTEHatch {

    private MTEMultiBlockBase controller;

    public MTEInformationPort(int id, String name, String regionalName) {
        super(id, name, regionalName, 4, 0, new String[0]);
    }

    private MTEInformationPort(String name, String[] description, ITexture[][][] textures) {
        super(name, 4, 0, description, textures);
    }

    public void attach(MTEMultiBlockBase machine) {
        controller = machine;
    }

    public MTEMultiBlockBase controller() {
        return controller;
    }

    public MTEInformationPort peer() {
        return MTEInformationCable.peer(getBaseMetaTileEntity());
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity base) {
        return new MTEInformationPort(mName, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture casing) {
        return new ITexture[] { casing };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture casing) {
        return getTexturesActive(casing);
    }

    @Override
    public boolean isFacingValid(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN;
    }

    @Override
    public boolean isAccessAllowed(net.minecraft.entity.player.EntityPlayer player) {
        return true;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }

}
