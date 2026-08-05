package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;

import lombok.Getter;

public class TileEntityAirlock extends GalaxiaMultiblockBase<TileEntityAirlock> {

    public enum AirlockState {
        CLOSED,
        OPEN,
    }

    @Getter
    private AirlockState state = AirlockState.CLOSED;

    public static final int MAX_CONNECTIONS = 2;
    private List<BlockPos> stationControllers = new ArrayList<>(MAX_CONNECTIONS);

    /**
     * Controller is now on the BOTTOM layer of the structure.
     */
    public static final int CONTROLLER_OFFSET_X = 2;
    public static final int CONTROLLER_OFFSET_Y = 2;
    public static final int CONTROLLER_OFFSET_Z = 0;

    public static final int MAXIMUM_RADIUS = 8;
    public static final String STRUCTURE_PIECE_MAIN = "main";
    public static final String STRUCTURE_EDGE = "edge";
    public static final String STRUCTURE_CENTER = "center";

    public static final int INVALID = -1;

    public int xMin = INVALID;
    public int xMax = INVALID;
    public int yMin = INVALID;
    public int yMax = INVALID;

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
        .addShape(STRUCTURE_EDGE, new String[][] { { "C" } })
        .addShape(STRUCTURE_CENTER, new String[][] { { "D" } })
        .addElement('C', GalaxiaStructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 0))
        .addElement('D', GalaxiaStructureUtility.ofBlockAnyMeta(GalaxiaBlocksEnum.AIRLOCK_DOOR.get()))
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

    public void toggleState() {
        if (!structureValid) return;

        switch (state) {
            case CLOSED -> {
                state = AirlockState.OPEN;
                openDoor();
            }
            case OPEN -> {
                state = AirlockState.CLOSED;
                closeDoor();
            }
        }
    }

    public boolean trackStationController(BlockPos pos) {
        if (stationControllers.size() >= MAX_CONNECTIONS) {
            return false;
        }

        if (stationControllers.contains(pos)) return true;
        stationControllers.add(pos);
        markDirty();
        notifyDirtySeal();
        return true;
    }

    public void untrackStationController(BlockPos pos) {
        if (!stationControllers.remove(pos)) {
            Galaxia.LOG.error("Invalid station controller to untrack");
        }
        markDirty();
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
        closeDoor();
    }

    @Override
    protected void onStructureDisformed() {
        closeDoor();
        this.xMin = INVALID;
        this.xMax = INVALID;
        this.yMin = INVALID;
        this.yMax = INVALID;
    }

    private void openDoor() {
        setDoorState(true);
    }

    private void closeDoor() {
        setDoorState(false);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 256 * 256;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setInteger("state", state.ordinal());
        nbt.setTag("stationControllers", BlockPos.listToNBT(stationControllers));
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

        notifyDirtySeal();
        this.markDirty();

        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
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
