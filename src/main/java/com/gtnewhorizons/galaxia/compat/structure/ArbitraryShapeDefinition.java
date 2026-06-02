package com.gtnewhorizons.galaxia.compat.structure;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.util.DenseBitSet;
import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;

import lombok.Getter;

public class ArbitraryShapeDefinition<T extends GalaxiaMultiblockBase<T>> implements IStructureDefinition<T> {

    private static final int[] DIR_DX = { 0, 0, 0, 0, 1, -1 };
    private static final int[] DIR_DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DIR_DZ = { 1, -1, 0, 0, 0, 0 };

    /**
     * Chunk size for the hierarchical flood: each axis of a chunk cell spans
     * 2^CHUNK_SHIFT. Tuning this higher reduces the coarse-pass overhead but
     * widens the "shell" that still needs fine-grained work.
     */
    private static final int CHUNK_SHIFT = 2;
    private static final int CHUNK_SIZE = 1 << CHUNK_SHIFT;

    @Getter
    private final int searchRadius;
    private T tile;
    @Getter
    private int volume;
    private final boolean enclosed;

    // ── Boundary elements (keyed by Block, define the shell) ──────────────────
    private final Map<Block, IStructureElement<T>> structureElements;

    // ── Interior elements (keyed by Block, matched inside the formed volume) ──
    //
    // Interior elements are NOT part of the boundary flood. They are scanned
    // once after a successful full validation via a cheap chunk TE map walk,
    // and their positions are persisted in interiorBlocks for fastRevalidate.
    //
    // For enclosed structures the TE must also pass isInsideStructure().
    // For open structures the AABB bounds are sufficient.
    //
    // element.check() is called exactly once per full validation — never during
    // fastRevalidate — so side-effects (e.g. attachment registration) fire
    // predictably and without duplication.
    private final Map<Block, List<IStructureElement<T>>> interiorElements;

    // ── Temporary bitsets — sized to searchRadius, cleared after every check ─
    private DenseBitSet floodVisited;
    private DenseBitSet validBoundaryBits;

    // ── Persistent bitsets — sized to the actual structure AABB ───────────────
    private DenseBitSet structureBlocks; // boundary blocks that passed check
    private DenseBitSet enclosedVisited; // every block inside the closed volume (enclosed only)
    private DenseBitSet interiorBlocks; // interior TE positions that passed check last full validation

    // Bounds of the currently allocated persistent bitsets
    private int encMinX, encMinY, encMinZ;
    private int encLenX = -1, encLenY = -1, encLenZ = -1;

    /**
     * AABB of valid-boundary blocks found by the most recent floodStructure call.
     * Stored in local (controller-relative) coordinates.
     */
    private int aabbMinX, aabbMaxX;
    private int aabbMinY, aabbMaxY;
    private int aabbMinZ, aabbMaxZ;

    // ── Coarse (chunk-level) state for the hierarchical flood ─────────────────
    private final int coarseRadius;
    private final DenseBitSet chunkHasBoundary;
    private final DenseBitSet coarseVisited;
    private final DenseBitSet coarseInterior;

    public static <T extends GalaxiaMultiblockBase<T>> Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    private ArbitraryShapeDefinition(Map<Block, IStructureElement<T>> structureElements,
        Map<Block, List<IStructureElement<T>>> interiorElements, int searchRadius, boolean enclosed) {

        if (searchRadius > LocalCoord.MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("Search radius too large: " + searchRadius);
        }
        // spotless:off
        this.enclosed           = enclosed;
        this.searchRadius       = searchRadius;
        this.structureElements  = structureElements;
        this.interiorElements   = interiorElements;

        this.structureBlocks    = null;
        this.enclosedVisited    = null;
        this.interiorBlocks     = null;

        // For open structures coarseRadius is 0 — the coarse bitsets are 1×1×1
        // and are never touched, but must still be allocated.
        this.coarseRadius       = enclosed ? (searchRadius >> CHUNK_SHIFT) + 2 : 0;
        int crLen               = 2 * coarseRadius + 1;
        this.chunkHasBoundary   = new DenseBitSet(-coarseRadius, -coarseRadius, -coarseRadius, crLen, crLen, crLen);
        this.coarseVisited      = new DenseBitSet(-coarseRadius, -coarseRadius, -coarseRadius, crLen, crLen, crLen);
        this.coarseInterior     = new DenseBitSet(-coarseRadius, -coarseRadius, -coarseRadius, crLen, crLen, crLen);
        // spotless:on
    }

