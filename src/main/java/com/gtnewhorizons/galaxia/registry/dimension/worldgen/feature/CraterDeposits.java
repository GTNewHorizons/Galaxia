package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/** Dimension-local authority for crater identity, spatial binding and successful extraction accounting. */
public final class CraterDeposits extends WorldSavedData {

    private static final String DATA_NAME = "galaxia_crater_deposits";
    private final Map<String, Deposit> deposits = new LinkedHashMap<>();
    private final Map<Long, List<String>> chunks = new LinkedHashMap<>();
    private final Map<String, Operation> operations = new LinkedHashMap<>();

    public CraterDeposits() {
        super(DATA_NAME);
    }

    public CraterDeposits(String name) {
        super(name);
    }

    public static CraterDeposits get(World world) {
        CraterDeposits data = (CraterDeposits) world.perWorldStorage.loadData(CraterDeposits.class, DATA_NAME);
        if (data == null) {
            data = new CraterDeposits();
            world.perWorldStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    /**
     * Repeated generation of neighboring chunks never replaces a crater's established state.
     *
     * @throws IllegalArgumentException if the diameter is below two blocks or the reserve is empty
     */
    public void register(String id, int x, int z, int diameter, CraterSize size, CraterProfile profile, long reserve) {
        if (diameter < 2 || reserve < 1) throw new IllegalArgumentException("Invalid crater dimensions or reserve");
        if (deposits.containsKey(id)) return;
        Deposit deposit = new Deposit(id, x, z, diameter, size, profile, reserve);
        deposits.put(id, deposit);
        index(deposit);
        markDirty();
    }

    /** The index narrows candidates before exact circular-footprint membership is evaluated. */
    public String find(int x, int z) {
        for (String id : chunks.getOrDefault(chunkKey(x >> 4, z >> 4), List.of())) {
            Deposit deposit = deposits.get(id);
            double dx = (double) x - deposit.x;
            double dz = (double) z - deposit.z;
            double radius = deposit.diameter / 2.0;
            if (dx * dx + dz * dz <= radius * radius) return id;
        }
        return null;
    }

    public Snapshot inspect(String id) {
        Deposit deposit = deposits.get(id);
        if (deposit == null) return null;
        return new Snapshot(
            deposit.profile,
            deposit.size,
            deposit.diameter,
            deposit.initialReserve,
            deposit.remaining,
            deposit.fracking,
            deposit.profile.drillingFluidPerOperation(deposit.fracking));
    }

    /**
     * Reserves an ordinary portion without changing the remaining reserve. Fracking uses completed history at
     * admission.
     * Concurrent fracking operations may share an admission price. Only their successful completions advance history.
     */
    public Operation begin(String craterId, boolean wet, boolean doubleDryCost) {
        Deposit deposit = deposits.get(craterId);
        if (deposit == null) return null;
        boolean fracking = deposit.remaining == 0;
        if (fracking && !wet) return null;
        long available = deposit.remaining - deposit.reserved;
        if (!fracking && available == 0) return null;
        long cost = fracking ? 0 : Math.min(available, !wet && doubleDryCost ? 2 : 1);
        long fluid = wet ? (fracking ? deposit.profile.drillingFluidPerOperation(deposit.fracking) : 16000) : 0;
        Operation operation = new Operation(
            UUID.randomUUID()
                .toString(),
            craterId,
            cost,
            fluid,
            fracking,
            wet);
        operations.put(operation.id, operation);
        deposit.reserved += cost;
        markDirty();
        return operation;
    }

    public Operation operation(String id) {
        return operations.get(id);
    }

    /**
     * Native failure releases the reservation. Successful completion commits the operation once.
     */
    public boolean finish(String operationId, boolean success) {
        Operation operation = operations.remove(operationId);
        if (operation == null) return false;
        Deposit deposit = deposits.get(operation.craterId);
        deposit.reserved -= operation.reserveCost;
        if (success) {
            deposit.remaining -= operation.reserveCost;
            if (operation.fracking) deposit.fracking++;
        }
        markDirty();
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        deposits.clear();
        chunks.clear();
        operations.clear();
        NBTTagList saved = nbt.getTagList("deposits", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < saved.tagCount(); i++) {
            NBTTagCompound entry = saved.getCompoundTagAt(i);
            Deposit deposit = new Deposit(
                entry.getString("id"),
                entry.getInteger("x"),
                entry.getInteger("z"),
                entry.getInteger("diameter"),
                CraterSize.valueOf(entry.getString("size")),
                CraterProfile.valueOf(entry.getString("profile")),
                entry.getLong("initial"));
            deposit.remaining = entry.getLong("remaining");
            deposit.fracking = entry.getLong("fracking");
            deposits.put(deposit.id, deposit);
            index(deposit);
        }
        NBTTagList pending = nbt.getTagList("operations", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < pending.tagCount(); i++) {
            NBTTagCompound entry = pending.getCompoundTagAt(i);
            Operation operation = new Operation(
                entry.getString("id"),
                entry.getString("crater"),
                entry.getLong("reserve"),
                entry.getLong("fluid"),
                entry.getBoolean("fracking"),
                entry.getBoolean("wet"));
            operations.put(operation.id, operation);
            deposits.get(operation.craterId).reserved += operation.reserveCost;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList saved = new NBTTagList();
        for (Deposit deposit : deposits.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("id", deposit.id);
            entry.setInteger("x", deposit.x);
            entry.setInteger("z", deposit.z);
            entry.setInteger("diameter", deposit.diameter);
            entry.setString("size", deposit.size.name());
            entry.setString("profile", deposit.profile.name());
            entry.setLong("initial", deposit.initialReserve);
            entry.setLong("remaining", deposit.remaining);
            entry.setLong("fracking", deposit.fracking);
            saved.appendTag(entry);
        }
        nbt.setTag("deposits", saved);
        NBTTagList pending = new NBTTagList();
        for (Operation operation : operations.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("id", operation.id);
            entry.setString("crater", operation.craterId);
            entry.setLong("reserve", operation.reserveCost);
            entry.setLong("fluid", operation.drillingFluid);
            entry.setBoolean("fracking", operation.fracking);
            entry.setBoolean("wet", operation.wet);
            pending.appendTag(entry);
        }
        nbt.setTag("operations", pending);
    }

    private void index(Deposit deposit) {
        int radius = (deposit.diameter + 1) / 2;
        for (int cx = (deposit.x - radius) >> 4; cx <= (deposit.x + radius) >> 4; cx++) {
            for (int cz = (deposit.z - radius) >> 4; cz <= (deposit.z + radius) >> 4; cz++) {
                chunks.computeIfAbsent(chunkKey(cx, cz), key -> new ArrayList<>())
                    .add(deposit.id);
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    public record Snapshot(CraterProfile profile, CraterSize size, int diameter, long initialReserve,
        long remainingReserve, long completedFracking, long nextFrackingFluid) {}

    public record Operation(String id, String craterId, long reserveCost, long drillingFluid, boolean fracking,
        boolean wet) {}

    private static final class Deposit {

        private final String id;
        private final int x;
        private final int z;
        private final int diameter;
        private final CraterSize size;
        private final CraterProfile profile;
        private final long initialReserve;
        private long remaining;
        private long fracking;
        private long reserved;

        private Deposit(String id, int x, int z, int diameter, CraterSize size, CraterProfile profile, long reserve) {
            this.id = id;
            this.x = x;
            this.z = z;
            this.diameter = diameter;
            this.size = size;
            this.profile = profile;
            this.initialReserve = reserve;
            this.remaining = reserve;
        }
    }
}
