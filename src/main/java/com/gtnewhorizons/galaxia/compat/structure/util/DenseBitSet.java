package com.gtnewhorizons.galaxia.compat.structure.util;

import java.util.Arrays;

public final class DenseBitSet {

    @FunctionalInterface
    public interface CoordConsumer {

        void accept(int lx, int ly, int lz);
    }

    // spotless: off
    // Assume 4KB pages (too bad if you are on macOS)
    private static final int PAGE_SHIFT = 12;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final int WORDS_PER_PAGE = PAGE_SIZE >>> 6;
    private static final int WORD_SHIFT = PAGE_SHIFT - 6;

    // 2-Level paging: blocks group pages to limit upfront allocation
    private static final int BLOCK_SHIFT = 8;
    private static final int PAGES_PER_BLOCK = 1 << BLOCK_SHIFT;

    // Batch-allocate this many aligned pages at once in a block
    private static final int ALLOC_BATCH = 4;

    // Sentinel for "page not yet allocated" in the page table.
    // 0xFFFF is used so that all usable slab indices fit in [0, 65534]
    // via unsigned interpretation (& 0xFFFF). This gives 65535 usable
    // slots rather than the 32767 a signed short would allow.
    // Max supported pages = 65534; enforced in the constructor.
    private static final short UNALLOCATED = (short) 0xFFFF;

    // ---- Page table: short[block][pageInBlock] = slabIndex ----
    // short indices (2 bytes) vs int (4 bytes): 2× denser page table
    // → fits in half as many cache lines. Reads use (& 0xFFFF) for
    // unsigned interpretation; writes store raw short bits directly.
    private final short[][] pageTable;

    // ---- Flat slab: all page data in one contiguous long[] ----
    // slab[slabIdx << WORD_SHIFT .. (slabIdx+1 << WORD_SHIFT) - 1] = one page.
    // Batch-allocated pages get consecutive slab indices, so they land
    // adjacent in memory and are prefetched together on sequential access.
    private long[] slab;
    private int slabUsed; // pages currently committed in the slab
    // spotless: on

    private final int minX, minY, minZ;
    private final int lenX, lenY, lenZ;
    private final long strideY;
    private final long strideX;
    private final long totalBits;

    private int size;
    private int cachedPageIdx = -1;
    private int cachedSlabBase;

    public DenseBitSet(int minX, int minY, int minZ, int lenX, int lenY, int lenZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.lenX = lenX;
        this.lenY = lenY;
        this.lenZ = lenZ;

        this.strideY = lenZ;
        this.strideX = (long) lenY * lenZ;
        this.totalBits = (long) lenX * strideX;

        long totalPages = (totalBits + PAGE_SIZE - 1) >>> PAGE_SHIFT;
        if (totalPages > 0xFFFE) throw new IllegalArgumentException(
            "Grid too large for short indices: " + totalPages + " pages needed, max 65534");
        int numBlocks = (int) ((totalPages + PAGES_PER_BLOCK - 1) >>> BLOCK_SHIFT);

        this.pageTable = new short[numBlocks][];
        int initCap = (int) Math.min(64L, totalPages);
        this.slab = new long[initCap << WORD_SHIFT];
        this.slabUsed = 0;
    }

    public boolean add(int lx, int ly, int lz) {
        long idx = index(lx, ly, lz);

        int pageIdx = (int) (idx >>> PAGE_SHIFT);
        int blockIdx = pageIdx >>> BLOCK_SHIFT;
        int pageInBlock = pageIdx & (PAGES_PER_BLOCK - 1);
        int slabBase;

        if (pageIdx == cachedPageIdx) {
            slabBase = cachedSlabBase;
        } else {
            short[] block = pageTable[blockIdx];
            if (block == null) {
                block = new short[PAGES_PER_BLOCK];
                Arrays.fill(block, UNALLOCATED);
                pageTable[blockIdx] = block;
            }

            if (block[pageInBlock] == UNALLOCATED) {
                // Batch-allocate ALLOC_BATCH aligned pages at once.
                // Because allocPage() is called sequentially, these pages receive
                // consecutive slab indices → their data is contiguous in the slab.
                int batchStart = pageInBlock & -ALLOC_BATCH;
                for (int i = 0; i < ALLOC_BATCH; i++) {
                    int pIdx = batchStart + i;
                    if (block[pIdx] == UNALLOCATED) {
                        block[pIdx] = (short) allocPage();
                    }
                }
            }

            // Unsigned read: & 0xFFFF reinterprets the short bits as [0, 65534].
            slabBase = (block[pageInBlock] & 0xFFFF) << WORD_SHIFT;

            cachedPageIdx = pageIdx;
            cachedSlabBase = slabBase;
        }

        int wordInPage = (int) ((idx >>> 6) & (WORDS_PER_PAGE - 1));
        long mask = 1L << (idx & 63);

        if ((slab[slabBase + wordInPage] & mask) != 0L) return false;

        slab[slabBase + wordInPage] |= mask;
        size++;
        return true;
    }

