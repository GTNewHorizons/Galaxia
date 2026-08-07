package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.galaxia.registry.celestial.station.gui.AirlockGUI;

import lombok.Getter;

public class TileEntityAirlock extends GalaxiaMultiblockBase<TileEntityAirlock> implements IGuiHolder<PosGuiData> {

    public enum AirlockState {
        CLOSED,
        OPEN,
    }

    @Getter
    private AirlockState state = AirlockState.CLOSED;

    public static final int MAX_CONNECTIONS = 2;
    private List<BlockPos> stationControllers = new ArrayList<>(MAX_CONNECTIONS);

    public static final int MIN_CHECK_INTERVAL = 5;
    public static final int MAX_CHECK_INTERVAL = 200;
    public static final int DEFAULT_CHECK_INTERVAL = 10;
    public static final int MIN_CLOSE_DELAY = 0;
    public static final int MAX_CLOSE_DELAY = 400;
    public static final int DEFAULT_CLOSE_DELAY = 40;
    public static final int MIN_PROXIMITY_RANGE = 1;
    public static final int MAX_PROXIMITY_RANGE = 10;
    public static final int DEFAULT_PROXIMITY_RANGE = 3;

    @Getter
    private boolean proximityOpening = false;
    @Getter
    private boolean proximityAutoClose = true;
    @Getter
    private boolean redstoneControl = true;
    @Getter
    private boolean manualClick = true;
    @Getter
    private boolean autoSealOnLeak = true;
    @Getter
    private int checkInterval = DEFAULT_CHECK_INTERVAL;
    @Getter
    private int closeDelay = DEFAULT_CLOSE_DELAY;
    @Getter
    private int proximityRange = DEFAULT_PROXIMITY_RANGE;

    /**
     * Controller is now on the BOTTOM layer of the structure.
     */
    public static final int CONTROLLER_OFFSET_X = 0;
    public static final int CONTROLLER_OFFSET_Y = 2;
    public static final int CONTROLLER_OFFSET_Z = 0;

    public static final int MAXIMUM_RADIUS = 8;
    public static final int INVALID = -1;

    public static final String STRUCTURE_PIECE_MAIN = "main";
    public static final String STRUCTURE_EMBED = "embed";
    public static final String STRUCTURE_EDGE = "edge";
    public static final String STRUCTURE_CENTER = "center";

    private AxisAlignedBB doorwayAABB;
    private int proximityCheckTimer = 0;

    private boolean redstonePowered = false;

    private int xMin = INVALID;
    private int xMax = INVALID;
    private int yMin = INVALID;
    private int yMax = INVALID;

