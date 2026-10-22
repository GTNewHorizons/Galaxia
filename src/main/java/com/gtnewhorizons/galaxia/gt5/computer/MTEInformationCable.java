package com.gtnewhorizons.galaxia.gt5.computer;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.render.TextureFactory;

/** Painted point-to-point information transport, isolated from native TecTech packet protocols. */
public final class MTEInformationCable extends MetaPipeEntity {

    public MTEInformationCable(int id, String name, String regionalName) {
        super(id, name, 0);
    }

    private MTEInformationCable(String name) {
        super(name, 0);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity base) {
        return new MTEInformationCable(mName);
    }

    @Override
    public void onPostTick(IGregTechTileEntity base, long tick) {
        if (!base.isServerSide() || tick % 20L != 0L) return;
        byte connections = 0;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (connects(base, neighbor(base, side), side)) connections |= side.flag;
        }
        if (mConnections != connections) {
            mConnections = connections;
            base.issueTextureUpdate();
        }
    }

    static IGregTechTileEntity neighbor(IGregTechTileEntity base, ForgeDirection side) {
        int x = base.getXCoord() + side.offsetX;
        int z = base.getZCoord() + side.offsetZ;
        if (!base.getWorld()
            .getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return null;
        return base.getIGregTechTileEntityAtSide(side);
    }

    static boolean connects(IGregTechTileEntity from, IGregTechTileEntity to, ForgeDirection side) {
        if (to == null || from.getColorization() < 0 || from.getColorization() != to.getColorization()) return false;
        if (to.getMetaTileEntity() instanceof MTEInformationCable) return true;
        return to.getMetaTileEntity() instanceof MTEInformationPort && to.getFrontFacing() == side.getOpposite();
    }

    /** Resolves only loaded, matching, unbranched cable paths. Paint is adjacency, never global identity. */
    static MTEInformationPort peer(IGregTechTileEntity start) {
        ForgeDirection facing = start.getFrontFacing();
        IGregTechTileEntity previous = start;
        IGregTechTileEntity current = neighbor(start, facing);
        if (!connects(start, current, facing)) return null;
        Set<IGregTechTileEntity> visited = new HashSet<>();
        visited.add(start);
        for (int length = 0; length < 1024 && current != null && visited.add(current); length++) {
            if (current.getMetaTileEntity() instanceof MTEInformationPort port) return port;
            if (!(current.getMetaTileEntity() instanceof MTEInformationCable)) return null;
            IGregTechTileEntity next = null;
            int connections = 0;
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                IGregTechTileEntity candidate = neighbor(current, side);
                if (!connects(current, candidate, side)) continue;
                connections++;
                if (candidate != previous) next = candidate;
            }
            if (connections != 2) return null;
            previous = current;
            current = next;
        }
        return null;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, int connections, int color,
        boolean connected, boolean redstone) {
        return new ITexture[] { TextureFactory.of(Textures.BlockIcons.MACHINE_CASING_SOLID_STEEL) };
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchPipeLevel0.toTileEntityBaseType();
    }

    @Override
    public float getCollisionThickness() {
        return 0.25F;
    }

    @Override
    public boolean renderInside(ForgeDirection side) {
        return false;
    }

    @Override
    public String[] getDescription() {
        return new String[] { net.minecraft.util.StatCollector.translateToLocal("galaxia.computer.cable.description") };
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound tag) {}

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound tag) {}

    @Override
    public boolean allowPutStack(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity base, int slot, ForgeDirection side, ItemStack stack) {
        return false;
    }
}