    // ── IStructureDefinition ──────────────────────────────────────────────────

    @Override
    public IStructureElement<T>[] getStructureFor(String s) {
        return structureElements.values()
            .toArray(new IStructureElement[0]);
    }

    @Override
    public boolean isContainedInStructure(String shapeName, int x, int y, int z) {
        if (tile == null) {
            Galaxia.LOG.error("Structure is not formed yet");
            return false;
        }
        return structureBlocks.containsChecked(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord);
    }

    @Override
    public boolean check(T tile, String shapeName, World world, ExtendedFacing extendedFacing, int x, int y, int z,
        int offsetX, int offsetY, int offsetZ, boolean forceCheckAllBlocks) {
        return enclosed ? enclosedCheck(tile, world, extendedFacing) : openCheck(tile, world);
    }

    @Override
    public boolean hints(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing, int x,
        int y, int z, int offsetX, int offsetY, int offsetZ) {
        // TODO: In addition to normal building, there should also be leak detection
        // that marks enclosedVisited near the boundary
        return false;
    }

    @Override
    public boolean build(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing, int x,
        int y, int z, int offsetX, int offsetY, int offsetZ) {
        // TODO: Build a big cube the size specified in the trigger
        return false;
    }

    @Override
    public boolean buildOrHints(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, boolean hintsOnly) {
        return false;
    }

