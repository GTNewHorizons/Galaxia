package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

public class TileEntitySilo extends TileEntity implements IGuiHolder<PosGuiData> {

    private EntityRocket entityRocket;

    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new ModularPanel("galaxia:rocket_silo").size(210, 130)
            .child(
                IKey.str("§lRocket Silo")
                    .asWidget()
                    .pos(8, 8))
            .child(
                Flow.row()
                    .coverChildren()
                    .child(createModuleButton(0, "Fuel Tank"))
                    .child(createModuleButton(1, "Capsule"))
                    .child(createModuleButton(2, "Storage Unit"))
                    .child(createModuleButton(3, "Engine"))
                    .pos(10, 35))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(
                        IKey.str("§aEnter Rocket")
                            .alignment(Alignment.CENTER))
                    .tooltip(t -> {
                        if (!hasCapsule()) {
                            t.add("§cRequires Capsule module");
                        } else if (!hasCorrectEngines()) {
                            t.add("§cRequires 1 engine per tank stack");
                        } else {
                            t.add("Sit in the capsule and launch the rocket");
                        }
                    })
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        enterRocket(data);
                    })));
    }

    private void enterRocket(PosGuiData data) {
        if (!hasCapsule()) return;
        EntityRocket rocket = getEntityRocket();
        if (rocket == null || rocket.isDead) return;
        EntityPlayer player = data.getPlayer();
        rocket.setCapsuleIndex(getFirstCapsuleIndex());
        player.mountEntity(rocket);
        if (!rocket.shouldRender()) {
            rocket.launch();
        }
    }

    private ButtonWidget<?> createModuleButton(int id, String name) {
        ModuleRegistry.ModuleInfo info = ModuleRegistry.getModule(id);
        String heightStr = info != null ? String.format("%.1fm", info.height()) : "??m";

        return new ButtonWidget<>().syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
            if (mouseData.mouseButton == 0) {
                addModule(id);
            }
        }))
            .size(48, 20)
            .overlay(IKey.str(name))
            .tooltip((t) -> t.add("Add " + name + " (" + heightStr + ")"));
    }

    public void openUI(EntityPlayer player) {
        GuiFactories.tileEntity()
            .open(player, xCoord, yCoord, zCoord);
    }

    public boolean hasCapsule() {
        return modules.contains(1);
    }

    public boolean hasCorrectEngines() {
        return ((double) getTankCount() / getEngineCount()) - (Math.floor((double) getTankCount() / getEngineCount()))
            == 0;
    }

    public int getFirstCapsuleIndex() {
        return modules.indexOf(1);
    }

    public int getEngineCount() {
        return Collections.frequency(modules, 3);
    }

    public int getTankCount() {
        return Collections.frequency(modules, 0);
    }

    public void addModule(int type) {
        modules.add(type);
        markDirty();
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (shouldRender && (entityRocket == null || entityRocket.isDead)) {
                spawnSeat();
            }
        }
    }

    public void launch() {
        modules.clear();
        shouldRender = true;
        entityRocket = null;

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private void spawnSeat() {
        entityRocket = new EntityRocket(worldObj);
        entityRocket.bindSilo(this);
        entityRocket.setPosition(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
    }

    public EntityRocket getEntityRocket() {
        return entityRocket;
    }

    public List<Integer> getModules() {
        return new ArrayList<>(modules);
    }

    public int getNumModules() {
        return modules.size();
    }

    public int getModuleType(int index) {
        return index >= 0 && index < modules.size() ? modules.get(index) : 0;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) {
            entityRocket.setDead();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("shouldRender", shouldRender);

        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("type", type);
            list.appendTag(entry);
        }
        nbt.setTag("modules", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        shouldRender = nbt.getBoolean("shouldRender");

        modules.clear();
        NBTTagList list = nbt.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            modules.add(entry.getInteger("type"));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