    public boolean containsChecked(int x, int y, int z) {
        return inBounds(x, y, z) && contains(x, y, z);
    }

    public boolean contains(int lx, int ly, int lz) {
        long idx = index(lx, ly, lz);

        int pageIdx = (int) (idx >>> PAGE_SHIFT);
        int blockIdx = pageIdx >>> BLOCK_SHIFT;
        if (blockIdx >= pageTable.length) return false;

        short[] block = pageTable[blockIdx];
        if (block == null) return false;

        short rawIdx = block[pageIdx & (PAGES_PER_BLOCK - 1)];
        if (rawIdx == UNALLOCATED) return false;

        int slabBase = (rawIdx & 0xFFFF) << WORD_SHIFT;
        int wordInPage = (int) ((idx >>> 6) & (WORDS_PER_PAGE - 1));
        return (slab[slabBase + wordInPage] & (1L << (idx & 63))) != 0L;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(slab, 0, slabUsed << WORD_SHIFT, 0L);
        cachedPageIdx = -1;
        size = 0;
    }

    public void forEach(CoordConsumer consumer) {
        for (int bi = 0; bi < pageTable.length; bi++) {
            short[] block = pageTable[bi];
            if (block == null) continue;

            for (int pi = 0; pi < block.length; pi++) {
                short rawIdx = block[pi];
                if (rawIdx == UNALLOCATED) continue;

                long baseIdx = (((long) bi << BLOCK_SHIFT) | pi) << PAGE_SHIFT;
                int slabBase = (rawIdx & 0xFFFF) << WORD_SHIFT;

                for (int wi = 0; wi < WORDS_PER_PAGE; wi++) {
                    long word = slab[slabBase + wi];
                    while (word != 0L) {
                        int bit = Long.numberOfTrailingZeros(word);
                        long idx = baseIdx + ((long) wi << 6) + bit;

                        int lx = (int) compact(idx) + minX;
                        int ly = (int) compact(idx >>> 1) + minY;
                        int lz = (int) compact(idx >>> 2) + minZ;

                        // Guard against Morton slots that fall outside the declared grid.
                        // Cannot use idx >= totalBits: Morton indices of valid coords can
                        // greatly exceed lenX*lenY*lenZ for non-cubic grids.
                        if (lx < minX + lenX && ly < minY + lenY && lz < minZ + lenZ) {
                            consumer.accept(lx, ly, lz);
                        }
                        word &= word - 1L;
                    }
                }
            }
        }
    }

    /** 3D Morton encoding to improve locality when flood-filling **/
    private static long spread(long v) {
        v &= 0x1fffffL;
        v = (v | (v << 32)) & 0x1f00000000ffffL;
        v = (v | (v << 16)) & 0x1f0000ff0000ffL;
        v = (v | (v << 8)) & 0x100f00f00f00f00fL;
        v = (v | (v << 4)) & 0x10c30c30c30c30c3L;
        v = (v | (v << 2)) & 0x1249249249249249L;
        return v;
    }

    private static long compact(long v) {
        v &= 0x1249249249249249L;
        v = (v | (v >> 2)) & 0x10c30c30c30c30c3L;
        v = (v | (v >> 4)) & 0x100f00f00f00f00fL;
        v = (v | (v >> 8)) & 0x1f0000ff0000ffL;
        v = (v | (v >> 16)) & 0x1f00000000ffffL;
        v = (v | (v >> 32)) & 0x1fffffL;
        return v;
    }

    private long index(int lx, int ly, int lz) {
        return spread(lx - minX) | (spread(ly - minY) << 1) | (spread(lz - minZ) << 2);
    }

    /** Commits the next page slot in the slab (growing it if necessary). */
    private int allocPage() {
        int slabIdx = slabUsed++;
        int neededWords = slabUsed << WORD_SHIFT;
        if (neededWords > slab.length) {
            slab = Arrays.copyOf(slab, Math.max(neededWords, slab.length * 2));
        }
        return slabIdx;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= minX && x < minX + lenX && y >= minY && y < minY + lenY && z >= minZ && z < minZ + lenZ;
    }
}
