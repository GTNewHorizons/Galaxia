package com.gtnewhorizons.galaxia.registry.block.machine;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityGalaxiaMachine;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class BlockGalaxiaMachine extends Block implements ITileEntityProvider {

    @SideOnly(Side.CLIENT)
    private IIcon textureActive;
    @SideOnly(Side.CLIENT)
    private IIcon textureInactive;

    private final String textureActivePath;
    private final String textureInactivePath;

    protected BlockGalaxiaMachine(String textureActivePath, String textureInactivePath) {
        super(Material.iron);
        this.textureActivePath = textureActivePath;
        this.textureInactivePath = textureInactivePath;
        setHardness(3.0f);
        setResistance(10.0f);
        setHarvestLevel("pickaxe", 1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        textureActive = reg.registerIcon(textureActivePath);
        textureInactive = reg.registerIcon(textureInactivePath);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        // meta 1 = active state written by TE on markBlockForUpdate
        return meta == 1 ? textureActive : textureInactive;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityGalaxiaMachine) {
            GuiFactories.tileEntity()
                .open(player, x, y, z);
        }
        return true;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }
}
