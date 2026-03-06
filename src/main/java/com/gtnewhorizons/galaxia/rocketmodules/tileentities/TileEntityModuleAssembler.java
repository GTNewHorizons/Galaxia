package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.rocketmodules.link.ILinkable;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class TileEntityModuleAssembler extends TileEntity implements IGuiHolder<PosGuiData>, ILinkable {

    // Hashmap stores <Module ID, Count>
    public HashMap<Integer, Integer> moduleMap = new HashMap<>();

    @Override
    public String getLinkableName() {
        return "Module Assembler";
    }

    @Override
    public boolean canBeMaster() {
        return true;
    }

    /**
     * Override to add custom master-side behaviour
     */
    @Override
    public void onSlaveLinked(TileEntity slave, EntityPlayer player) {
        // Currently just a hook
    }

    @Override
    public void setMasterPos(ChunkCoordinates pos) { /* master-only TE */ }

    @Override
    public ChunkCoordinates getMasterPos() {
        return null;
    }

    /**
     * The UI builder for the Tile Entity GUI
     *
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be
     *                    registered
     * @param settings    settings which apply to the whole ui and not just this
     *                    panel
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("galaxia:module_assembler").size(240, 160);

        // Title
        panel.child(
            IKey.str("§lModule Assembler")
                .asWidget()
                .pos(8, 8));

        // Adding module buttons
        Flow row = Flow.row()
            .coverChildren()
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row.child(createModuleButton(m));
        }
        panel.child(row);

        // Module storage counters
        Flow row2 = Flow.row()
            .coverChildren()
            .pos(10, 70)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            Supplier<String> stringSupplier = () -> m.getName() + " : " + moduleMap.getOrDefault(m.getId(), 0);
            row2.child(
                IKey.dynamic(stringSupplier)
                    .asWidget()
                    .padding(4)
                    .size(40, 20));
        }
        panel.child(row2);
        return panel;
    }

    /**
     * Creates a button to add a new module
     *
     * @param m The rocket module this button handles
     * @return The ButtonWidget needed in the main panel
     */
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
        moduleMap.clear();
        NBTTagCompound mapNbt = tag.getCompoundTag("moduleMap");
        for (String key : mapNbt.func_150296_c()) {
            moduleMap.put(Integer.parseInt(key), mapNbt.getInteger(key));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound mapNbt = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> e : moduleMap.entrySet()) {
            mapNbt.setInteger(
                e.getKey()
                    .toString(),
                e.getValue());
        }
        tag.setTag("moduleMap", mapNbt);
    }

    /**
     * Writes an NBT packet to the server
     *
     * @return Packet holding the nbt update
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    /**
     * Receives a data packet and updates NBT data
     *
     * @param net    The network manager of the server
     * @param packet The incoming packet from the server
     */
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    /**
     * Adds a new module to the internal storage
     *
     * @param id The ID of the module being added
     */
    public void addModule(int id) {
        moduleMap.put(id, moduleMap.getOrDefault(id, 0) + 1);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

}
