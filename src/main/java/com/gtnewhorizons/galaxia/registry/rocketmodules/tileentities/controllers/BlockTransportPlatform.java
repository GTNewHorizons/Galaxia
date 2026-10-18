package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;

/** Native controller block, kept separate because Minecraft registers its tile provider. */
public class BlockTransportPlatform extends BlockRocketController implements ITileEntityProvider {

    private final CelestialAsset.Endpoint.Kind kind;

    public BlockTransportPlatform(CelestialAsset.Endpoint.Kind kind) {
        super(
            "galaxia:machine/launchpad_controller",
            "galaxia:machine/launchpad_controller",
            () -> GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get());
        if (kind != CelestialAsset.Endpoint.Kind.LANDING_PAD && kind != CelestialAsset.Endpoint.Kind.DOCK) {
            throw new IllegalArgumentException("Transport platforms are Landing Pads or Docks");
        }
        this.kind = kind;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityTransportPlatform(kind);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        var tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityTransportPlatform pad && pad.machineIdentity()
            .canUse(player, TeamAction.CONFIGURE_LOGISTICS)) {
            pad.refreshEndpoint();
            GuiFactories.tileEntity()
                .open(player, x, y, z);
            return true;
        }
        return false;
    }

    @Override
    public ForgeDirection getFacing(IBlockAccess world, int x, int y, int z) {
        var tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityTransportPlatform pad ? pad.getPlacedFacing() : ForgeDirection.NORTH;
    }

    @Override
    public boolean isFormed(IBlockAccess world, int x, int y, int z) {
        var tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityTransportPlatform pad && pad.isStructureValid();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileEntityTransportPlatform pad)
            pad.endpointDestroyed();
        super.breakBlock(world, x, y, z, block, metadata);
    }
}
