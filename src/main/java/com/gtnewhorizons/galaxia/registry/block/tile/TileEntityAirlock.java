package com.gtnewhorizons.galaxia.registry.block.tile;

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

public class TileEntityAirlock extends GalaxiaMultiblockBase<TileEntityAirlock> {

    public TileEntityAirlock() {
        super();
    }

    public enum AirlockState {
        CLOSED,
        OPEN,
    }

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

    public int halfHeight = INVALID;
    public int halfWidth = INVALID;

    public static final IStructureDefinition<TileEntityAirlock> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityAirlock>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
            StructureUtility.transpose(new String[][] {
                { "CCCCC" },
                { "CDDDC" },
                { "CD~DC" },
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

    public AirlockState getState() {
        return state;
    }

    public boolean isOpen() {
        return state == AirlockState.OPEN;
    }

    public boolean isExternalConnection() {
        return stationControllers.size() < MAX_CONNECTIONS;
    }

    public boolean isInternalConnection() {
        return !isExternalConnection();
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

        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean trackStationController(BlockPos pos) {
        if (stationControllers.size() >= MAX_CONNECTIONS) {
            return false;
        }

        if (stationControllers.contains(pos)) return true;
        stationControllers.add(pos);
        markDirty();
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
        int halfWidth = 0, halfHeight = 0;

        // Find a corner
        while (halfWidth < MAXIMUM_RADIUS) {
            halfWidth += 1;
            if (checkPiece(STRUCTURE_EDGE, halfWidth, 0, 0)) break;
        }

        while (halfHeight < MAXIMUM_RADIUS) {
            halfHeight += 1;
            if (checkPiece(STRUCTURE_EDGE, 0, halfHeight, 0)) break;
        }

        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = -halfHeight; y <= halfHeight; y++) {
                // Skip the controller itself
                if (x == 0 && y == 0) continue;

                boolean isEdge = (Math.abs(x) == halfWidth || Math.abs(y) == halfHeight);
                String expected = isEdge ? STRUCTURE_EDGE : STRUCTURE_CENTER;
                if (!checkPiece(expected, x, y, 0)) return false;
            }
        }

        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;

        return true;
    }

    @Override
    protected void onStructureFormed() {
        closeDoor();
    }

    @Override
    protected void onStructureDisformed() {
        closeDoor();
        this.halfHeight = INVALID;
        this.halfWidth = INVALID;
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

        final int hw = halfWidth - 1;
        final int hh = halfHeight - 1;

        for (int x = -hw; x <= hw; x++) {
            for (int y = -hh; y <= hh; y++) {
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

        this.markDirty();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (isChunkUnloading) {
            setDoorState(false);
        }
    }
}
