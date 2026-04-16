package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;

public class TileEntityRocketTrophy extends TileEntity implements IGuiHolder<PosGuiData> {

    private ItemStack schematic = null;

    private float offsetX = 0f;
    private float offsetY = 1f;
    private float offsetZ = 0f;
    private float yaw = 0f;
    private float pitch = 0f;
    private float scale = 1f;

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_trophy_main")
            .size(300, 160);

        panel.child(
            IKey.str(
                EnumChatFormatting.BOLD + StatCollector.translateToLocal("galaxia.gui.rocket_trophy.title")
                    + EnumChatFormatting.RESET)
                .asWidget()
                .pos(8, 8));

        panel.child(
            IKey.dynamic(
                () -> schematic != null
                    ? EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("galaxia.gui.rocket_trophy.schematic_loaded")
                        + EnumChatFormatting.RESET
                    : EnumChatFormatting.RED
                        + StatCollector.translateToLocal("galaxia.gui.rocket_trophy.schematic_none")
                        + EnumChatFormatting.RESET)
                .asWidget()
                .pos(8, 22));
        // Offset label
        panel.child(
            IKey.str(StatCollector.translateToLocal("galaxia.gui.rocket_trophy.position"))
                .asWidget()
                .pos(8, 35));

        // X / Y / Z fields in a row

        Flow labelRowTop = Flow.row()
            .coverChildren()
            .pos(8, 45)
            .padding(2);

        Flow controlRowTop = Flow.row()
            .coverChildren()
            .pos(8, 57)
            .padding(2);

        Flow labelRowBot = Flow.row()
            .coverChildren()
            .pos(8, 90)
            .padding(2);

        Flow controlRowBot = Flow.row()
            .coverChildren()
            .pos(8, 102)
            .padding(2);

        String[] rowTopLabels = { "X", "Y", "Z" };
        String[] rowBotLabels = { StatCollector.translateToLocal("galaxia.gui.rocket_trophy.yaw"),
            StatCollector.translateToLocal("galaxia.gui.rocket_trophy.pitch"),
            StatCollector.translateToLocal("galaxia.gui.rocket_trophy.scale") };

        float[] rowTopSteps = { 0.5f, 0.5f, 0.5f };
        float[] rowBotSteps = { 5f, 5f, 0.1f };

        StringSyncValue[] syncsTop = new StringSyncValue[] {
            new StringSyncValue(() -> String.format("%.2f", offsetX), s -> setOffsetX(Float.parseFloat(s))),
            new StringSyncValue(() -> String.format("%.2f", offsetY), s -> setOffsetY(Float.parseFloat(s))),
            new StringSyncValue(() -> String.format("%.2f", offsetZ), s -> setOffsetZ(Float.parseFloat(s))), };

        StringSyncValue[] syncsBot = new StringSyncValue[] {
            new StringSyncValue(() -> String.format("%.2f", pitch), s -> setPitch(Float.parseFloat(s))),
            new StringSyncValue(() -> String.format("%.2f", yaw), s -> setYaw(Float.parseFloat(s))),
            new StringSyncValue(() -> String.format("%.2f", scale), s -> setScale(Float.parseFloat(s))), };

