
package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

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
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileEntityGantryTerminal)) {
            return;
        }

        TileEntityGantryTerminal gantryTerminal = (TileEntityGantryTerminal) tileEntity;

        // Cycle through valid directions and check for new connections
        for (Vec3 check_offset : GantryAPI.CHECK_OFFSETS) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTileEntity = world.getTileEntity(cx, cy, cz);
            if (checkTileEntity instanceof TileEntityGantry checkGantry) {
                gantryTerminal.connect(checkGantry);
            } else if (checkTileEntity instanceof TileEntitySilo checkSilo) {
                gantryTerminal.connectSilo(checkSilo);
                checkSilo.setGantryTerminal(gantryTerminal);
                if (!world.isRemote) {
                    world.markBlockForUpdate(checkSilo.xCoord, checkSilo.yCoord, checkSilo.zCoord);
                    world.markBlockForUpdate(gantryTerminal.xCoord, gantryTerminal.yCoord, gantryTerminal.zCoord);
                }
            } else if (checkTileEntity instanceof TileEntityModuleAssembler checkAssembler) {
                gantryTerminal.connectAssembler(checkAssembler);
                checkAssembler.setGantryTerminal(gantryTerminal);
                if (!world.isRemote) {
                    world.markBlockForUpdate(checkAssembler.xCoord, checkAssembler.yCoord, checkAssembler.zCoord);
                    world.markBlockForUpdate(gantryTerminal.xCoord, gantryTerminal.yCoord, gantryTerminal.zCoord);
                }
            }

        }

    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity gantry = world.getTileEntity(x, y, z);
        if (!(gantry instanceof TileEntityGantry)) {
            return;
        }
        TileEntityGantry terminal = (TileEntityGantry) gantry;

        // Iterate through neighbours and disconnect them
        for (Vec3 check_offset : new ArrayList<>(terminal.neighbourDirs)) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTileEntity = world.getTileEntity(cx, cy, cz);
            if (checkTileEntity instanceof TileEntityGantry checkGantry) {
                terminal.disconnect(checkGantry);
            }
        }
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
