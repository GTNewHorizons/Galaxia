
package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class BlockGantryTerminal extends Block implements ITileEntityProvider {

    public BlockGantryTerminal() {
        super(Material.iron);
        this.setBlockTextureName("gold_block");
        this.setHardness(1.5F);
        this.setResistance(10.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantryTerminal();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileEntityGantry)) {
            return false;
        }
        // Debug message in chat
        if (player.isSneaking()) {
            player.addChatComponentMessage(
                new ChatComponentText("Is connected: " + GantryAPI.terminatesWithTerminals(world, x, y, z)));
            return true;
        }
        // Debug message in chat
        TileEntityGantryTerminal terminal = (TileEntityGantryTerminal) tileEntity;
        player.addChatComponentMessage(
            new ChatComponentText(
                "Module: " + terminal.getModule()
                    + ", Direction: "
                    + terminal.getDirection()
                    + ", Silo: "
                    + terminal.getSilo()
                    + ", Assembler"
                    + terminal.getAssembler()));
        return true;

    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

}