        for (int axis = 0; axis < 3; axis++) {
            final int a = axis;
            String label = rowTopLabels[a];
            labelRowTop.child(
                IKey.str(label)
                    .asWidget()
                    .marginLeft(15)
                    .size(45, 10));

            controlRowTop.child(
                new ButtonWidget<>().size(14, 18)
                    .overlay(
                        IKey.str("-")
                            .alignment(Alignment.Center))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                        if (md.mouseButton == 0 && !worldObj.isRemote) {
                            if (a == 0) setOffsetX(offsetX - rowTopSteps[a]);
                            else if (a == 1) setOffsetY(offsetY - rowTopSteps[a]);
                            else if (a == 2) setOffsetZ(offsetZ - rowTopSteps[a]);
                        }
                    })))
                .child(
                    new TextFieldWidget().size(28, 18)
                        .setNumbersDouble(val -> val)
                        .acceptsExpressions(false)
                        .value(syncsTop[a]))
                .child(
                    new ButtonWidget<>().size(14, 18)
                        .overlay(
                            IKey.str("+")
                                .alignment(Alignment.Center))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                            if (md.mouseButton == 0 && !worldObj.isRemote) {
                                if (a == 0) setOffsetX(offsetX + rowTopSteps[a]);
                                else if (a == 1) setOffsetY(offsetY + rowTopSteps[a]);
                                else setOffsetZ(offsetZ + rowTopSteps[a]);
                            }
                        })));
        }
        for (int axis = 0; axis < 3; axis++) {
            final int a = axis;
            String label = rowBotLabels[a];
            labelRowBot.child(
                IKey.str(label)
                    .asWidget()
                    .marginLeft(15)
                    .size(45, 10));

            controlRowBot.child(
                new ButtonWidget<>().size(14, 18)
                    .overlay(
                        IKey.str("-")
                            .alignment(Alignment.Center))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                        if (md.mouseButton == 0 && !worldObj.isRemote) {
                            if (a == 0) setPitch(pitch - rowBotSteps[a]);
                            else if (a == 1) setYaw(yaw - rowBotSteps[a]);
                            else if (a == 2) setScale(scale - rowBotSteps[a]);
                        }
                    })))
                .child(
                    new TextFieldWidget().size(28, 18)
                        .setNumbersDouble(val -> val)
                        .acceptsExpressions(false)
                        .value(syncsBot[a]))
                .child(
                    new ButtonWidget<>().size(14, 18)
                        .overlay(
                            IKey.str("+")
                                .alignment(Alignment.Center))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                            if (md.mouseButton == 0 && !worldObj.isRemote) {
                                if (a == 0) setPitch(pitch + rowBotSteps[a]);
                                else if (a == 1) setYaw(yaw + rowBotSteps[a]);
                                else setScale(scale + rowBotSteps[a]);
                            }
                        })));
        }

        panel.child(labelRowTop);
        panel.child(controlRowTop);
        panel.child(labelRowBot);
        panel.child(controlRowBot);

        return panel;
    }

    public ItemStack getSchematic() {
        return schematic;
    }

    public void setSchematic(ItemStack stack) {
        this.schematic = stack;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float x) {
        offsetX = x;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float y) {
        offsetY = y;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetZ(float z) {
        offsetZ = z;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float p) {
        pitch = p;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float y) {
        yaw = y;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float s) {
        scale = s;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (schematic == null)
            return AxisAlignedBB.getBoundingBox(xCoord - 5, yCoord - 5, zCoord - 5, xCoord + 5, yCoord + 5, zCoord + 5);
        RocketAssembly assembly = new RocketAssembly(ItemRocketSchematic.readModules(schematic));
        double width = assembly.getTotalWidth();
        double height = assembly.getTotalHeight();

        return AxisAlignedBB.getBoundingBox(
            xCoord - width,
            yCoord - height,
            zCoord - width,
            xCoord + width,
            yCoord + height,
            zCoord + width);

    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setFloat("offsetX", offsetX);
        tag.setFloat("offsetY", offsetY);
        tag.setFloat("offsetZ", offsetZ);
        tag.setFloat("pitch", pitch);
        tag.setFloat("yaw", yaw);
        tag.setFloat("scale", scale);

        if (schematic != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            schematic.writeToNBT(itemTag);
            tag.setTag("schematic", itemTag);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        offsetX = tag.getFloat("offsetX");
        offsetY = tag.getFloat("offsetY");
        offsetZ = tag.getFloat("offsetZ");
        pitch = tag.getFloat("pitch");
        yaw = tag.getFloat("yaw");
        scale = tag.getFloat("scale");

        if (tag.hasKey("schematic")) {
            schematic = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("schematic"));
        } else {
            schematic = null;
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
