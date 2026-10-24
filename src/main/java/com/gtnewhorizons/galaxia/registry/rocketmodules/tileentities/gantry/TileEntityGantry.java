package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

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

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;

/** A physical node. The world Rocket manifest owns module custody, movement and route claims. */
public class TileEntityGantry extends TileEntity {

    public static final double MOVEMENT_PER_TICK = 0.2;
    public static final int PATH_WINDOW = 8;
    public static final byte NO_MOVE = -1;
    private UUID nodeId = UUID.randomUUID();
    private GantryNetwork network;
    private boolean joinedNetwork;
    final List<TileEntityGantry> neighbours = new ArrayList<>();
    private final Map<UUID, Neighbour> savedNeighbours = new LinkedHashMap<>();
    public final List<Vec3> neighbourDirs = new ArrayList<>();
    private Vec3 currentDirection;
    public Vec3 clientIncomingDirection;
    public int clientModuleId = -1;
    public float clientPrevProgress;
    public float clientProgress;
    public byte[] clientPath = new byte[0];

    private record Neighbour(UUID id, int x, int y, int z) {}

    public UUID nodeId() {
        return nodeId;
    }

    public int dimension() {
        return worldObj.provider.dimensionId;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        if (worldObj.isRemote) {
            clientPrevProgress = clientProgress;
            return;
        }
        if (!joinedNetwork) {
            joinedNetwork = true;
            invalidateAround();
        }
        GantryAPI.tickNode(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            this);
    }

