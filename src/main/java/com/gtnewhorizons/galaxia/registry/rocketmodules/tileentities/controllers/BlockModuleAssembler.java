package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;

/**
 * Block for the Module Assembler
 */
public class BlockModuleAssembler extends BlockRocketController implements ITileEntityProvider {

    public BlockModuleAssembler() {
        super(
            "galaxia:machine/module_assembler_on",
            "galaxia:machine/module_assembler_off",
            () -> GalaxiaBlocksEnum.RUSTY_PANEL.get()); // keep as lambda - ensures we load lazily
    }

    /**
     * Creates a tile entity bound to this block
     *
     * @param worldIn The world the tile entity is in (i.e. world of this block)
     * @param meta    The metadata of the tile entity
     * @return TileEntity (in this case the MA)
     */
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityModuleAssembler();
    }

    /**
     * Handler for the logic of the block being activated (Right click by player)
     *
     * @param world  The world the block is in
     * @param x      X coordinate of the block
     * @param y      Y coordinate of the block
     * @param z      Z coordinate of the block
     * @param player The player activating the block
     * @param side   The side the player interacted with (not relevant here as of
     *               yet)
     * @param hitX   The hitbox X coordinate (Not used in this implementation)
     * @param hitY   The hitbox Y coordinate (Not used in this implementation)
     * @param hitZ   The hitbox Z coordinate (Not used in this implementation)
     * @return Boolean : True => activated
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleAssembler) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public ForgeDirection getFacing(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleAssembler assembler) {
            return assembler.isStructureValid() ? assembler.getCurrentFacing()
                .getDirection() : assembler.getPlacedFacing();
        }
        return ForgeDirection.NORTH;
    }

    @Override
    public boolean isFormed(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleAssembler assembler) return assembler.isStructureValid();
        return false;
    }
}
