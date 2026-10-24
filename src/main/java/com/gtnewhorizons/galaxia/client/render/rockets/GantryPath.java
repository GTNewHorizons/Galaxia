package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The route of a module around the gantry currently holding it, as the curve its hooks run along. Positions are
 * relative to the centre of that gantry and measured in blocks along the route: 0 is where the module entered the
 * gantry, 1 where it leaves, negative values lie behind it.
 * <p>
 * Inside a block the curve runs from the entry face to the exit face, straight or as a quarter circle of radius 0.5
 * when the route turns, the same curve the rail corners and bends are modelled on.
 * <p>
 * Hooks hang below flat rails. On vertical rails they hang to the side the module came from or is heading to: below a
 * bend the outer side, above a bend the inner one, which is where hanging below the flat rail ends up
 */
@SideOnly(Side.CLIENT)
final class GantryPath {

    /** Distance from the rail centre down to where the hooks hold the module */
    static final double HOOK_DROP = 0.8;

    private static final Vec DOWN = new Vec(0, -1, 0);
    private static final Vec SIDE = new Vec(1, 0, 0);

    private final ForgeDirection[] moves;
    private final int window;
    private final Vec[] blocks;
    private final Vec[] normals;

    private GantryPath(ForgeDirection[] moves, int window) {
        this.moves = moves;
        this.window = window;

        // Block b sits at index b + window, the module's own block is b = 0
        blocks = new Vec[moves.length + 1];
        blocks[window] = Vec.ZERO;
        for (int i = window; i < moves.length; i++) blocks[i + 1] = blocks[i].add(Vec.of(moves[i]));
        for (int i = window - 1; i >= 0; i--) blocks[i] = blocks[i + 1].sub(Vec.of(moves[i]));

        normals = new Vec[moves.length];
        for (int i = 0; i < moves.length; i++) normals[i] = boundaryNormal(i);
    }

    /**
     * Reads the path a gantry received with its module, unknown moves continue straight
     *
     * @param raw Moves as {@link ForgeDirection} ordinals, the module's current move in the middle
     *
     * @return The path, or null if there is none
     */
    static GantryPath of(byte[] raw) {
        if (raw == null || raw.length % 2 == 0) return null;
        int window = raw.length / 2;
        if (raw[window] == TileEntityGantry.NO_MOVE) return null;
        ForgeDirection[] moves = new ForgeDirection[raw.length];
        moves[window] = ForgeDirection.getOrientation(raw[window]);
        for (int i = window + 1; i < raw.length; i++) {
            moves[i] = raw[i] == TileEntityGantry.NO_MOVE ? moves[i - 1] : ForgeDirection.getOrientation(raw[i]);
        }
        for (int i = window - 1; i >= 0; i--) {
            moves[i] = raw[i] == TileEntityGantry.NO_MOVE ? moves[i + 1] : ForgeDirection.getOrientation(raw[i]);
        }
        return new GantryPath(moves, window);
    }

    /** How far the path reaches either way from the module's block */
    double reach() {
        return window - 1;
    }

    /**
     * Where a point of the path lies and which way it runs
     *
     * @param s Position along the path in blocks
     *
     * @return The pose at that position
     */
    Pose pose(double s) {
        int block = (int) Math.floor(s);
        block = Math.max(-window + 1, Math.min(window, block));
        double t = Math.max(0, Math.min(1, s - block));
        int index = block + window;
        ForgeDirection in = moves[index - 1];
        ForgeDirection out = moves[index];

        Vec position;
        Vec tangent;
        if (in == out || in == out.getOpposite()) {
            // A reversed move only happens when a module is rerouted, it simply heads off the new way
            position = Vec.of(out)
                .scale(t - 0.5);
            tangent = Vec.of(out);
        } else {
            // Quarter circle around the block edge between the entry and exit faces
            double angle = t * Math.PI / 2;
            Vec vin = Vec.of(in), vout = Vec.of(out);
            Vec centre = vin.scale(-0.5)
                .add(vout.scale(0.5));
            position = centre.add(vout.scale(-0.5 * Math.cos(angle)))
                .add(vin.scale(0.5 * Math.sin(angle)));
            tangent = vin.scale(Math.cos(angle))
                .add(vout.scale(Math.sin(angle)))
                .normalize();
        }

        Vec normal = slerp(normals[index - 1], normals[index], t);
        normal = normal.sub(tangent.scale(normal.dot(tangent)));
        if (normal.length() < 1e-4) normal = tangent.y != 0 ? SIDE : DOWN;
        return new Pose(blocks[index].add(position), tangent, normal.normalize());
    }

    /**
     * Side the hooks hang to where the module crosses into the next block
     */
    private Vec boundaryNormal(int move) {
        if (moves[move].offsetY == 0) return DOWN;

        // Vertical run containing this move, and the flat moves just before and after it
        int sign = moves[move].offsetY;
        int first = move, last = move;
        while (first > 0 && moves[first - 1].offsetY != 0) first--;
        while (last < moves.length - 1 && moves[last + 1].offsetY != 0) last++;
        Vec entry = first > 0 ? Vec.of(moves[first - 1])
            .scale(sign) : null;
        Vec exit = last < moves.length - 1 ? Vec.of(moves[last + 1])
            .scale(sign) : null;

        if (entry == null && exit == null) return SIDE;
        if (entry == null) return exit;
        if (exit == null) return entry;
        // Different ends: the hooks swing round the shaft on the way
        return slerp(entry, exit, (move - first + 1) / (double) (last - first + 2));
    }

    /**
     * Spherical interpolation between two unit vectors
     */
    private static Vec slerp(Vec a, Vec b, double f) {
        double dot = Math.max(-1, Math.min(1, a.dot(b)));
        if (dot > 0.9999) return a;
        if (dot < -0.9999) {
            // Opposite: turn around any axis square to both
            Vec axis = a.cross(new Vec(0, 1, 0));
            if (axis.length() < 1e-4) axis = a.cross(SIDE);
            axis = axis.normalize();
            return a.scale(Math.cos(f * Math.PI))
                .add(
                    axis.cross(a)
                        .scale(Math.sin(f * Math.PI)));
        }
        double angle = Math.acos(dot);
        double sin = Math.sin(angle);
        return a.scale(Math.sin((1 - f) * angle) / sin)
            .add(b.scale(Math.sin(f * angle) / sin));
    }

    /**
     * Point on the path: position, running direction and the side hooks hang to, all unit vectors but the position
     */
    record Pose(Vec position, Vec tangent, Vec normal) {}

    record Vec(double x, double y, double z) {

        static final Vec ZERO = new Vec(0, 0, 0);

        static Vec of(ForgeDirection direction) {
            return new Vec(direction.offsetX, direction.offsetY, direction.offsetZ);
        }

        Vec add(Vec o) {
            return new Vec(x + o.x, y + o.y, z + o.z);
        }

        Vec sub(Vec o) {
            return new Vec(x - o.x, y - o.y, z - o.z);
        }

        Vec scale(double s) {
            return new Vec(x * s, y * s, z * s);
        }

        double dot(Vec o) {
            return x * o.x + y * o.y + z * o.z;
        }

        Vec cross(Vec o) {
            return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }

        double length() {
            return Math.sqrt(dot(this));
        }

        Vec normalize() {
            double length = length();
            return length < 1e-9 ? this : scale(1 / length);
        }
    }
}
