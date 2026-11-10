package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaCasingTextures;
import com.gtnewhorizons.galaxia.compat.gt.IRocketHatchHost;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatchElement;
import com.gtnewhorizons.galaxia.compat.gt.RocketHatches;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.MachineTerminals;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui.ModuleAssemblerUI;

import gregtech.api.util.GTStructureUtility;
import lombok.Getter;

/**
 * Builds rocket modules from items in its input buses and sends them along the gantry to whoever ordered them.
 * Orders that are not in stock are crafted automatically, players can also queue crafts by hand to build up stock
 */
public class TileEntityModuleAssembler extends GalaxiaMultiblockBase<TileEntityModuleAssembler>
    implements IGuiHolder<PosGuiData>, IRocketHatchHost {

    /** Ticks between attempts to start a craft whose inputs are missing */
    private static final int MISSING_INPUT_RETRY = 20;

    /**
     * A module promised to a terminal, sent as soon as one is in stock
     *
     * @param part     The module with its position in the ordering rocket
     * @param terminal Position of the terminal it has to reach
     */
    private record Delivery(RocketPartInstance part, BlockPos terminal) {}

    public enum Status {
        IDLE,
        CRAFTING,
        MISSING_INPUTS
    }

    private final MachineTerminals terminals = new MachineTerminals();
    @Getter
    private final RocketHatches hatches = new RocketHatches();

    /** Available module stock: maps key - count */
    private final Map<String, Integer> moduleStock = new LinkedHashMap<>();
    /** Debug switch, every module counts as in stock and sending one costs nothing */
    private boolean infiniteStock = false;
    private final Deque<Delivery> deliveries = new ArrayDeque<>();
    private final Deque<String> craftQueue = new ArrayDeque<>();
    @Getter
    private String activeCraft = null;
    private int craftTicks = 0;
    private int craftDuration = 0;
    private int retryCooldown = 0;
    @Getter
    private Status status = Status.IDLE;

    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static final IStructureDefinition<TileEntityModuleAssembler> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityModuleAssembler>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            new String[][] { { "CCC", "CCC", "CCC" }, { "C C", "T T", "C C" }, { "C C", "C C", "C C" },
                { "C C", "C C", "C C" }, { "CCC", "C~C", "CCC" } })
        .addElement(
            'C',
            GTStructureUtility.<TileEntityModuleAssembler>buildHatchAdder()
                .anyOf(RocketHatchElement.InputBus)
                .casingIndex(GalaxiaCasingTextures.RUSTY_PANEL)
                .hint(1)
                .buildAndChain(StructureUtility.ofBlock(GalaxiaBlocksEnum.RUSTY_PANEL.get(), 0)))
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
        if (worldObj.isRemote || !structureValid) return;

        dispatchDeliveries();
        tickCrafting();
    }

    /**
     * Sends every order that is in stock and queues crafts for the ones that are not
     */
    private void dispatchDeliveries() {
        for (Iterator<Delivery> iterator = deliveries.iterator(); iterator.hasNext();) {
            Delivery delivery = iterator.next();
            IRocketPartDef def = delivery.part()
                .def();
            if (!(delivery.terminal()
                .getTE(worldObj) instanceof TileEntityGantryTerminal target)) {
                // The ordering silo is gone, nobody will collect this
                iterator.remove();
                markDirty();
                continue;
            }
            if (getStockCount(def) > 0 && GantryAPI.send(delivery.part(), terminals.reaching(target), target)) {
                consumeFromStock(def);
                iterator.remove();
                markDirty();
            }
        }

        for (Delivery delivery : deliveries) {
            IRocketPartDef def = delivery.part()
                .def();
            if (countPromised(def) > getStockCount(def) + countQueued(def)) {
                craftQueue.addLast(def.key());
                markDirty();
            }
        }
    }

    private void tickCrafting() {
        if (activeCraft != null) {
            status = Status.CRAFTING;
            if (++craftTicks >= craftDuration) {
                moduleStock.merge(activeCraft, 1, Integer::sum);
                activeCraft = null;
                craftTicks = 0;
                markDirty();
            }
            return;
        }

        if (craftQueue.isEmpty()) {
            status = Status.IDLE;
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }

        for (Iterator<String> iterator = craftQueue.iterator(); iterator.hasNext();) {
            IRocketPartDef def = RocketPartRegistry.instance()
                .getByKey(iterator.next());
            if (def == null) {
                iterator.remove();
                continue;
            }
            if (hasInputs(def.recipe())) {
                consumeInputs(def.recipe());
                iterator.remove();
                activeCraft = def.key();
                craftTicks = 0;
                craftDuration = Math.max(
                    1,
                    def.recipe()
                        .duration());
                status = Status.CRAFTING;
                markDirty();
                return;
            }
        }
        status = Status.MISSING_INPUTS;
        retryCooldown = MISSING_INPUT_RETRY;
    }

    /**
     * Checks the input buses for every ingredient of a recipe
     *
     * @param recipe The recipe to check
     *
     * @return Boolean : True => all inputs are present
     */
    public boolean hasInputs(ModuleRecipe recipe) {
        for (ModuleRecipe.Input input : recipe.inputs()) {
            if (countInput(input) < input.amount()) return false;
        }
        return true;
    }

    /** How many items in the input buses match an ingredient */
    public long countInput(ModuleRecipe.Input input) {
        return hatches.countItems(worldObj, input::matches);
    }

    private void consumeInputs(ModuleRecipe recipe) {
        for (ModuleRecipe.Input input : recipe.inputs()) {
            hatches.extractItems(worldObj, input::matches, input.amount());
        }
    }

    /**
     * Promises a module to a terminal. It is sent right away when in stock, otherwise crafted first
     *
     * @param part     The module to deliver
     * @param terminal The terminal that should receive it
     */
    public void requestDelivery(RocketPartInstance part, TileEntityGantryTerminal terminal) {
        deliveries.addLast(new Delivery(part, new BlockPos(terminal.xCoord, terminal.yCoord, terminal.zCoord)));
        markDirty();
    }

    /**
     * Drops every pending order for a terminal, crafts already started still finish into stock
     *
     * @param terminal The terminal whose orders are cancelled
     */
    public void cancelDeliveries(TileEntityGantryTerminal terminal) {
        BlockPos pos = new BlockPos(terminal.xCoord, terminal.yCoord, terminal.zCoord);
        if (deliveries.removeIf(
            d -> d.terminal()
                .equals(pos)))
            markDirty();
    }

    /**
     * Queues crafts by hand to build up stock
     *
     * @param def   The module to craft
     * @param count How many
     */
    public void queueCraft(IRocketPartDef def, int count) {
        for (int i = 0; i < count; i++) craftQueue.addLast(def.key());
        retryCooldown = 0;
        markDirty();
    }

    /** Removes the last queued craft of a module, if any */
    public void cancelCraft(IRocketPartDef def) {
        Iterator<String> iterator = craftQueue.descendingIterator();
        while (iterator.hasNext()) {
            if (iterator.next()
                .equals(def.key())) {
                iterator.remove();
                markDirty();
                return;
            }
        }
    }

    /**
     * Called by the gantry terminal when a returned module physically arrives at this assembler.
     * Adds the module back to stock so the queue can pick it up.
     */
    public void addPart(IRocketPartDef def) {
        moduleStock.merge(def.key(), 1, Integer::sum);
        markDirty();
    }

    /** Stock reported for every module while {@link #infiniteStock} is on */
    public static final int INFINITE_STOCK = 999;

    public boolean isInfiniteStock() {
        return infiniteStock;
    }

    /** Debug helper, switches infinite stock on or off */
    public void toggleInfiniteStock() {
        infiniteStock = !infiniteStock;
        markDirty();
    }

    /** Debug helper, adds or removes stock without any cost */
    public void debugAdjustStock(IRocketPartDef def, int delta) {
        if (delta > 0) moduleStock.merge(def.key(), delta, Integer::sum);
        else for (int i = 0; i < -delta; i++) consumeFromStock(def);
        markDirty();
    }

    private void consumeFromStock(IRocketPartDef def) {
        if (infiniteStock) return;
        int count = moduleStock.getOrDefault(def.key(), 0);
        if (count <= 1) {
            moduleStock.remove(def.key());
        } else {
            moduleStock.put(def.key(), count - 1);
        }
    }

    public int getStockCount(IRocketPartDef def) {
        return infiniteStock ? INFINITE_STOCK : moduleStock.getOrDefault(def.key(), 0);
    }

    public Map<String, Integer> getModuleStock() {
        return Collections.unmodifiableMap(moduleStock);
    }

    /** Pending orders of a module type */
    public int countPromised(IRocketPartDef def) {
        int count = 0;
        for (Delivery delivery : deliveries) {
            if (delivery.part()
                .def() == def) count++;
        }
        return count;
    }

    /** Queued and running crafts of a module type */
    public int countQueued(IRocketPartDef def) {
        int count = def.key()
            .equals(activeCraft) ? 1 : 0;
        for (String key : craftQueue) {
            if (key.equals(def.key())) count++;
        }
        return count;
    }

    public boolean isAssembling() {
        return activeCraft != null;
    }

    /** Progress of the running craft from 0 to 1 */
    public float getCraftProgress() {
        return activeCraft == null || craftDuration <= 0 ? 0 : (float) craftTicks / craftDuration;
    }

    /** Pending orders plus queued crafts */
    public int getQueueSize() {
        return deliveries.size() + craftQueue.size();
    }

    public int getDeliveryCount() {
        return deliveries.size();
    }

    public int getCraftQueueSize() {
        return craftQueue.size();
    }

    @Override
    protected boolean checkStructure() {
        if (worldObj != null && !worldObj.isRemote) {
            hatches.clear();
            terminals.clear();
        }
        return super.checkStructure();
    }

    /** The terminal the gantry rails are attached to, null when the assembler has none */
    public TileEntityGantryTerminal getGantryTerminal() {
        return terminals.best();
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
        terminals.add(terminal);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) markStructureDirty();
        return ModuleAssemblerUI.build(this, data, syncManager, settings);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        NBTTagList stockList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : moduleStock.entrySet()) {
            NBTTagCompound e = new NBTTagCompound();
            e.setString("key", entry.getKey());
            e.setInteger("count", entry.getValue());
            stockList.appendTag(e);
        }
        tag.setTag("moduleStock", stockList);
        tag.setBoolean("infiniteStock", infiniteStock);

        NBTTagList deliveryList = new NBTTagList();
        for (Delivery delivery : deliveries) {
            NBTTagCompound d = new NBTTagCompound();
            d.setTag(
                "part",
                delivery.part()
                    .serialize());
            d.setTag(
                "terminal",
                delivery.terminal()
                    .toNBT());
            deliveryList.appendTag(d);
        }
        tag.setTag("deliveries", deliveryList);

        NBTTagList craftList = new NBTTagList();
        for (String key : craftQueue) craftList.appendTag(new NBTTagString(key));
        tag.setTag("craftQueue", craftList);

        if (activeCraft != null) {
            tag.setString("activeCraft", activeCraft);
            tag.setInteger("craftTicks", craftTicks);
            tag.setInteger("craftDuration", craftDuration);
        }
        tag.setInteger("status", status.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        RocketPartRegistry registry = RocketPartRegistry.instance();

        moduleStock.clear();
        infiniteStock = tag.getBoolean("infiniteStock");
        NBTTagList stockList = tag.getTagList("moduleStock", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stockList.tagCount(); i++) {
            NBTTagCompound e = stockList.getCompoundTagAt(i);
            IRocketPartDef def = registry.getByKey(e.getString("key"));
            if (def != null) moduleStock.merge(def.key(), e.getInteger("count"), Integer::sum);
        }

        deliveries.clear();
        NBTTagList deliveryList = tag.getTagList("deliveries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < deliveryList.tagCount(); i++) {
            NBTTagCompound d = deliveryList.getCompoundTagAt(i);
            RocketPartInstance part = RocketPartInstance.deserialize(d.getCompoundTag("part"), registry);
            if (part != null) deliveries.addLast(new Delivery(part, BlockPos.fromNBT(d.getCompoundTag("terminal"))));
        }

        craftQueue.clear();
        NBTTagList craftList = tag.getTagList("craftQueue", Constants.NBT.TAG_STRING);
        for (int i = 0; i < craftList.tagCount(); i++) craftQueue.addLast(craftList.getStringTagAt(i));

        activeCraft = tag.hasKey("activeCraft") ? tag.getString("activeCraft") : null;
        craftTicks = tag.getInteger("craftTicks");
        craftDuration = tag.getInteger("craftDuration");
        int statusOrdinal = tag.getInteger("status");
        status = statusOrdinal >= 0 && statusOrdinal < Status.values().length ? Status.values()[statusOrdinal]
            : Status.IDLE;
    }
}
