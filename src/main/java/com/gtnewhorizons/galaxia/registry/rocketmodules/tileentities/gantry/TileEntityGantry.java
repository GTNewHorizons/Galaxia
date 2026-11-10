package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.TransitModule;

/**
 * One block of gantry rail. Rails connect to gantries and terminals on all six faces, like pipes, and the connections
 * are always read from the world, so what the player sees is what the network uses
 */
public class TileEntityGantry extends TileEntity {

    /** Blocks a module moves per tick */
    public static final float SPEED = 0.1f;
    /** Moves of the route sent to clients on each side of a module, enough for the hooks of the longest module */
    public static final int PATH_WINDOW = TransitModule.TRAIL_LENGTH;
    /** Marks an unknown move in the path window */
    public static final byte NO_MOVE = -1;

    private static final int DISPATCH_INTERVAL = 40;
    private static final int NO_ROUTE_RETRY = 20;

    private final Deque<TransitModule> queue = new ArrayDeque<>();
    private int dispatchCooldown = 0;
    // Modules saved with the world, resolved once their destination terminals are loaded
    private NBTTagList pendingTransit;
    private GantryNetwork network;
    private boolean joinedNetwork = false;

    private TransitModule containedTransitModule;
    private Vec3 currentDirection;
    private float progress = 0f;
    // Route around the moving module, PATH_WINDOW moves back, the current move, PATH_WINDOW moves ahead
    private byte[] path = new byte[0];

    public int clientModuleId = -1;
    public float clientPrevProgress = 0f;
    public float clientProgress = 0f;
    public byte[] clientPath = new byte[0];