    /** Physical connections use loaded face-adjacent rails. Unattached harness nodes use explicit links. */
    public List<TileEntityGantry> getNeighbours() {
        if (worldObj == null) return List.copyOf(neighbours);
        var result = new ArrayList<TileEntityGantry>(6);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            TileEntityGantry next = getNeighbour(side);
            if (next == null) continue;
            result.add(next);
            savedNeighbours.put(next.nodeId(), new Neighbour(next.nodeId(), next.xCoord, next.yCoord, next.zCoord));
        }
        return result;
    }

    public void connect(TileEntityGantry other) {
        if (other == this) return;
        neighbours.removeIf(
            previous -> previous != other && previous.xCoord == other.xCoord
                && previous.yCoord == other.yCoord
                && previous.zCoord == other.zCoord);
        savedNeighbours.values()
            .removeIf(
                previous -> !previous.id()
                    .equals(other.nodeId()) && previous.x() == other.xCoord
                    && previous.y() == other.yCoord
                    && previous.z() == other.zCoord);
        if (!neighbours.contains(other)) neighbours.add(other);
        if (!other.neighbours.contains(this)) other.neighbours.add(this);
        savedNeighbours.put(other.nodeId(), new Neighbour(other.nodeId(), other.xCoord, other.yCoord, other.zCoord));
        other.savedNeighbours.put(nodeId, new Neighbour(nodeId, xCoord, yCoord, zCoord));
        invalidateAround();
        other.invalidateAround();
        updateNeighbourDirs();
        other.updateNeighbourDirs();
    }

    public void disconnect(TileEntityGantry other) {
        neighbours.remove(other);
        other.neighbours.remove(this);
        savedNeighbours.remove(other.nodeId());
        other.savedNeighbours.remove(nodeId);
        invalidateAround();
        other.invalidateAround();
        updateNeighbourDirs();
        other.updateNeighbourDirs();
    }

    public void updateNeighbourDirs() {
        if (worldObj != null && worldObj.isRemote) return;
        neighbourDirs.clear();
        for (TileEntityGantry next : getNeighbours()) {
            neighbourDirs
                .add(Vec3.createVectorHelper(next.xCoord - xCoord, next.yCoord - yCoord, next.zCoord - zCoord));
        }
        syncProjection();
    }

    public boolean checkValidGraph() {
        return worldObj != null && GantryAPI.terminatesWithTerminals(worldObj, xCoord, yCoord, zCoord);
    }

    public GantryNetwork getNetwork() {
        if (network == null || !network.isValid()) network = GantryNetwork.build(this);
        return network;
    }

    void bindNetwork(GantryNetwork network) {
        this.network = network;
    }

    public void invalidateNetwork() {
        if (network != null) network.invalidate();
        network = null;
    }

    public void invalidateAround() {
        invalidateNetwork();
        for (TileEntityGantry next : getNeighbours()) next.invalidateNetwork();
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

    public static boolean connects(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ) instanceof TileEntityGantry;
    }

    public static int connectionMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (connects(world, x, y, z, side)) mask |= 1 << side.ordinal();
        }
        return mask;
    }

    @Nullable
    public TileEntityGantry getNeighbour(ForgeDirection side) {
        if (worldObj == null || side == ForgeDirection.UNKNOWN) return null;
        int x = xCoord + side.offsetX, y = yCoord + side.offsetY, z = zCoord + side.offsetZ;
        if (!worldObj.blockExists(x, y, z)) return null;
        return worldObj.getTileEntity(x, y, z) instanceof TileEntityGantry next && !next.isInvalid() ? next : null;
    }

    public static void onRailChanged(World world, int x, int y, int z) {
        if (world.isRemote) return;
        if (world.getTileEntity(x, y, z) instanceof TileEntityGantry node) node.invalidateNetwork();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            int nx = x + side.offsetX, ny = y + side.offsetY, nz = z + side.offsetZ;
            if (world.blockExists(nx, ny, nz) && world.getTileEntity(nx, ny, nz) instanceof TileEntityGantry next)
                next.invalidateNetwork();
        }
    }

    @Nullable
    public RocketPartInstance getModule() {
        if (worldObj == null || worldObj.isRemote) return null;
        var module = GantryAPI.heldModule(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            nodeId);
        return module == null ? null : module.part();
    }

    public Vec3 getDirection() {
        return currentDirection;
    }

    public float getProgress() {
        return clientProgress;
    }

    public float getInterpolatedProgress(float partialTicks) {
        return clientPrevProgress + (clientProgress - clientPrevProgress) * partialTicks;
    }

    void projectDirection(TileEntityGantry next) {
        currentDirection = Vec3.createVectorHelper(next.xCoord - xCoord, next.yCoord - yCoord, next.zCoord - zCoord);
    }

    void projectDirectionToController() {
        if (this instanceof TileEntityGantryTerminal terminal) {
            TileEntity controller = terminal.controller();
            currentDirection = controller == null ? null
                : Vec3.createVectorHelper(
                    controller.xCoord - xCoord,
                    controller.yCoord - yCoord,
                    controller.zCoord - zCoord);
        }
    }

    public void syncProjection() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("nodeId", nodeId.toString());
        var links = new LinkedHashMap<>(savedNeighbours);
        for (TileEntityGantry next : neighbours) {
            links.put(next.nodeId(), new Neighbour(next.nodeId(), next.xCoord, next.yCoord, next.zCoord));
        }
        var encoded = new NBTTagList();
        for (Neighbour next : links.values()) {
            var link = new NBTTagCompound();
            link.setString(
                "id",
                next.id()
                    .toString());
            link.setInteger("x", next.x());
            link.setInteger("y", next.y());
            link.setInteger("z", next.z());
            encoded.appendTag(link);
        }
        tag.setTag("neighbours", encoded);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        var in = new NbtReader(tag, "gantry");
        nodeId = UUID.fromString(in.string("nodeId"));
        neighbours.clear();
        savedNeighbours.clear();
        var links = in.compounds("neighbours");
        neighbourDirs.clear();
        for (int i = 0; i < links.tagCount(); i++) {
            var link = in.element("neighbours", i, links.getCompoundTagAt(i));
            var next = new Neighbour(
                UUID.fromString(link.string("id")),
                link.integer("x"),
                link.integer("y"),
                link.integer("z"));
            if (savedNeighbours.put(next.id(), next) != null)
                throw new IllegalStateException("Duplicate Gantry neighbor");
            neighbourDirs.add(Vec3.createVectorHelper(next.x() - xCoord, next.y() - yCoord, next.z() - zCoord));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        var tag = new NBTTagCompound();
        tag.setString("nodeId", nodeId.toString());
        var directions = new NBTTagList();
        for (Vec3 direction : neighbourDirs) {
            var entry = new NBTTagCompound();
            entry.setDouble("x", direction.xCoord);
            entry.setDouble("y", direction.yCoord);
            entry.setDouble("z", direction.zCoord);
            directions.appendTag(entry);
        }
        tag.setTag("directions", directions);
        var module = GantryAPI.heldModule(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            nodeId);
        tag.setInteger(
            "moduleId",
            module == null ? -1
                : module.part()
                    .def()
                    .id());
        tag.setFloat(
            "progress",
            module == null || module.gantryTransport() == null ? 0
                : (float) module.gantryTransport()
                    .progress());
        // A handoff can reach this node after its tick. Project the new trip, never the previous module's heading.
        currentDirection = null;
        if (module != null && module.gantryTransport() != null) {
            var trip = module.gantryTransport();
            int nextIndex = trip.routeIndex() + 1;
            if (nextIndex < trip.reservedRoute()
                .size()) {
                var next = savedNeighbours.get(
                    trip.reservedRoute()
                        .get(nextIndex));
                if (next != null) {
                    currentDirection = Vec3.createVectorHelper(next.x() - xCoord, next.y() - yCoord, next.z() - zCoord);
                }
            } else {
                projectDirectionToController();
            }
        }
        if (currentDirection != null) {
            tag.setDouble("dirX", currentDirection.xCoord);
            tag.setDouble("dirY", currentDirection.yCoord);
            tag.setDouble("dirZ", currentDirection.zCoord);
        }
        if (module != null && module.gantryTransport() != null) tag.setByteArray("path", pathWindow(module));
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    /** Derives the hook curve from the same immutable route that owns this module's movement. */
    private byte[] pathWindow(Rocket.Module module) {
        byte[] path = new byte[PATH_WINDOW * 2 + 1];
        Arrays.fill(path, NO_MOVE);
        var trip = module.gantryTransport();
        var route = trip.reservedRoute();
        for (int i = -PATH_WINDOW; i <= PATH_WINDOW; i++) {
            int index = trip.routeIndex() + i;
            if (index < 0 || index >= route.size()) continue;
            TileEntityGantry from = getNetwork().findNode(route.get(index));
            if (from == null) continue;
            Vec3 direction;
            if (index + 1 < route.size()) {
                TileEntityGantry to = getNetwork().findNode(route.get(index + 1));
                if (to == null) continue;
                direction = Vec3
                    .createVectorHelper(to.xCoord - from.xCoord, to.yCoord - from.yCoord, to.zCoord - from.zCoord);
            } else if (from instanceof TileEntityGantryTerminal terminal) {
                int[] controller = terminal.getConsumerPosition();
                if (controller == null) continue;
                direction = Vec3.createVectorHelper(
                    controller[0] - from.xCoord,
                    controller[1] - from.yCoord,
                    controller[2] - from.zCoord);
            } else continue;
            var side = toDirection(direction);
            if (side != ForgeDirection.UNKNOWN) path[PATH_WINDOW + i] = (byte) side.ordinal();
        }
        return path;
    }

    private static ForgeDirection toDirection(Vec3 step) {
        double ax = Math.abs(step.xCoord), ay = Math.abs(step.yCoord), az = Math.abs(step.zCoord);
        if (ax == 0 && ay == 0 && az == 0) return ForgeDirection.UNKNOWN;
        if (ay >= ax && ay >= az) return step.yCoord > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;
        if (ax >= az) return step.xCoord > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;
        return step.zCoord > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        var tag = packet.func_148857_g();
        nodeId = UUID.fromString(tag.getString("nodeId"));
        clientModuleId = tag.getInteger("moduleId");
        clientPrevProgress = clientProgress;
        clientProgress = tag.getFloat("progress");
        clientPath = tag.getByteArray("path");
        currentDirection = tag.hasKey("dirX")
            ? Vec3.createVectorHelper(tag.getDouble("dirX"), tag.getDouble("dirY"), tag.getDouble("dirZ"))
            : null;
        var directions = tag.getTagList("directions", NBT.TAG_COMPOUND);
        neighbourDirs.clear();
        for (int i = 0; i < directions.tagCount(); i++) {
            var direction = directions.getCompoundTagAt(i);
            neighbourDirs.add(
                Vec3.createVectorHelper(direction.getDouble("x"), direction.getDouble("y"), direction.getDouble("z")));
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }

}