    @Override
    public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, int elementBudget, IItemSource source,
        EntityPlayerMP player, boolean hintsOnly) {
        return -1;
    }

    @Override
    public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, int elementBudget, ISurvivalBuildEnvironment env,
        boolean hintsOnly) {
        return -1;
    }

    @Override
    public void iterate(String shapeName, World world, ExtendedFacing extendedFacing, int x, int y, int z, int offsetX,
        int offsetY, int offsetZ, IStructureWalker<T> walker) {}

    public boolean isInsideStructure(int x, int y, int z) {
        if (tile == null) {
            Galaxia.LOG.error("Structure is not formed yet");
            return false;
        }
        return enclosed && enclosedVisited.containsChecked(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord)
            || isInCoarseInteriorChecked(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord);
    }

    public int getStructureBlocksAmount() {
        return structureBlocks != null ? structureBlocks.size() : 0;
    }

    private boolean openCheck(T tile, World world) {
        if (fastRevalidate(tile, world)) return true;

        int sr = searchRadius;
        int srLen = 2 * sr + 1;
        this.floodVisited = new DenseBitSet(-sr, -sr, -sr, srLen, srLen, srLen);
        this.validBoundaryBits = new DenseBitSet(-sr, -sr, -sr, srLen, srLen, srLen);

        floodStructure(tile, world);

        if (canReuse()) {
            structureBlocks.clear();
            if (interiorBlocks != null) interiorBlocks.clear();
        } else {
            structureBlocks = new DenseBitSet(aabbMinX, aabbMinY, aabbMinZ, encLenX, encLenY, encLenZ);
            interiorBlocks = interiorElements.isEmpty() ? null
                : new DenseBitSet(aabbMinX, aabbMinY, aabbMinZ, encLenX, encLenY, encLenZ);
        }

        final boolean[] valid = { true };
        this.validBoundaryBits.forEach((lx, ly, lz) -> {
            if (!valid[0]) return;
            if (!checkValidBoundary(
                tile,
                world,
                LocalCoord.worldX(lx, tile.xCoord),
                LocalCoord.worldY(ly, tile.yCoord),
                LocalCoord.worldZ(lz, tile.zCoord))) {
                valid[0] = false;
                return;
            }
            this.structureBlocks.add(lx, ly, lz);
        });

        if (valid[0] && !interiorElements.isEmpty()) {
            scanInteriorElements(tile, world);
        }

        this.tile = tile;
        this.floodVisited = null;
        this.validBoundaryBits = null;
        return valid[0];
    }

    private boolean enclosedCheck(T tile, World world, ExtendedFacing extendedFacing) {
        if (fastRevalidate(tile, world)) return true;

        if (floodVisited == null) {
            int sr = searchRadius;
            int srLen = 2 * sr + 1;
            this.floodVisited = new DenseBitSet(-sr, -sr, -sr, srLen, srLen, srLen);
            this.validBoundaryBits = new DenseBitSet(-sr, -sr, -sr, srLen, srLen, srLen);
        }
        coarseInterior.clear();
        coarseVisited.clear();
        floodStructure(tile, world);

        if (canReuse()) {
            structureBlocks.clear();
            enclosedVisited.clear();
            if (interiorBlocks != null) interiorBlocks.clear();
        } else {
            structureBlocks = new DenseBitSet(aabbMinX, aabbMinY, aabbMinZ, encLenX, encLenY, encLenZ);
            enclosedVisited = new DenseBitSet(aabbMinX, aabbMinY, aabbMinZ, encLenX, encLenY, encLenZ);
            interiorBlocks = interiorElements.isEmpty() ? null
                : new DenseBitSet(aabbMinX, aabbMinY, aabbMinZ, encLenX, encLenY, encLenZ);
        }

        ForgeDirection placedFacing = extendedFacing.getDirection();
        boolean isEnclosed = checkEnclosed(tile, world, placedFacing);
        if (isEnclosed) {
            this.tile = tile;
            this.volume = (enclosedVisited.size() + coarseVisited.size() * coarseRadius * coarseRadius * coarseRadius)
                - structureElements.size();

            if (!interiorElements.isEmpty()) {
                scanInteriorElements(tile, world);
            }

            floodVisited = null;
            validBoundaryBits = null;
        } else {
            // Discard temporary state; enclosedVisited and structureBlocks are kept
            // for isInsideStructure / fastRevalidate queries.
            floodVisited.clear();
            validBoundaryBits.clear();
        }
        return isEnclosed;
    }

    private boolean fastRevalidate(T tile, World world) {
        if (structureBlocks == null || !tile.isStructureValid() || structureBlocks.isEmpty()) return false;
        if (world == null || world.isRemote) return true;

        final boolean[] valid = { true };

        // ── Phase 1: boundary ─────────────────────────────────────────────────
        structureBlocks.forEach((lx, ly, lz) -> {
            if (!valid[0]) return;
            if (!couldBeValidBoundary(
                tile,
                world,
                LocalCoord.worldX(lx, tile.xCoord),
                LocalCoord.worldY(ly, tile.yCoord),
                LocalCoord.worldZ(lz, tile.zCoord))) {
                valid[0] = false;
                return;
            }
            for (int d = 0; d < 6; d++) {
                int nx = lx + DIR_DX[d], ny = ly + DIR_DY[d], nz = lz + DIR_DZ[d];
                if (structureBlocks.containsChecked(nx, ny, nz)) continue;
                // If the neighbor is outside the structure volume, changes there
                // cannot affect our enclosure — skip it
                if (enclosedVisited != null && !enclosedVisited.containsChecked(nx, ny, nz)
                    && !isInCoarseInteriorChecked(nx, ny, nz)) continue;
                if (couldBeValidBoundary(
                    tile,
                    world,
                    LocalCoord.worldX(nx, tile.xCoord),
                    LocalCoord.worldY(ny, tile.yCoord),
                    LocalCoord.worldZ(nz, tile.zCoord))) {
                    valid[0] = false;
                    return;
                }
            }
        });

        if (!valid[0]) return false;

        // ── Phase 2: known interior elements ─────────────────────────────────
        if (interiorBlocks != null && !interiorBlocks.isEmpty()) {
            interiorBlocks.forEach((lx, ly, lz) -> {
                if (!valid[0]) return;
                if (!interiorElements.containsKey(
                    world.getBlock(
                        LocalCoord.worldX(lx, tile.xCoord),
                        LocalCoord.worldY(ly, tile.yCoord),
                        LocalCoord.worldZ(lz, tile.zCoord)))) {
                    valid[0] = false;
                }
            });
        }

        if (!valid[0]) return false;

        // ── Phase 3: new interior element detection ───────────────────────────
        return interiorElements.isEmpty() || !hasNewInteriorElements(tile, world);
    }

    /**
     * Walks the chunk TE maps covering the structure AABB and calls
     * {@code element.check()} on every TE whose block type is registered as
     * an interior element. Passing positions are recorded in {@code interiorBlocks}.
     */
    private void scanInteriorElements(T tile, World world) {
        if (interiorBlocks == null) return;

        int minX = tile.xCoord + aabbMinX, maxX = tile.xCoord + aabbMaxX;
        int minY = tile.yCoord + aabbMinY, maxY = tile.yCoord + aabbMaxY;
        int minZ = tile.zCoord + aabbMinZ, maxZ = tile.zCoord + aabbMaxZ;

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) continue;
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);

                for (TileEntity te : chunk.chunkTileEntityMap.values()) {
                    if (te.xCoord < minX || te.xCoord > maxX) continue;
                    if (te.yCoord < minY || te.yCoord > maxY) continue;
                    if (te.zCoord < minZ || te.zCoord > maxZ) continue;

                    int lx = te.xCoord - tile.xCoord;
                    int ly = te.yCoord - tile.yCoord;
                    int lz = te.zCoord - tile.zCoord;

                    if (structureBlocks != null && structureBlocks.containsChecked(lx, ly, lz)) continue;
                    if (enclosed && !isInsideStructure(te.xCoord, te.yCoord, te.zCoord)) continue;

                    List<IStructureElement<T>> els = interiorElements
                        .get(world.getBlock(te.xCoord, te.yCoord, te.zCoord));
                    for (var el : els) {
                        if (el != null && el.check(tile, world, te.xCoord, te.yCoord, te.zCoord)) {
                            interiorBlocks.add(lx, ly, lz);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns {@code true} if any loaded TE in the AABB matches a registered
     * interior element block type but is NOT already recorded in
     * {@code interiorBlocks}.
     */
    private boolean hasNewInteriorElements(T tile, World world) {
        int minX = tile.xCoord + aabbMinX, maxX = tile.xCoord + aabbMaxX;
        int minY = tile.yCoord + aabbMinY, maxY = tile.yCoord + aabbMaxY;
        int minZ = tile.zCoord + aabbMinZ, maxZ = tile.zCoord + aabbMaxZ;

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) continue;
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);

                for (TileEntity te : chunk.chunkTileEntityMap.values()) {
                    if (te.xCoord < minX || te.xCoord > maxX) continue;
                    if (te.yCoord < minY || te.yCoord > maxY) continue;
                    if (te.zCoord < minZ || te.zCoord > maxZ) continue;

                    if (!interiorElements.containsKey(world.getBlock(te.xCoord, te.yCoord, te.zCoord))) continue;
                    if (enclosed && !isInsideStructure(te.xCoord, te.yCoord, te.zCoord)) continue;

                    int lx = te.xCoord - tile.xCoord;
                    int ly = te.yCoord - tile.yCoord;
                    int lz = te.zCoord - tile.zCoord;

                    if (structureBlocks != null && structureBlocks.containsChecked(lx, ly, lz)) continue;
                    if (interiorBlocks != null && interiorBlocks.containsChecked(lx, ly, lz)) continue;

                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the current AABB matches the previously allocated
     * persistent bitset dimensions. When they match the bitsets can be cleared
     * in-place, avoiding allocation churn on repeated validation.
     */
    private boolean canReuse() {
        int neededLenX = aabbMaxX - aabbMinX + 1;
        int neededLenY = aabbMaxY - aabbMinY + 1;
        int neededLenZ = aabbMaxZ - aabbMinZ + 1;

        if (neededLenX == encLenX && neededLenY == encLenY
            && neededLenZ == encLenZ
            && aabbMinX == encMinX
            && aabbMinY == encMinY
            && aabbMinZ == encMinZ) {
            return true;
        } else {
            encMinX = aabbMinX;
            encMinY = aabbMinY;
            encMinZ = aabbMinZ;
            encLenX = neededLenX;
            encLenY = neededLenY;
            encLenZ = neededLenZ;
            return false;
        }
    }

    /**
     * BFS from the controller outward through valid-boundary blocks.
     * Populates {@code validBoundaryBits} and the block-level AABB.
     */
    private void floodStructure(T tile, World world) {
        floodVisited.clear();
        validBoundaryBits.clear();

        aabbMinX = aabbMinY = aabbMinZ = Integer.MAX_VALUE;
        aabbMaxX = aabbMaxY = aabbMaxZ = Integer.MIN_VALUE;

        IntQueue floodBFS = new IntQueue();
        final int xCoord = tile.xCoord, yCoord = tile.yCoord, zCoord = tile.zCoord;
        final int sr = searchRadius;

        floodBFS.enqueue(LocalCoord.pack(0, 0, 0, sr));
        floodVisited.add(0, 0, 0);
        addToBoundary(0, 0, 0);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur, sr);
            int ly = LocalCoord.unpackY(cur, sr);
            int lz = LocalCoord.unpackZ(cur, sr);

            // Include diagonals to support disconnected structure like diagonal walls
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int nlx = lx + dx, nly = ly + dy, nlz = lz + dz;

                        if (!LocalCoord.isInBounds(nlx, nly, nlz, sr)) continue;
                        if (!floodVisited.add(nlx, nly, nlz)) continue;
                        if (!couldBeValidBoundary(
                            tile,
                            world,
                            LocalCoord.worldX(nlx, xCoord),
                            LocalCoord.worldY(nly, yCoord),
                            LocalCoord.worldZ(nlz, zCoord))) continue;

                        addToBoundary(nlx, nly, nlz);
                        floodBFS.enqueue(LocalCoord.pack(nlx, nly, nlz, sr));
                    }
                }
            }
        }
    }

    private void addToBoundary(int lx, int ly, int lz) {
        validBoundaryBits.add(lx, ly, lz);
        if (lx < aabbMinX) aabbMinX = lx;
        if (lx > aabbMaxX) aabbMaxX = lx;
        if (ly < aabbMinY) aabbMinY = ly;
        if (ly > aabbMaxY) aabbMaxY = ly;
        if (lz < aabbMinZ) aabbMinZ = lz;
        if (lz > aabbMaxZ) aabbMaxZ = lz;
    }

    private boolean couldBeValidBoundary(T tile, World world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        IStructureElement<T> element = structureElements.get(b);
        if (element == null) return false;
        return element.couldBeValid(tile, world, x, y, z, null);
    }

    private boolean checkValidBoundary(T tile, World world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        IStructureElement<T> element = structureElements.get(b);
        if (element == null) return false;
        return element.check(tile, world, x, y, z);
    }

    /**
     * Hierarchical enclosure check — two levels of granularity.
     *
     * <p>
     * <b>Phase 1 — coarse BFS (chunk granularity):</b> starting from the chunk
     * that contains the seed block, flood outward through chunks that have no
     * valid-boundary bits. Any chunk that escapes the structure's coarse AABB
     * proves the interior is open → return {@code false}. All surviving chunks
     * are recorded in {@code coarseInterior}.
     *
     * <p>
     * <b>Phase 2 — bulk pre-mark + fine BFS seeding:</b> every block in every
     * {@code coarseInterior} chunk is marked visited in one sweep. The face of
     * each boundary chunk adjacent to a {@code coarseInterior} chunk is enqueued
     * as the seed for the fine BFS.
     *
     * <p>
     * <b>Phase 3 — fine BFS (block granularity, shell only):</b> the fine BFS
     * traverses the shell of boundary chunks. Wall blocks (those in
     * {@code validBoundaryBits} that pass {@code checkValidBoundary}) are
     * collected into {@code structureBlocks}.
     *
     * <p>
     * Complexity improvement (hollow sphere, radius R, chunk size C=4):
     * <ul>
     * <li>Original: O(R³) fine-BFS block visits
     * <li>Hierarchical: O(R²) fine-BFS block visits + O(R³/C³) bulk marks
     * </ul>
     */
    private boolean checkEnclosed(T tile, World world, ForgeDirection placedFacing) {
        chunkHasBoundary.clear();
        validBoundaryBits
            .forEach((lx, ly, lz) -> chunkHasBoundary.add(lx >> CHUNK_SHIFT, ly >> CHUNK_SHIFT, lz >> CHUNK_SHIFT));

        final int sx = placedFacing.offsetX, sy = placedFacing.offsetY, sz = placedFacing.offsetZ;
        final int sr = searchRadius;
        final int xCoord = tile.xCoord, yCoord = tile.yCoord, zCoord = tile.zCoord;

        final int caMinX = aabbMinX >> CHUNK_SHIFT, caMaxX = aabbMaxX >> CHUNK_SHIFT;
        final int caMinY = aabbMinY >> CHUNK_SHIFT, caMaxY = aabbMaxY >> CHUNK_SHIFT;
        final int caMinZ = aabbMinZ >> CHUNK_SHIFT, caMaxZ = aabbMaxZ >> CHUNK_SHIFT;

        final int csx = sx >> CHUNK_SHIFT, csy = sy >> CHUNK_SHIFT, csz = sz >> CHUNK_SHIFT;
        final int cr = coarseRadius;

        IntQueue coarseBFS = new IntQueue();

        if (!chunkHasBoundary.contains(csx, csy, csz) && csx >= caMinX
            && csx <= caMaxX
            && csy >= caMinY
            && csy <= caMaxY
            && csz >= caMinZ
            && csz <= caMaxZ) {
            coarseVisited.add(csx, csy, csz);
            coarseInterior.add(csx, csy, csz);
            coarseBFS.enqueue(LocalCoord.pack(csx, csy, csz, cr));
        }

        while (!coarseBFS.isEmpty()) {
            int cur = coarseBFS.dequeue();
            int cx = LocalCoord.unpackX(cur, cr);
            int cy = LocalCoord.unpackY(cur, cr);
            int cz = LocalCoord.unpackZ(cur, cr);

            for (int d = 0; d < 6; d++) {
                int ncx = cx + DIR_DX[d], ncy = cy + DIR_DY[d], ncz = cz + DIR_DZ[d];

                if (ncx < caMinX || ncx > caMaxX || ncy < caMinY || ncy > caMaxY || ncz < caMinZ || ncz > caMaxZ)
                    return false;

                if (!coarseVisited.add(ncx, ncy, ncz)) continue;
                if (chunkHasBoundary.contains(ncx, ncy, ncz)) continue;

                coarseInterior.add(ncx, ncy, ncz);
                coarseBFS.enqueue(LocalCoord.pack(ncx, ncy, ncz, cr));
            }
        }

        IntQueue fineBFS = new IntQueue();

        coarseInterior.forEach((cx, cy, cz) -> {
            for (int d = 0; d < 6; d++) {
                int ncx = cx + DIR_DX[d], ncy = cy + DIR_DY[d], ncz = cz + DIR_DZ[d];
                if (chunkHasBoundary.contains(ncx, ncy, ncz)) {
                    enqueueFace(ncx, ncy, ncz, d, fineBFS, sr);
                }
            }
        });

        if (!coarseInterior.contains(csx, csy, csz) && sx >= aabbMinX
            && sx <= aabbMaxX
            && sy >= aabbMinY
            && sy <= aabbMaxY
            && sz >= aabbMinZ
            && sz <= aabbMaxZ
            && enclosedVisited.add(sx, sy, sz)) {
            fineBFS.enqueue(LocalCoord.pack(sx, sy, sz, sr));
        }

        while (!fineBFS.isEmpty()) {
            int cur = fineBFS.dequeue();
            int lx = LocalCoord.unpackX(cur, sr);
            int ly = LocalCoord.unpackY(cur, sr);
            int lz = LocalCoord.unpackZ(cur, sr);

            for (int d = 0; d < 6; d++) {
                int nlx = lx + DIR_DX[d], nly = ly + DIR_DY[d], nlz = lz + DIR_DZ[d];

                if (nlx < aabbMinX || nlx > aabbMaxX
                    || nly < aabbMinY
                    || nly > aabbMaxY
                    || nlz < aabbMinZ
                    || nlz > aabbMaxZ) return false;

                if (isInCoarseInterior(nlx, nly, nlz)) continue;
                if (!enclosedVisited.add(nlx, nly, nlz)) continue;

                if (validBoundaryBits.contains(nlx, nly, nlz)) {
                    int nwx = LocalCoord.worldX(nlx, xCoord);
                    int nwy = LocalCoord.worldY(nly, yCoord);
                    int nwz = LocalCoord.worldZ(nlz, zCoord);
                    if (checkValidBoundary(tile, world, nwx, nwy, nwz)) {
                        structureBlocks.add(nlx, nly, nlz);
                        continue;
                    }
                }

                fineBFS.enqueue(LocalCoord.pack(nlx, nly, nlz, sr));
            }
        }

        return !structureBlocks.isEmpty() && structureBlocks.size() >= 6;
    }

    private boolean isInCoarseInteriorChecked(int lx, int ly, int lz) {
        return coarseInterior.containsChecked(lx >> CHUNK_SHIFT, ly >> CHUNK_SHIFT, lz >> CHUNK_SHIFT);
    }

    private boolean isInCoarseInterior(int lx, int ly, int lz) {
        return coarseInterior.contains(lx >> CHUNK_SHIFT, ly >> CHUNK_SHIFT, lz >> CHUNK_SHIFT);
    }

    /**
     * Enqueues the interior-facing face of boundary chunk {@code (ncx, ncy, ncz)}.
     *
     * <p>
     * {@code d} is the direction <em>interior → boundary</em>. The face we
     * want is therefore the {@code −d} face of the boundary chunk — the
     * CHUNK_SIZE² slice nearest to the interior.
     *
     * <p>
     * Blocks already marked visited are silently skipped by {@link #tryEnqueue}.
     */
    private void enqueueFace(int ncx, int ncy, int ncz, int d, IntQueue bfs, int sr) {
        int bx0 = ncx << CHUNK_SHIFT, by0 = ncy << CHUNK_SHIFT, bz0 = ncz << CHUNK_SHIFT;
        int dx = DIR_DX[d], dy = DIR_DY[d], dz = DIR_DZ[d];

        if (dx != 0) {
            int fx = (dx > 0) ? bx0 : bx0 + CHUNK_SIZE - 1;
            for (int iy = 0; iy < CHUNK_SIZE; iy++)
                for (int iz = 0; iz < CHUNK_SIZE; iz++) tryEnqueue(fx, by0 + iy, bz0 + iz, bfs, sr);
        } else if (dy != 0) {
            int fy = (dy > 0) ? by0 : by0 + CHUNK_SIZE - 1;
            for (int ix = 0; ix < CHUNK_SIZE; ix++)
                for (int iz = 0; iz < CHUNK_SIZE; iz++) tryEnqueue(bx0 + ix, fy, bz0 + iz, bfs, sr);
        } else {
            int fz = (dz > 0) ? bz0 : bz0 + CHUNK_SIZE - 1;
            for (int ix = 0; ix < CHUNK_SIZE; ix++)
                for (int iy = 0; iy < CHUNK_SIZE; iy++) tryEnqueue(bx0 + ix, by0 + iy, fz, bfs, sr);
        }
    }

    private void tryEnqueue(int lx, int ly, int lz, IntQueue bfs, int sr) {
        if (lx >= aabbMinX && lx <= aabbMaxX
            && ly >= aabbMinY
            && ly <= aabbMaxY
            && lz >= aabbMinZ
            && lz <= aabbMaxZ
            && enclosedVisited.add(lx, ly, lz)) {
            bfs.enqueue(LocalCoord.pack(lx, ly, lz, sr));
        }
    }

    public static class Builder<T extends GalaxiaMultiblockBase<T>> {

        private final Map<Block, IStructureElement<T>> elements = new HashMap<>();
        private final Map<Block, List<IStructureElement<T>>> interiorElements = new HashMap<>();
        private int searchRadius = LocalCoord.SEARCH_RADIUS;
        private int enclosed = -1;

        private Builder() {}

        public Builder<T> enclosed() {
            this.enclosed = 1;
            return this;
        }

        public Builder<T> open() {
            this.enclosed = 0;
            return this;
        }

        public Builder<T> withSearchRadius(int radius) {
            if (radius > LocalCoord.MAX_SEARCH_RADIUS) {
                throw new IllegalArgumentException("Search radius too large for 10-bit encoding: " + radius);
            }
            this.searchRadius = radius;
            return this;
        }

        public Builder<T> addControllerBlock(Block controller) {
            return addElement(
                GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta(
                    (c, te) -> te != null && te.xCoord == c.xCoord && te.yCoord == c.yCoord && te.zCoord == c.zCoord,
                    controller,
                    0));
        }

        // ── Boundary elements ─────────────────────────────────────────────────

        public Builder<T> addElement(IExtendedStructureElement<T> element) {
            elements.put(element.getValidBlock(), element);
            return this;
        }

        public Builder<T> addElements(Stream<IExtendedStructureElement<T>> elements) {
            this.elements.putAll(elements.collect(Collectors.toMap(IExtendedStructureElement::getValidBlock, e -> e)));
            return this;
        }

        // ── Interior elements ─────────────────────────────────────────────────
        //
        // Interior elements are scanned inside the formed volume after each
        // successful full validation. They do not participate in the boundary
        // flood, so their block types must not overlap with boundary element
        // block types.

        public Builder<T> addInteriorElement(IExtendedStructureElement<T> element) {
            interiorElements.computeIfAbsent(element.getValidBlock(), e -> new ArrayList<>())
                .add(element);
            return this;
        }

        // ── Embed ─────────────────────────────────────────────────────────────

        public <D> Builder<T> embedDefinition(String shape, IStructureDefinition<D> definition) {
            if (!(definition instanceof StructureDefinition<D>def)) {
                throw new IllegalArgumentException("Unsupported structure definition");
            }
            String encodedShape = def.getShapes()
                .get(shape);
            if (encodedShape == null) {
                throw new IllegalArgumentException("Unknown shape: " + shape);
            }

            Map<Character, IStructureElement<D>> sourceElements = def.getElements();
            for (char c : encodedShape.toCharArray()) {
                if (c == '+' || c == '-' || c == ' ') continue;
                IStructureElement<D> element = sourceElements.get(c);
                if (element instanceof IExtendedStructureElement<D>el && !element.isNavigating()) {
                    this.elements.put(el.getValidBlock(), (IStructureElement<T>) element);
                } else {
                    Galaxia.LOG.error("Trying to embed invalid structure elements, ignoring it");
                }
            }
            return this;
        }

        public ArbitraryShapeDefinition<T> build() {
            if (enclosed == -1) {
                throw new InvalidParameterException("Must specify if multiblock is open or enclosed");
            }
            return new ArbitraryShapeDefinition<>(elements, interiorElements, searchRadius, enclosed == 1);
        }
    }
}
