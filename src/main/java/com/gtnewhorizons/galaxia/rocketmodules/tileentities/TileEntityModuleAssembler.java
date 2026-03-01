package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.HashMap;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class TileEntityModuleAssembler extends TileEntity implements IGuiHolder<PosGuiData> {

    // Hashmap stores <Module ID, Count>
    public HashMap<Integer, Integer> moduleMap = new HashMap<>();

    /**
     * The UI builder for the Tile Entity GUI
     * 
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be registered
     * @param settings    settings which apply to the whole ui and not just this panel
     * @return
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
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    addModule(m.getId());

                }
            }));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {}

    @Override
    public void writeToNBT(NBTTagCompound tag) {}

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