    public static final IStructureDefinition<TileEntityAirlock> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityAirlock>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
            StructureUtility.transpose(new String[][] {
                { "CCCCC" },
                { "CDDDC" },
                { "~DDDC" },
                { "CDDDC" },
                { "CCCCC" },
            }))
            // spotless:on
        .addShape(STRUCTURE_EMBED, new String[][] { { "~CE" } })
        .addShape(STRUCTURE_EDGE, new String[][] { { "C" } })
        .addShape(STRUCTURE_CENTER, new String[][] { { "D" } })
        .addElement('C', GalaxiaStructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 0))
        .addElement('E', GalaxiaStructureUtility.ofBlockAnyMeta(GalaxiaBlocksEnum.AIRLOCK_DOOR.get()))
        .addElement(
            'D',
            GalaxiaStructureUtility.ofBlockWithMeta(
                GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                (t, meta) -> BlockAirlockDoor.getOrientation(meta) == BlockAirlockDoor.orientationForAxis(
                    t.getCurrentFacing()
                        .getRelativeBackInWorld()),
                t -> BlockAirlockDoor.encodeMeta(
                    false,
                    BlockAirlockDoor.orientationForAxis(
                        t.getCurrentFacing()
                            .getRelativeBackInWorld()))))
        .build();

    @Override
    public IStructureDefinition<TileEntityAirlock> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return CONTROLLER_OFFSET_X;
    }

    @Override
    protected int getControllerOffsetY() {
        return CONTROLLER_OFFSET_Y;
    }

    @Override
    protected int getControllerOffsetZ() {
        return CONTROLLER_OFFSET_Z;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get();
    }

    public boolean isOpen() {
        return state == AirlockState.OPEN;
    }

    public boolean isExternalConnection() {
        return stationControllers.size() < MAX_CONNECTIONS;
    }

    public List<BlockPos> getStationControllers() {
        return new ArrayList<>(stationControllers);
    }

    public void setProximityOpening(boolean proximityOpening) {
        this.proximityOpening = proximityOpening;
        markDirty();
    }

    public void setProximityAutoClose(boolean proximityAutoClose) {
        this.proximityAutoClose = proximityAutoClose;
        markDirty();
    }

    public void setRedstoneControl(boolean redstoneControl) {
        this.redstoneControl = redstoneControl;
        markDirty();
    }

    public void setManualClick(boolean manualClick) {
        this.manualClick = manualClick;
        markDirty();
    }

    public void setAutoSealOnLeak(boolean autoSealOnLeak) {
        this.autoSealOnLeak = autoSealOnLeak;
        markDirty();
    }

    public void setCheckInterval(int checkInterval) {
        this.checkInterval = Math.clamp(checkInterval, MIN_CHECK_INTERVAL, MAX_CHECK_INTERVAL);
        markDirty();
    }

    public void setCloseDelay(int closeDelay) {
        this.closeDelay = Math.clamp(closeDelay, MIN_CLOSE_DELAY, MAX_CLOSE_DELAY);
        markDirty();
    }

    public void setProximityRange(int proximityRange) {
        this.proximityRange = Math.clamp(proximityRange, MIN_PROXIMITY_RANGE, MAX_PROXIMITY_RANGE);
        if (structureValid) this.doorwayAABB = computeDoorwayAABB();
        markDirty();
    }

    public void toggleState() {
        if (!structureValid) return;
        if (redstonePowered) return;
        if (!manualClick) return;

        switch (state) {
            case CLOSED -> {
                state = AirlockState.OPEN;
                setDoorState(true);
            }
            case OPEN -> {
                state = AirlockState.CLOSED;
                setDoorState(false);
            }
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote || !structureValid) return;

        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        if (redstoneControl && powered != redstonePowered) {
            // Rising edge -> open and stay open while the signal is applied; falling edge -> close.
            redstonePowered = powered;
            setDoorState(powered);
        } else if (!redstoneControl && redstonePowered) {
            redstonePowered = false;
        }
        // Redstone has precedence over proximity detection; the door stays open for the signal duration.
        if (redstonePowered) return;

        if (!proximityOpening) {
            proximityCheckTimer = 0;
            return;
        }

        if (proximityCheckTimer > 0) {
            proximityCheckTimer--;
            return;
        }
        proximityCheckTimer = checkInterval;

        boolean playerNear = !worldObj.getEntitiesWithinAABB(EntityPlayer.class, doorwayAABB)
            .isEmpty();
        if (playerNear) {
            if (!isOpen()) {
                setDoorState(true);
                // Keep the door open for the close-delay so a player can pass through before the next check.
                proximityCheckTimer = closeDelay;
            }
        } else if (isOpen() && proximityAutoClose) {
            setDoorState(false);
        }
    }

    /**
     * Box covering every door block of the structure in world space, expanded by {@link #proximityRange} in front of
     * and behind the wall so that players approaching from either side are detected.
     */
    private AxisAlignedBB computeDoorwayAABB() {
        if (xMin == INVALID || xMax == INVALID || yMin == INVALID || yMax == INVALID) return null;

        double[] bounds = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
            -Double.MAX_VALUE };
        for (int x = xMin + 1; x <= xMax - 1; x++) {
            for (int y = yMin + 1; y <= yMax - 1; y++) {
                STRUCTURE_DEFINITION.iterate(
                    STRUCTURE_CENTER,
                    worldObj,
                    currentFacing,
                    xCoord,
                    yCoord,
                    zCoord,
                    x,
                    y,
                    0,
                    (_, w, ox, oy, oz, _, _, _) -> {
                        if (w.getBlock(ox, oy, oz) instanceof BlockOpenable) {
                            bounds[0] = Math.min(bounds[0], ox);
                            bounds[1] = Math.min(bounds[1], oy);
                            bounds[2] = Math.min(bounds[2], oz);
                            bounds[3] = Math.max(bounds[3], ox + 1.0);
                            bounds[4] = Math.max(bounds[4], oy + 1.0);
                            bounds[5] = Math.max(bounds[5], oz + 1.0);
                        }
                        return true;
                    });
            }
        }
        if (bounds[0] == Double.MAX_VALUE) return null;

        // Expand only along the wall's depth axis (the door slab's thin axis), i.e. in front of and behind the door.
        // The magnitude is used since the depth direction may have negative offsets.
        ForgeDirection depth = currentFacing.getRelativeBackInWorld();
        return AxisAlignedBB.getBoundingBox(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])
            .expand(
                Math.abs(depth.offsetX) * proximityRange,
                Math.abs(depth.offsetY) * proximityRange,
                Math.abs(depth.offsetZ) * proximityRange);
    }

    public boolean trackStationController(BlockPos pos) {
        if (stationControllers.size() >= MAX_CONNECTIONS) {
            return false;
        }

        if (stationControllers.contains(pos)) return true;
        stationControllers.add(pos);
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        notifyDirtySeal();
        return true;
    }

    public void untrackStationController(BlockPos pos) {
        if (!stationControllers.remove(pos)) {
            Galaxia.LOG.error("Invalid station controller to untrack");
        }
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    protected boolean checkStructure() {
        // Cardinal step offsets in the local (X, Y) plane, ordered +X, -X, +Y, -Y so each pair is opposite.
        final int[] stepX = { 1, -1, 0, 0 };
        final int[] stepY = { 0, 0, 1, -1 };

        // Probe outwards in all four directions to locate which side of the frame the controller sits on.
        int[] distance = new int[4];
        int flush = -1;
        int flushCount = 0;
        for (int d = 0; d < 4; d++) {
            distance[d] = probeDirection(0, 0, stepX[d], stepY[d]);
            if (distance[d] == INVALID) return false;
            if (distance[d] == 0) {
                flush = d;
                flushCount++;
            }
        }

        // The controller must sit on the casing frame, flush with exactly one side.
        if (flushCount != 1) return false;

        // The room extends away from the flush side; the far frame is one step behind it.
        int main = flush ^ 1;
        int mainExtent = distance[main];

        // The perpendicular half-width, measured one cell into the room (must be symmetric -> controller is centered).
        int perp = flush < 2 ? 2 : 0;
        int extentHigh = probeDirection(stepX[main], stepY[main], stepX[perp], stepY[perp]);
        int extentLow = probeDirection(stepX[main], stepY[main], stepX[perp + 1], stepY[perp + 1]);
        if (extentHigh != extentLow || extentHigh < 1) {
            return false;
        }

        // Project the rectangle {0..mainExtent along main, -half..half along perp} onto the X/Y axes.
        int extentX = extentHigh * Math.abs(stepX[perp]);
        xMin = Math.min(0, mainExtent * stepX[main]) - extentX;
        xMax = Math.max(0, mainExtent * stepX[main]) + extentX;

        int extentY = extentHigh * Math.abs(stepY[perp]);
        yMin = Math.min(0, mainExtent * stepY[main]) - extentY;
        yMax = Math.max(0, mainExtent * stepY[main]) + extentY;

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                // Skip the controller itself
                if (x == 0 && y == 0) continue;

                boolean isEdge = (x == xMin || x == xMax || y == yMin || y == yMax);
                String expected = isEdge ? STRUCTURE_EDGE : STRUCTURE_CENTER;
                if (!checkPiece(expected, x, y, 0)) return false;
            }
        }

        return true;
    }

    /**
     * Probes from a local offset (startX, startY), walking in the given direction (stepX, stepY), until a casing
     * frame is found. Returns the distance to the frame, 0 if the very first cell is already outside the structure
     * (edge), or {@link #INVALID} if the frame is missing or the probe exceeds {@link #MAXIMUM_RADIUS}.
     */
    private int probeDirection(int startX, int startY, int stepX, int stepY) {
        for (int i = 1; i <= MAXIMUM_RADIUS; i++) {
            int x = startX + stepX * i;
            int y = startY + stepY * i;
            if (checkPiece(STRUCTURE_EDGE, x, y, 0)) return i;
            if (!checkPiece(STRUCTURE_CENTER, x, y, 0)) {
                return i == 1 ? 0 : INVALID;
            }
        }
        return INVALID;
    }

    @Override
    protected void onStructureFormed() {
        setDoorState(false);
        this.doorwayAABB = computeDoorwayAABB();
        GalaxiaAPI.causeMachineUpdate(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    protected void onStructureDisformed() {
        setDoorState(false);
        this.doorwayAABB = null;
        this.xMin = INVALID;
        this.xMax = INVALID;
        this.yMin = INVALID;
        this.yMax = INVALID;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 256 * 256;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return AirlockGUI.build(this, data, syncManager, settings);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setInteger("state", state.ordinal());
        nbt.setTag("stationControllers", BlockPos.listToNBT(stationControllers));
        nbt.setBoolean("proximityOpening", proximityOpening);
        nbt.setBoolean("proximityAutoClose", proximityAutoClose);
        nbt.setBoolean("redstoneControl", redstoneControl);
        nbt.setBoolean("manualClick", manualClick);
        nbt.setBoolean("autoSealOnLeak", autoSealOnLeak);
        nbt.setInteger("checkInterval", checkInterval);
        nbt.setInteger("closeDelay", closeDelay);
        nbt.setInteger("proximityRange", proximityRange);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        int s = nbt.getInteger("state");
        if (s >= 0 && s < AirlockState.values().length) {
            state = AirlockState.values()[s];
        }

        if (nbt.hasKey("stationControllers")) {
            stationControllers = BlockPos.listFromNBT(nbt.getTagList("stationControllers", Constants.NBT.TAG_COMPOUND));
        }

        if (nbt.hasKey("proximityOpening")) proximityOpening = nbt.getBoolean("proximityOpening");
        if (nbt.hasKey("proximityAutoClose")) proximityAutoClose = nbt.getBoolean("proximityAutoClose");
        if (nbt.hasKey("redstoneControl")) redstoneControl = nbt.getBoolean("redstoneControl");
        if (nbt.hasKey("manualClick")) manualClick = nbt.getBoolean("manualClick");
        if (nbt.hasKey("autoSealOnLeak")) autoSealOnLeak = nbt.getBoolean("autoSealOnLeak");
        if (nbt.hasKey("checkInterval")) setCheckInterval(nbt.getInteger("checkInterval"));
        if (nbt.hasKey("closeDelay")) setCloseDelay(nbt.getInteger("closeDelay"));
        if (nbt.hasKey("proximityRange")) setProximityRange(nbt.getInteger("proximityRange"));
    }

    private void setDoorState(boolean open) {
        state = open ? AirlockState.OPEN : AirlockState.CLOSED;

        for (int x = xMin + 1; x <= xMax - 1; x++) {
            for (int y = yMin + 1; y <= yMax - 1; y++) {
                STRUCTURE_DEFINITION.iterate(
                    STRUCTURE_CENTER,
                    worldObj,
                    currentFacing,
                    xCoord,
                    yCoord,
                    zCoord,
                    x,
                    y,
                    0,
                    (_, w, ox, oy, oz, _, _, _) -> {
                        Block b = w.getBlock(ox, oy, oz);
                        if (b instanceof BlockOpenable door) {
                            door.setOpen(w, ox, oy, oz, open);
                            return true;
                        }
                        return false;
                    });
            }
        }

        // No need to update lights, if it ever causes any problems try with
        // worldObj.markBlockRangeForRenderUpdate( doorBounds[0], doorBounds[1], doorBounds[2], doorBounds[3],
        // doorBounds[4], doorBounds[5]);
        notifyDirtySeal();
        this.markDirty();
    }

    private void notifyDirtySeal() {
        for (BlockPos controllerPos : getStationControllers()) {
            TileStationBase<?> controller = controllerPos.getTE(worldObj);
            if (controller != null) controller.markSealedDirty();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (isChunkUnloading) {
            setDoorState(false);
        }
    }
}