    /**
     * Updates the tile entity. In this case, handling current module progression
     * and queue management
     */
    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            // -1 used as "null" as 0 is a valid ID
            if (clientModuleId != -1) {
                // The module waits at the end of the block until the server hands it to the next gantry
                clientPrevProgress = clientProgress;
                clientProgress = Math.min(1f, clientProgress + SPEED);
            }
            return;
        }

        if (!joinedNetwork) {
            // A gantry loading next to an existing network makes it rebuild with this one included
            joinedNetwork = true;
            invalidateAround();
        }

        resolvePendingTransit();

        if (dispatchCooldown > 0) dispatchCooldown--;
        tryDispatch();

        if (containedTransitModule == null) {
            return;
        }

        // Progress module
        progress += SPEED;

        // If progress finished, hand off to next gantry
        if (progress >= 1.0f) {
            progress = 1.0f;
            handOff();
        }
        markDirty();
    }

    /**
     * Starts the next queued module if this gantry is free and knows where to send it
     */
    private void tryDispatch() {
        if (queue.isEmpty() || dispatchCooldown > 0 || containedTransitModule != null) return;

        TransitModule entry = queue.peek();
        if (entry.destination() == null || entry.destination()
            .isInvalid()) {
            // Destination was removed, send the module back to an assembler instead of losing it
            List<TileEntityGantryTerminal> returns = getNetwork().getReturnPoints();
            if (!returns.isEmpty()) {
                queue.poll();
                entry = entry.redirect(returns.getFirst());
                queue.addFirst(entry);
            }
        }
        Vec3 direction = getDirectionTo(entry.destination());
        if (direction == null) {
            // No route right now, the network may still be under construction
            dispatchCooldown = NO_ROUTE_RETRY;
            return;
        }

        queue.poll();
        containedTransitModule = entry;
        currentDirection = direction;
        progress = 0f;
        path = buildPath(entry, direction);

        if (this instanceof TileEntityGantryTerminal) {
            // Long modules need more room before the next one leaves
            int length = entry.module()
                .def()
                .height();
            dispatchCooldown = Math.max(DISPATCH_INTERVAL, (int) Math.ceil((length + 1) / SPEED));
        }

        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * The route around a module that just started moving here: the moves it made to get here, the move it starts now
     * and the moves still ahead of it
     */
    private byte[] buildPath(TransitModule transit, Vec3 direction) {
        byte[] result = new byte[PATH_WINDOW * 2 + 1];
        Arrays.fill(result, NO_MOVE);
        byte[] trail = transit.trail();
        for (int i = 0; i < Math.min(trail.length, PATH_WINDOW); i++) {
            result[PATH_WINDOW - 1 - i] = trail[i];
        }

        ForgeDirection move = toDirection(direction);
        result[PATH_WINDOW] = (byte) move.ordinal();
        TileEntityGantry next = getNeighbour(move);
        TileEntityGantryTerminal destination = transit.destination();
        for (int i = 1; i <= PATH_WINDOW && next != null && next != this; i++) {
            Vec3 step = next.getDirectionTo(destination);
            if (step == null) break;
            move = toDirection(step);
            result[PATH_WINDOW + i] = (byte) move.ordinal();
            // At the destination the last move points into the machine
            if (next == destination) break;
            next = next.getNeighbour(move);
        }
        return result;
    }

    /**
     * Closest axis direction of a step, steps into a machine can point at a controller further away
     */
    static ForgeDirection toDirection(Vec3 step) {
        double ax = Math.abs(step.xCoord), ay = Math.abs(step.yCoord), az = Math.abs(step.zCoord);
        if (ax == 0 && ay == 0 && az == 0) return ForgeDirection.EAST;
        if (ay >= ax && ay >= az) return step.yCoord > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;
        if (ax >= az) return step.xCoord > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;
        return step.zCoord > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
    }

    /**
     * Gets the network this gantry belongs to, rebuilding it if the topology changed
     *
     * @return The current network
     */
    public GantryNetwork getNetwork() {
        if (network == null || !network.isValid()) {
            network = GantryNetwork.build(this);
        }
        return network;
    }

    void bindNetwork(GantryNetwork network) {
        this.network = network;
    }

    /** Marks the current network stale so it is rebuilt on the next query */
    public void invalidateNetwork() {
        if (network != null) network.invalidate();
        network = null;
    }

    /** Marks this network and the networks of all touching gantries stale, used whenever rails are added or removed */
    public void invalidateAround() {
        invalidateNetwork();
        for (TileEntityGantry neighbour : getNeighbours()) neighbour.invalidateNetwork();
    }

    /**
     * Direction from this gantry to the next step towards a terminal. Once at the terminal it points into the machine
     * the terminal feeds
     *
     * @param destination The terminal to reach
     *
     * @return The step vector, or null if there is no route
     */
    public Vec3 getDirectionTo(TileEntityGantryTerminal destination) {
        if (destination == null || destination.isInvalid()) return null;
        TileEntityGantry next = getNetwork().nextHop(this, destination);
        if (next == null) return null;
        if (next == this) {
            int[] machine = destination.getConsumerPosition();
            if (machine == null) return Vec3.createVectorHelper(0, 0, 0);
            return Vec3.createVectorHelper(machine[0] - xCoord, machine[1] - yCoord, machine[2] - zCoord);
        }
        return Vec3.createVectorHelper(next.xCoord - xCoord, next.yCoord - yCoord, next.zCoord - zCoord);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateNetwork();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        invalidateNetwork();
    }

    /**
     * Gets the current direction of the gantry
     *
     * @return The current direction
     */
    public Vec3 getDirection() {
        return currentDirection;
    }

    /**
     * Gets an interpolated progress based on the partial tick
     *
     * @param partialTicks The amount through the current tick the world is
     * @return The interpolated progress
     */
    public float getInterpolatedProgress(float partialTicks) {
        return clientPrevProgress + (clientProgress - clientPrevProgress) * partialTicks;
    }

    /**
     * Gets the current progress of the module stored (0 if no module)
     *
     * @return Current progress / 0 if no module
     */
    public float getProgress() {
        return progress;
    }

    /**
     * Whether a gantry or terminal sits on the given side of a block
     *
     * @param world The world to look in
     * @param x     X position of the block
     * @param y     Y position of the block
     * @param z     Z position of the block
     * @param side  The side to check
     *
     * @return Boolean : True => the rail connects on that side
     */
    public static boolean connects(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ) instanceof TileEntityGantry;
    }

    /**
     * Sides a block at the given position connects on, bit per {@link ForgeDirection} ordinal
     */
    public static int connectionMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (connects(world, x, y, z, side)) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    /**
     * Gets the gantry on one side, never loading chunks
     *
     * @param side The side to look at
     *
     * @return The gantry found or null
     */
    public TileEntityGantry getNeighbour(ForgeDirection side) {
        if (worldObj == null || side == ForgeDirection.UNKNOWN) return null;
        int x = xCoord + side.offsetX, y = yCoord + side.offsetY, z = zCoord + side.offsetZ;
        if (!worldObj.blockExists(x, y, z)) return null;
        return worldObj.getTileEntity(x, y, z) instanceof TileEntityGantry gantry && !gantry.isInvalid() ? gantry
            : null;
    }

    /**
     * Gets the gantries this one is connected to
     *
     * @return Every gantry or terminal touching one of the six faces
     */
    public List<TileEntityGantry> getNeighbours() {
        List<TileEntityGantry> result = new ArrayList<>(6);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            TileEntityGantry neighbour = getNeighbour(side);
            if (neighbour != null) result.add(neighbour);
        }
        return result;
    }

    /**
     * Clears the module from gantry and resets progress
     */
    public void clearModule() {
        progress = 0f;
        clientProgress = 0f;
        clientPrevProgress = 0f;
        clientModuleId = -1;
        containedTransitModule = null;
        currentDirection = null;
        path = new byte[0];
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public RocketPartInstance getModule() {
        if (containedTransitModule == null) return null;
        return containedTransitModule.module();
    }

    /**
     * Hands off a module to the next gantry in the path of the module. If the path broke while the module was moving
     * it waits here and gets routed again instead of being lost
     */
    public void handOff() {
        TransitModule transit = containedTransitModule;
        if (transit == null) return;

        // Get next gantry to hand to
        TileEntityGantry next = getNeighbourGantry(currentDirection);
        if (next == null || next == this) {
            // If terminal endpoint, pass to consumer (assembler / silo etc.)
            if (this instanceof TileEntityGantryTerminal terminal && terminal == transit.destination()
                && terminal.passModuleToConsumer(transit.module())) {
                clearModule();
                return;
            }
            clearModule();
            queue.addFirst(transit);
            dispatchCooldown = NO_ROUTE_RETRY;
            return;
        }

        // If next gantry can take it, pass it on and let it start right away so the module never stops between blocks
        if (next.acceptModule(transit.moved(toDirection(currentDirection)))) {
            clearModule();
            next.tryDispatch();
        }
    }

    /**
     * Gets the neighbouring gantry based on a given direction
     *
     * @param dir The vector direction to check
     *
     * @return The gantry found or null
     */
    public TileEntityGantry getNeighbourGantry(Vec3 dir) {
        if (dir == null) return null;
        int nx = xCoord + (int) dir.xCoord;
        int ny = yCoord + (int) dir.yCoord;
        int nz = zCoord + (int) dir.zCoord;
        if (!worldObj.blockExists(nx, ny, nz)) return null;
        TileEntity te = worldObj.getTileEntity(nx, ny, nz);
        return (te instanceof TileEntityGantry ? (TileEntityGantry) te : null);
    }

    /**
     * Adds a new transit module to the dispatch queue
     *
     * @param transit The transit module to add
     */
    public void enqueueModule(TransitModule transit) {
        queue.addLast(transit);
        markDirty();
    }

    /**
     * Takes an incoming transit module and adds it to the dispatch queue
     *
     * @param transit The transit module
     *
     * @return Boolean : True => accepted module
     */
    public boolean acceptModule(TransitModule transit) {
        if (worldObj.isRemote || transit == null) return false;
        enqueueModule(transit);
        return true;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList transit = new NBTTagList();
        if (containedTransitModule != null) transit.appendTag(containedTransitModule.serialize());
        for (TransitModule entry : queue) transit.appendTag(entry.serialize());
        // Unresolved entries are kept as-is so an early save does not drop them
        if (pendingTransit != null) {
            for (int i = 0; i < pendingTransit.tagCount(); i++) {
                transit.appendTag(pendingTransit.getCompoundTagAt(i));
            }
        }
        tag.setTag("transit", transit);
    }

    /**
     * Restores modules that were moving when the world was saved. A module in the middle of a step restarts that
     * step, which keeps the save format independent of animation state
     */
    private void resolvePendingTransit() {
        if (pendingTransit == null) return;
        NBTTagList entries = pendingTransit;
        pendingTransit = null;
        for (int i = 0; i < entries.tagCount(); i++) {
            TransitModule entry = TransitModule.deserialize(worldObj, entries.getCompoundTagAt(i));
            if (entry != null) queue.addLast(entry);
        }
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("transit")) {
            NBTTagList transit = tag.getTagList("transit", NBT.TAG_COMPOUND);
            if (transit.tagCount() > 0) pendingTransit = transit;
        }
    }

    /** Number of modules waiting in this gantry, including the one moving */
    public int getLoad() {
        return queue.size() + (containedTransitModule != null ? 1 : 0);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeDescription(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    /** Fills the description packet, subclasses add their own state */
    protected void writeDescription(NBTTagCompound tag) {
        if (containedTransitModule != null) {
            tag.setInteger(
                "moduleId",
                containedTransitModule.module()
                    .def()
                    .id());
            tag.setByteArray("path", path);
            tag.setFloat("progress", progress);
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();

        // DO NOT call readFromNBT(tag) here — it will clobber xCoord/yCoord/zCoord
        clientModuleId = tag.hasKey("moduleId") ? tag.getInteger("moduleId") : -1;
        clientProgress = tag.getFloat("progress");
        clientPrevProgress = clientProgress;
        clientPath = tag.getByteArray("path");
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

    /**
     * Rebuilds the networks around a block that became or stopped being a rail
     *
     * @param world The world the block is in
     * @param x     X position of the block
     * @param y     Y position of the block
     * @param z     Z position of the block
     */
    public static void onRailChanged(World world, int x, int y, int z) {
        if (world.isRemote) return;
        if (world.getTileEntity(x, y, z) instanceof TileEntityGantry gantry) gantry.invalidateNetwork();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            int nx = x + side.offsetX, ny = y + side.offsetY, nz = z + side.offsetZ;
            if (world.blockExists(nx, ny, nz)
                && world.getTileEntity(nx, ny, nz) instanceof TileEntityGantry neighbour) {
                neighbour.invalidateNetwork();
            }
        }
    }
}
