package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.validators.IRocketValidator;
import com.gtnewhorizons.galaxia.rocketmodules.validators.ValidationResult;
import li.cil.oc.util.BlockPosition;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gtnewhorizons.galaxia.core.Galaxia.LOG;

public class TileEntityModuleAssembler extends TileEntity implements IGuiHolder<PosGuiData> {
    private HashMap<Integer, Integer> moduleMap = new HashMap<>();
    private boolean hasSilo = false;
    private TileEntitySilo silo;

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        silo = findSiloToLink();

        ModularPanel panel = new ModularPanel("galaxia:module_assembler").size(240, 160);
        if (silo == null) return panel.child(
            IKey.str("§Couldn't find Silo")
                .asWidget()
                .pos(10, 35));
        hasSilo = true;
        panel.child(
            IKey.str("§lModule Assembler")
                .asWidget()
                .pos(8, 8));

        Flow row = Flow.row()
            .coverChildren()
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row.child(createModuleButton(m));
        }
        panel.child(row);

        Flow row2 = Flow.row()
                .coverChildren()
                .pos(10, 100)
                .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row2.child(IKey.str(m.getName() + " : " + moduleMap.getOrDefault(m.getId(), 0)).asWidget().padding(4).size(40, 20));
        }
        panel.child(row2);


        panel.child(
            new ButtonWidget<>().size(220, 30)
                .pos(10, 120)
                .overlay(
                    IKey.str("§aSend to silo")
                        .alignment(Alignment.CENTER))
                .tooltipDynamic(t -> {
                    if (moduleMap.isEmpty()) {
                        t.addLine("§7Add some modules first");
                        return;
                    }
                })
                .syncHandler(
                    new InteractionSyncHandler().setOnMousePressed(
                        md -> {
                            silo.receiveModules(moduleMap);
                            silo.markDirty();
                            this.moduleMap.clear();
                            this.markDirty();
                            // Send to silo here
                        })));

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

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tag.setBoolean("hasSilo", hasSilo);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        hasSilo = tag.getBoolean("hasSilo");
    }

    @Override
    public Packet getDescriptionPacket() {
        return null;
    }

    @Override
    public void onDataPacket(NetworkManager networkManager, S35PacketUpdateTileEntity s35PacketUpdateTileEntity) {

    }

    public void addModule(int id) {
        moduleMap.put(id, moduleMap.getOrDefault(id, 0) + 1);
        markDirty();
        LOG.info("ID: " + id + " | Count: " + moduleMap.get(id));
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public TileEntitySilo findSiloToLink() {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    TileEntity te = worldObj.getTileEntity(
                        xCoord + dx,
                        yCoord + dy,
                        zCoord + dz);

                    if (te instanceof TileEntitySilo) {
                        return (TileEntitySilo) te;
                    }
                }
            }
        }
        return null;
    }
}
