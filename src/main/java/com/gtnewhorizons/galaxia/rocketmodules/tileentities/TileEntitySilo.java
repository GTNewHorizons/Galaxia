package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

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
    // Modules currently in the rendering stack
    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;
    // Validation rules for rocket systems
    private final List<IRocketValidator> validators = Arrays
        .asList(new CapsuleRequiredValidator(), new EngineToTankRatioValidator(), new WeightLimitValidator());

    /**
     * The UI builder for the tile entity
     * 
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be registered
     * @param settings    settings which apply to the whole ui and not just this panel
     * @return The ModularPanel to display as UI
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("galaxia:rocket_silo").size(240, 160);
        TileEntityModuleAssembler ma = findAssemblerToLink();
        // If no linked MA,
        if (ma == null) return panel.child(

            IKey.str("§Couldn't find Assembler")
                .asWidget()
                .pos(10, 35));
        // Title
        panel.child(
            IKey.str("§lRocket Silo")
                .asWidget()
                .pos(8, 8));
        // Module addition buttons
        Flow row = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            row.child(createModuleButton(m, ma));
        }
        panel.child(row);

        // Assembler count indicators
        Flow row2 = Flow.row()
            .coverChildren()
            .pos(10, 70)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            Supplier<String> stringSupplier = () -> m.getName() + " : " + ma.moduleMap.getOrDefault(m.getId(), 0);
            row2.child(
                IKey.dynamic(stringSupplier)
                    .asWidget()
                    .padding(4)
                    .size(40, 20));
        }
        panel.child(row2);

        // Build Rocket and enter button
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

    /**
     * Creates the button for adding a module
     * 
     * @param m  The Rocket module this button is responsible for
     * @param ma The Module Assembler this is linked to
     * @return ButtonWidget to add to the panel
     */
    private ButtonWidget<?> createModuleButton(RocketModule m, TileEntityModuleAssembler ma) {
        return new ButtonWidget<>().size(48, 20)
            .overlay(IKey.str(m.getName()))
            .tooltip(t -> t.add("§7" + String.format("%.1fm | %.0fkg", m.getHeight(), m.getWeight())))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    if (hasRemaining(m.getId(), ma)) {
                        addModule(m.getId(), ma);
                    }

                }
            }));
    }

    /**
     * Enters the rocket and starts launch cycle (cycle = GO currently)
     * 
     * @param data The data from the GUI
     */
    private void enterRocket(PosGuiData data) {
        if (getAssembly().getModules()
            .stream()
            .noneMatch(m -> m.getPassengerCapacity() > 0)) return;
        EntityRocket rocket = getEntityRocket();
        if (rocket == null || rocket.isDead) return;
        rocket.setCapsuleIndex(getFirstCapsuleIndex());
        data.getPlayer()
            .mountEntity(rocket);
        if (!rocket.shouldRender()) rocket.launch();
    }

    /**
     * Adds a module to the render stack and eventual entity, and removes 1 from associated Assembler map
     * 
     * @param id The module ID to add
     * @param ma The linked Module Assembler
     */
    public void addModule(int id, TileEntityModuleAssembler ma) {
        modules.add(id);
        ma.moduleMap.put(id, ma.moduleMap.get(id) - 1);
        assembly = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * Checks to see if the linked assembler has the module requested
     * 
     * @param id The ID of the module to check
     * @param ma The linked assembler to check from
     * @return Boolean : True -> has the module
     */
    public boolean hasRemaining(int id, TileEntityModuleAssembler ma) {
        return ma.moduleMap.getOrDefault(id, 0) > 0;
    }

    /**
     * Gets the RocketAssmebly for this silo or creates a new one
     * 
     * @return RocketAssembly
     */
    public RocketAssembly getAssembly() {
        if (assembly == null) assembly = new RocketAssembly(getModules());
        return assembly;
    }

    /**
     * Gets the first capsule index from the modules list
     * 
     * @return The index of the first capsule
     */
    public int getFirstCapsuleIndex() {
        List<RocketModule> list = getAssembly().getModules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i)
                .getPassengerCapacity() > 0) return i;
        }
        return -1;
    }

    /**
     * Starts the launch sequence and updates states
     */
    public void launch() {
        modules.clear();
        shouldRender = true;
        entityRocket = null;
        assembly = null;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * Spawns the rocket (used to switch from being a TE to an actual entity)
     */
    private void spawnRocket() {
        entityRocket = new EntityRocket(worldObj);
        entityRocket.bindSilo(this);
        entityRocket.setPosition(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5);
        worldObj.spawnEntityInWorld(entityRocket);
    }

    /**
     * Getter for the rocket entity
     * 
     * @return Rocket entity
     */
    public EntityRocket getEntityRocket() {
        return entityRocket;
    }

    /**
     * Gets all modules in the current stack
     * 
     * @return ArrayList of modules
     */
    public ArrayList<Integer> getModules() {
        return new ArrayList<>(modules);
    }

    /**
     * Gets the number of modules in the stack
     * 
     * @return Number of modules in stack
     */
    public int getNumModules() {
        return modules.size();
    }

    /**
     * Updates the entity once conditions met
     */
    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            if (shouldRender && (entityRocket == null || entityRocket.isDead)) {
                spawnRocket();
            }
        }
    }

    /**
     * Invalidation method based on entity state
     */
    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) {
            entityRocket.setDead();
        }
    }

    /**
     * Returns rendering bounding box
     * 
     * @return Bounding box
     */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    /**
     * Gets the max render distance squared
     * 
     * @return Max RDS
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    /**
     * Writes TE data to NBT taq
     * 
     * @param nbt Tag to write to NBT
     */
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

    /**
     * Reads from NBT tag and updates TE state
     * 
     * @param nbt Tag to read from
     */
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

    /**
     * Description packet method used for server side syncing
     * 
     * @return The update packet
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    /**
     * Receiver for the packet
     * 
     * @param net The NetworkManager the packet came from
     * @param pkt The packet
     */
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }

    /**
     * Finds a module assembler within a 10x10 plane on same height
     * 
     * @return ModuleAssembler TE if found, else null
     */
    public TileEntityModuleAssembler findAssemblerToLink() {
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                TileEntity te = worldObj.getTileEntity(xCoord + dx, yCoord, zCoord + dz);

                if (te instanceof TileEntityModuleAssembler) {
                    return (TileEntityModuleAssembler) te;
                }
            }
        }
        return null;
    }
}
