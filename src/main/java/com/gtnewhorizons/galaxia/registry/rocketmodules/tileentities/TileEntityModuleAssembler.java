package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public class TileEntityModuleAssembler extends GalaxiaMultiblockBase<TileEntityModuleAssembler>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE {

    /** Ticks to simulate assembly work per part before injecting it into the gantry. */
    private static final int ASSEMBLY_TICKS = 100;

    private TileEntityGantryTerminal gantryTerminal;

    private record ProductionTask(RocketPartInstance part, TileEntitySilo targetSilo) {}

    private final Queue<ProductionTask> productionQueue = new ArrayDeque<>();
    private ProductionTask activeTask = null;
    private int assemblyTicksRemaining = 0;

    /** Available module stock: maps id - count. */
    private final Map<Integer, Integer> moduleStock = new HashMap<>();

    // Pending coords for lazy-load after world load
    private final List<int[]> pendingQueueSiloCoords = new ArrayList<>();
    private final List<RocketPartInstance> pendingQueueParts = new ArrayList<>();
    private int[] pendingActiveTaskSiloCoords = null;
    private RocketPartInstance pendingActiveTaskPart = null;
    private int pendingActiveTaskTicks = 0;

    private ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;
    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static final IStructureDefinition<TileEntityModuleAssembler> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityModuleAssembler>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            new String[][] { { "CCC", "CCC", "CCC" }, { "C C", "T T", "C C" }, { "C C", "C C", "C C" },
                { "C C", "C C", "C C" }, { "CCC", "C~C", "CCC" } })
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((assembler, te) -> {
            if (te instanceof TileEntityGantryTerminal terminal) {
                assembler.setGantryTerminal(terminal);
                terminal.connectAssembler(assembler);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
        .build();

    public TileEntityModuleAssembler() {
        super();
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) return;

        resolvePendingTaskCoords();

        if (activeTask != null) {
            assemblyTicksRemaining--;
            if (assemblyTicksRemaining <= 0) {
                GantryAPI.injectModule(activeTask.part(), this, activeTask.targetSilo(), false);
                activeTask = null;
                markDirty();
                tryStartNextTask();
            }
        } else {
            tryStartNextTask();
        }
    }

    /**
     * Scans the production queue for the first task whose part type is currently in stock,
     * consumes one unit, and begins assembly. Tasks whose parts are not yet in stock are
     * skipped — they remain in the queue and are retried on the next tick when new stock
     * arrives.
     */
    private void tryStartNextTask() {
        if (productionQueue.isEmpty() || activeTask != null) return;

        // copying the queue to avoid concurrent modification exceptions
        for (ProductionTask task : new ArrayList<>(productionQueue)) {
            if (hasInStock(
                task.part()
                    .def())) {
                productionQueue.remove(task);
                consumeFromStock(
                    task.part()
                        .def());
                activeTask = task;
                assemblyTicksRemaining = ASSEMBLY_TICKS;
                markDirty();
                return;
            }
        }
    }

    /**
     * After a world load the silo TileEntity references are not yet available,
     * so they are stored as coordinates and resolved here on the first server tick.
     */
    private void resolvePendingTaskCoords() {
        if (pendingActiveTaskSiloCoords != null && pendingActiveTaskPart != null) {
            TileEntity te = worldObj.getTileEntity(
                pendingActiveTaskSiloCoords[0],
                pendingActiveTaskSiloCoords[1],
                pendingActiveTaskSiloCoords[2]);
            if (te instanceof TileEntitySilo silo) {
                activeTask = new ProductionTask(pendingActiveTaskPart, silo);
                assemblyTicksRemaining = pendingActiveTaskTicks;
            }
            pendingActiveTaskSiloCoords = null;
            pendingActiveTaskPart = null;
        }

        if (!pendingQueueSiloCoords.isEmpty()) {
            for (int i = 0; i < pendingQueueSiloCoords.size(); i++) {
                int[] coords = pendingQueueSiloCoords.get(i);
                TileEntity te = worldObj.getTileEntity(coords[0], coords[1], coords[2]);
                if (te instanceof TileEntitySilo silo) {
                    productionQueue.offer(new ProductionTask(pendingQueueParts.get(i), silo));
                }
            }
            pendingQueueSiloCoords.clear();
            pendingQueueParts.clear();
        }
    }

    /**
     * Called by GantryAPI/TileEntitySilo to enqueue a single-part production job.
     */
    public void enqueueProduction(RocketPartInstance part, TileEntitySilo silo) {
        productionQueue.offer(new ProductionTask(part.copy(), silo));
        markDirty();
    }

    /**
     * Called by the gantry terminal when a returned module physically arrives at this assembler.
     * Adds the module back to stock so the queue can pick it up.
     */
    public void addPart(IRocketPartDef def, int x, int y, int z) {
        addToStock(def);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private void addToStock(IRocketPartDef def) {
        moduleStock.merge(def.id(), 1, Integer::sum);
    }

    private void consumeFromStock(IRocketPartDef def) {
        int count = moduleStock.getOrDefault(def.id(), 0);
        if (count <= 1) {
            moduleStock.remove(def.id());
        } else {
            moduleStock.put(def.id(), count - 1);
        }
    }

    private boolean hasInStock(IRocketPartDef def) {
        return moduleStock.getOrDefault(def.id(), 0) > 0;
    }

    public int getStockCount(IRocketPartDef def) {
        return moduleStock.getOrDefault(def.id(), 0);
    }

    public Map<Integer, Integer> getModuleStock() {
        return Collections.unmodifiableMap(moduleStock);
    }

    public boolean isAssembling() {
        return activeTask != null;
    }

    public int getAssemblyTicksRemaining() {
        return assemblyTicksRemaining;
    }

    public int getQueueSize() {
        return productionQueue.size();
    }

    @Override
    public IStructureDefinition<TileEntityModuleAssembler> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return 1;
    }

    @Override
    protected int getControllerOffsetY() {
        return 1;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 4;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get();
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        this.gantryTerminal = terminal;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return gantryTerminal;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) markStructureDirty();

        ModularPanel panel = new ModularPanel("galaxia:module_assembler");
        panel.size(400, 300);

        BooleanSyncValue validSync = new BooleanSyncValue(() -> structureValid, v -> {});
        syncManager.syncValue("assemblerStructureValid", validSync);

        panel.childIf(
            !validSync.getBoolValue(),
            () -> IKey
                .str(EnumChatFormatting.RED + StatCollector.translateToLocal("galaxia.gui.module_assembler.not_formed"))
                .asWidget()
                .pos(10, 35));

        Flow row = Flow.row()
            .coverChildren()
            .padding(4);
        for (IRocketPartDef def : RocketPartRegistry.instance()
            .getAll()) {
            row.child(createStockButton(def, syncManager));
        }
        panel.childIf(validSync.getBoolValue(), () -> row);

        return panel;
    }

    private ButtonWidget<?> createStockButton(IRocketPartDef def, PanelSyncManager syncManager) {
        IntSyncValue stockSync = new IntSyncValue(() -> getStockCount(def), v -> {});
        syncManager.syncValue("stock_" + def.id(), stockSync);

        return new ButtonWidget<>().size(100, 20)
            .overlay(
                IKey.dynamic(() -> def.name() + " (" + stockSync.getValue() + ")")
                    .alignment(Alignment.Center))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    addToStock(def);
                    markDirty();
                } else if (md.mouseButton == 1 && hasInStock(def)) {
                    consumeFromStock(def);
                    markDirty();
                }
            }));
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        NBTTagList stockList = new NBTTagList();
        for (Map.Entry<Integer, Integer> entry : moduleStock.entrySet()) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("id", entry.getKey());
            e.setInteger("count", entry.getValue());
            stockList.appendTag(e);
        }
        tag.setTag("moduleStock", stockList);

        NBTTagList queueList = new NBTTagList();
        for (ProductionTask task : productionQueue) {
            queueList.appendTag(serializeTask(task));
        }
        tag.setTag("queue", queueList);

        if (activeTask != null) {
            tag.setTag("activeTask", serializeTask(activeTask));
            tag.setInteger("assemblyTicks", assemblyTicksRemaining);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        moduleStock.clear();
        if (tag.hasKey("moduleStock")) {
            NBTTagList stockList = tag.getTagList("moduleStock", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < stockList.tagCount(); i++) {
                NBTTagCompound e = stockList.getCompoundTagAt(i);
                moduleStock.put(e.getInteger("id"), e.getInteger("count"));
            }
        }

        if (tag.hasKey("queue")) {
            NBTTagList queueList = tag.getTagList("queue", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < queueList.tagCount(); i++) {
                NBTTagCompound entry = queueList.getCompoundTagAt(i);
                RocketPartInstance part = RocketPartInstance
                    .deserialize(entry.getCompoundTag("part"), RocketPartRegistry.instance());
                if (part != null) {
                    pendingQueueParts.add(part);
                    pendingQueueSiloCoords.add(
                        new int[] { entry.getInteger("siloX"), entry.getInteger("siloY"), entry.getInteger("siloZ") });
                }
            }
        }

        if (tag.hasKey("activeTask")) {
            NBTTagCompound taskTag = tag.getCompoundTag("activeTask");
            pendingActiveTaskPart = RocketPartInstance
                .deserialize(taskTag.getCompoundTag("part"), RocketPartRegistry.instance());
            if (pendingActiveTaskPart != null) {
                pendingActiveTaskSiloCoords = new int[] { taskTag.getInteger("siloX"), taskTag.getInteger("siloY"),
                    taskTag.getInteger("siloZ") };
                pendingActiveTaskTicks = tag.getInteger("assemblyTicks");
            }
        }
    }

    private NBTTagCompound serializeTask(ProductionTask task) {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag(
            "part",
            task.part()
                .serialize());
        t.setInteger("siloX", task.targetSilo().xCoord);
        t.setInteger("siloY", task.targetSilo().yCoord);
        t.setInteger("siloZ", task.targetSilo().zCoord);
        return t;
    }

    @Override
    public ForgeDirection getPlacedFacing() {
        return currentFacing.getDirection();
    }

    @Override
    public void setPlacedFacing(ForgeDirection dir) { /* handled by multiblock */ }

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }

    public boolean checkValidGraph() {
        if (gantryTerminal == null) return false;
        return gantryTerminal.checkValidGraph();
    }
}
