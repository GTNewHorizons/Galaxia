package com.gtnewhorizons.galaxia.registry.block.tile;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.registry.block.base.BlockSpaceAir;

public class TileNoduleController extends TileEntity implements IGuiHolder<PosGuiData> {

    private boolean isDepressurized = false;

    public void depressurize() {
        this.isDepressurized = true;
    }

    public void repressurize() {
        if (!isDepressurized) return;
        this.isDepressurized = false;
        int x = this.xCoord, y = this.yCoord, z = this.zCoord;
        Block spaceAir = Block.getBlockFromName("galaxia:space_air");
        for (int[] d : BlockSpaceAir.adjacents) {
            if (BlockSpaceAir.isDepressurized(this.worldObj, x + d[0], y + d[1], z + d[2])) {
                this.worldObj.setBlock(x + d[0], y + d[1], z + d[2], spaceAir, 2, 2);
                this.worldObj.notifyBlockOfNeighborChange(x + d[0], y + d[1], z + d[2], spaceAir);
            }
        }
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new ModularPanel("galaxia:rocket_silo").size(210, 130)
            .child(
                IKey.str("nodule_controller")
                    .asWidget()
                    .pos(8, 8))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(
                        IKey.str((isDepressurized ? "§e" : "§7") + "repressurize")
                            .alignment(Alignment.CENTER))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        repressurize();
                    })));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("isDepressurized", isDepressurized);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        isDepressurized = nbt.getBoolean("isDepressurized");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
