package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.validators.CapsuleRequiredValidator;
import com.gtnewhorizons.galaxia.rocketmodules.validators.EngineToTankRatioValidator;
import com.gtnewhorizons.galaxia.rocketmodules.validators.IRocketValidator;
import com.gtnewhorizons.galaxia.rocketmodules.validators.ValidationResult;
import com.gtnewhorizons.galaxia.rocketmodules.validators.WeightLimitValidator;

public class TileEntitySilo extends TileEntity implements IGuiHolder<PosGuiData> {

    private EntityRocket entityRocket;
    private RocketAssembly assembly;
    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;

    private final List<IRocketValidator> validators = Arrays
        .asList(new CapsuleRequiredValidator(), new EngineToTankRatioValidator(), new WeightLimitValidator());

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("galaxia:rocket_silo").size(240, 160);
        panel.child(
            IKey.str("§lRocket Silo")
                .asWidget()
                .pos(8, 8));

        Flow row = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row.child(createModuleButton(m));
        }
        panel.child(row);

        panel.child(
            new ButtonWidget<>().size(220, 30)
                .pos(10, 120)
                .overlay(
                    IKey.str("§aEnter Rocket")
                        .alignment(Alignment.CENTER))
                .tooltipDynamic(t -> {
                    if (getAssembly().getModules()
                        .isEmpty()) {
                        t.addLine("§7Add some modules first");
                        return;
                    }
                    for (IRocketValidator v : validators) {
                        ValidationResult r = v.validate(getAssembly());
                        if (!r.valid()) t.addLine("§c" + r.message());
                    }
                })
                .syncHandler(
                    new InteractionSyncHandler().setOnMousePressed(
                        md -> { if (md.mouseButton == 0 && !worldObj.isRemote) enterRocket(data); })));

        return panel;
    }

    private ButtonWidget<?> createModuleButton(RocketModule m) {
        return new ButtonWidget<>().size(48, 20)
            .overlay(IKey.str(m.getName()))
            .tooltip(t -> t.add("§7" + String.format("%.1fm | %.0fkg", m.getHeight(), m.getWeight())))
            .syncHandler(
                new InteractionSyncHandler()
                    .setOnMousePressed(md -> { if (md.mouseButton == 0) addModule(m.getId()); }));
    }

    private void enterRocket(PosGuiData data) {
        if (!getAssembly().getModules()
            .stream()
            .anyMatch(m -> m.getPassengerCapacity() > 0)) return;
        EntityRocket rocket = getEntityRocket();
        if (rocket == null || rocket.isDead) return;
        rocket.setCapsuleIndex(getFirstCapsuleIndex());
        data.getPlayer()
            .mountEntity(rocket);
        if (!rocket.shouldRender()) rocket.launch();
    }

    public void addModule(int id) {
        modules.add(id);
        assembly = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public RocketAssembly getAssembly() {
        if (assembly == null) assembly = new RocketAssembly(getModules());
        return assembly;
    }

    public int getFirstCapsuleIndex() {
        List<RocketModule> list = getAssembly().getModules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i)
                .getPassengerCapacity() > 0) return i;
        }
        return -1;
    }

    public void openUI(EntityPlayer player) {
        GuiFactories.tileEntity()
            .open(player, xCoord, yCoord, zCoord);
    }

    public void launch() {
        modules.clear();
        shouldRender = true;
        entityRocket = null;
        assembly = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (shouldRender && (entityRocket == null || entityRocket.isDead)) {
                spawnSeat();
            }
        }
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
        assembly = null;
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
