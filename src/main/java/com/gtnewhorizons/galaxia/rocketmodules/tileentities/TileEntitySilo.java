package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.core.network.MonorailAnimPacket;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.rocketmodules.client.render.MonorailAnimationState;
import com.gtnewhorizons.galaxia.rocketmodules.link.ILinkable;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.CapsuleRequiredValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.EngineToTankRatioValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.IRocketValidator;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.ValidationResult;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.validators.WeightLimitValidator;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntitySilo extends TileEntity implements IGuiHolder<PosGuiData>, ILinkable {

    private EntityRocket entityRocket;
    private RocketAssembly assembly;
    // Modules currently in the rendering stack
    private final List<Integer> modules = new ArrayList<>();
    public boolean shouldRender = true;
    // Validation rules for rocket systems
    private final List<IRocketValidator> validators = Arrays
        .asList(new CapsuleRequiredValidator(), new EngineToTankRatioValidator(), new WeightLimitValidator());
    private int destination = 0;
    private final IntValue.Dynamic selectedDim = new IntValue.Dynamic(() -> destination, v -> {
        destination = v;
        GALAXIA_NETWORK.sendToServer(new DestinationSetPacket(xCoord, yCoord, zCoord, v));
    });

    /**
     * Stored position of the linked master (ModuleAssembler).
     * Persisted in NBT so the link survives chunk unload / world restart.
     * Null = not linked.
     */
    private ChunkCoordinates masterPos = null;

    private double monorailYOffset = 3.0;

    @SideOnly(Side.CLIENT)
    private MonorailAnimationState animationState;

    @SideOnly(Side.CLIENT)
    public MonorailAnimationState getAnimationState() {
        if (animationState == null) animationState = new MonorailAnimationState();
        return animationState;
    }

    @Override
    public String getLinkableName() {
        return "Rocket Silo";
    }

    @Override
    public boolean canBeSlave() {
        return true;
    }

    @Override
    public void setMasterPos(ChunkCoordinates pos) {
        this.masterPos = pos;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public ChunkCoordinates getMasterPos() {
        return masterPos;
    }

    public double getMonorailYOffset() {
        return monorailYOffset;
    }

    public void setMonorailYOffset(double offset) {
        this.monorailYOffset = offset;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * resolves and casts master to TileEntityModuleAssembler.
     *
     * @return Linked assembler, or null if not linked / unloaded.
     */
    public TileEntityModuleAssembler getLinkedAssembler() {
        // Walk the chain upstream until we reach the MA
        ChunkCoordinates cursor = masterPos;
        int safety = 64;
        while (cursor != null && safety-- > 0) {
            TileEntity te = worldObj.getTileEntity(cursor.posX, cursor.posY, cursor.posZ);
            if (te instanceof TileEntityModuleAssembler ma) return ma;
            if (te instanceof TileEntityMonorailPole pole) cursor = pole.getPrevPos();
            else return null;
        }
        return null;
    }

    /**
     * The UI builder for the tile entity
     *
     * @param data        information about the creation context
     * @param syncManager sync handler where widget sync handlers should be
     *                    registered
     * @param settings    settings which apply to the whole ui and not just this
     *                    panel
     * @return The ModularPanel to display as UI
     */
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_silo_main")
            .size(240, 160);

        TileEntityModuleAssembler ma = getLinkedAssembler();
        if (ma == null) {
            return panel.child(
                IKey.str("§cNo Module Assembler linked.\nUse the Linking Tool to connect one.")
                    .asWidget()
                    .pos(10, 35));
        }

        panel.child(
            new PageButton(0, tabController).size(120, 28)
                .pos(0, -28)
                .overlay(IKey.str("Build Rocket")))
            .child(
                new PageButton(1, tabController).size(120, 28)
                    .pos(120, -28)
                    .overlay(IKey.str("Launch Rocket")));

        // Title
        panel.child(
            IKey.str("§lRocket Silo")
                .asWidget()
                .pos(8, 8));
        // Module addition buttons
        Flow moduleRow = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);
        for (RocketModule m : ModuleRegistry.getAll()) {
            moduleRow.child(createModuleButton(m, ma));
        }

        Flow destRow = Flow.row()
            .coverChildren()
            .pos(10, 35)
            .padding(4);

        for (BasePlanet dim : SolarSystemRegistry.getAllPlanets()) {
            destRow.child(createDestinationButton(dim));
        }

        // Builder Page
        panel.child(
            new PagedWidget<>().controller(tabController)
                .addPage(
                    new ParentWidget<>().size(240, 160)
                        .child(moduleRow)
                        .child(
                            new ButtonWidget<>().size(220, 30)
                                .pos(10, 80)
                                .overlay(
                                    IKey.str("Return Modules")
                                        .alignment(Alignment.Center))
                                .tooltip(t -> t.addLine("Disassemble Rocket"))
                                .syncHandler(
                                    new InteractionSyncHandler().setOnMousePressed(
                                        md -> { if (md.mouseButton == 0 && !worldObj.isRemote) returnModules(ma); }))))
                // Launch Page
                .addPage(
                    new ParentWidget<>().size(240, 160)
                        .child(destRow)
                        .child(
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
                                        md -> {
                                            if (md.mouseButton == 0 && !worldObj.isRemote) enterRocket(data);
                                        })))));

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
            .syncHandler(
                new InteractionSyncHandler().setOnMousePressed(
                    md -> { if (md.mouseButton == 0 && hasRemaining(m.getId(), ma)) addModule(m.getId(), ma); }));
    }

    private ToggleButton createDestinationButton(BasePlanet dim) {
        return new ToggleButton().size(48, 20)
            .overlay(
                IKey.str(
                    dim.getPlanetEnum()
                        .getName()))
            .valueWrapped(
                selectedDim,
                dim.getPlanetEnum()
                    .getId());
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
        rocket.setDesination(destination);
        data.getPlayer()
            .mountEntity(rocket);
        if (!rocket.shouldRender()) rocket.launch();
    }

    /**
     * Adds a module to the render stack and eventual entity, and removes from
     * associated Assembler map
     *
     * @param id The module ID to add
     * @param ma The linked Module Assembler
     */
    public void addModule(int id, TileEntityModuleAssembler ma) {
        modules.add(id);
        ma.moduleMap.put(id, ma.moduleMap.get(id) - 1);
        assembly = null;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            if (!worldObj.isRemote) {
                GALAXIA_NETWORK.sendToAllAround(
                    new MonorailAnimPacket(xCoord, yCoord, zCoord, id, MonorailAnimPacket.DIR_TO_SILO),
                    new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 512));
            }
        }
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

    public void setDesination(int dim) {
        this.destination = dim;
    }

    /**
     * Returns the modules back to the linked assembler
     * Sends a Silo->MA animation packet to clients for each returned module
     *
     * @param ma The Module Assembler tile entity
     */
    public void returnModules(TileEntityModuleAssembler ma) {
        if (!worldObj.isRemote) {
            // Send animation packets BEFORE clearing the list so we still have IDs.
            for (Integer id : modules) {
                GALAXIA_NETWORK.sendToAllAround(
                    new MonorailAnimPacket(xCoord, yCoord, zCoord, id, MonorailAnimPacket.DIR_TO_MA),
                    new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(
                        worldObj.provider.dimensionId,
                        xCoord,
                        yCoord,
                        zCoord,
                        512));
            }
        }

        for (Integer id : modules) {
            ma.moduleMap.put(id, ma.moduleMap.getOrDefault(id, 0) + 1);
        }
        assembly = null;
        modules.clear();
        markDirty();
        ma.markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(ma.xCoord, ma.yCoord, ma.zCoord);
        }
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
        } else {
            if (masterPos != null) {
                getAnimationState().tick(calcPathLength());
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private float calcPathLength() {
        float total = 0f;
        int prevX = xCoord, prevY = yCoord, prevZ = zCoord;
        ChunkCoordinates cursor = masterPos;
        int safety = 64;
        while (cursor != null && safety-- > 0) {
            float dx = cursor.posX - prevX;
            float dy = cursor.posY - prevY;
            float dz = cursor.posZ - prevZ;
            total += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            prevX = cursor.posX;
            prevY = cursor.posY;
            prevZ = cursor.posZ;
            TileEntity te = worldObj.getTileEntity(cursor.posX, cursor.posY, cursor.posZ);
            if (te instanceof TileEntityModuleAssembler) break;
            else if (te instanceof TileEntityMonorailPole pole) cursor = pole.getPrevPos();
            else break;
        }
        return total;
    }

    /**
     * Invalidation method based on entity state
     */
    @Override
    public void invalidate() {
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
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

        // modules list
        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("type", type);
            list.appendTag(entry);
        }
        nbt.setTag("modules", list);

        // link data - always write so readFromNBT can detect absence
        nbt.setBoolean("hasLink", masterPos != null);
        if (masterPos != null) {
            nbt.setInteger("masterX", masterPos.posX);
            nbt.setInteger("masterY", masterPos.posY);
            nbt.setInteger("masterZ", masterPos.posZ);
        }

        nbt.setDouble("monorailYOffset", monorailYOffset);
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
            modules.add(
                list.getCompoundTagAt(i)
                    .getInteger("type"));
        }
        assembly = null;

        // link data
        if (nbt.getBoolean("hasLink")) {
            masterPos = new ChunkCoordinates(
                nbt.getInteger("masterX"),
                nbt.getInteger("masterY"),
                nbt.getInteger("masterZ"));
        } else {
            masterPos = null;
        }

        monorailYOffset = nbt.hasKey("monorailYOffset") ? nbt.getDouble("monorailYOffset") : 3.0;
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
}
