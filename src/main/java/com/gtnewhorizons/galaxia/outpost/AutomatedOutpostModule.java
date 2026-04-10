package com.gtnewhorizons.galaxia.outpost;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.celestial.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;
import com.gtnewhorizons.galaxia.registry.GTUtility;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectRegistration;
import com.gtnewhorizons.galaxia.registry.celestial.GtOreVeinDefinition;

/**
 * A single module instance installed in an {@link AutomatedOutpostState}.
 */
public final class AutomatedOutpostModule {

    private static final Random RANDOM = new Random();

    public enum Status {
        IN_CONSTRUCTION,
        OPERATIONAL,
        DISABLED
    }

    /** The type of this module. */
    public final OutpostModuleKind kind;

    /** Static configuration data (kind-specific record). */
    private OutpostModuleData data;

    /** Current status of the module. */
    private Status status;

    /** Construction progress (0.0 to 1.0). */
    private float constructionProgress;

    /** Resources already consumed for construction. */
    private final Map<ItemStackWrapper, Integer> consumedResources = new LinkedHashMap<>();

    /** Remaining cooldown ticks before this module may act again. */
    public int cooldownTicks;

    /** Internal energy buffer in EU. */
    public long energyBuffer;

    public AutomatedOutpostModule(OutpostModuleKind kind, OutpostModuleData data) {
        this.kind = kind;
        this.data = data;
        this.status = Status.IN_CONSTRUCTION;
        this.constructionProgress = 0.0f;
        this.cooldownTicks = 0;
        this.energyBuffer = 0L;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOperational() {
        return status == Status.OPERATIONAL;
    }

    public boolean isDisabled() {
        return status == Status.DISABLED;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OutpostModuleData getData() {
        return data;
    }

    public void setData(OutpostModuleData data) {
        this.data = data;
    }

    public float getConstructionProgress() {
        return constructionProgress;
    }

    public void setConstructionProgress(float constructionProgress) {
        this.constructionProgress = constructionProgress;
    }

    public Map<ItemStackWrapper, Integer> getConsumedResources() {
        return consumedResources;
    }

    public void clearConsumedResources() {
        consumedResources.clear();
    }

    public void completeConstructionInstantly() {
        consumedResources.clear();
        for (Map.Entry<ItemStackWrapper, Integer> entry : kind.getRequiredResources()
            .entrySet()) {
            consumedResources.put(entry.getKey(), entry.getValue());
        }
        status = Status.OPERATIONAL;
        constructionProgress = 1.0f;
        cooldownTicks = 0;
    }

    /**
     * Ticks the module logic.
     */
    public void tick(AutomatedOutpostState outpost) {
        if (status == Status.IN_CONSTRUCTION) {
            updateConstruction(outpost);
        } else if (status == Status.OPERATIONAL) {
            updateOperational(outpost);
        }
    }

    private void updateConstruction(AutomatedOutpostState outpost) {
        Map<ItemStackWrapper, Integer> requirements = kind.getRequiredResources();
        boolean finished = true;
        int totalNeeded = 0;
        int totalConsumed = 0;

        for (Map.Entry<ItemStackWrapper, Integer> entry : requirements.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            int required = entry.getValue();
            int consumed = consumedResources.getOrDefault(item, 0);

            totalNeeded += required;
            totalConsumed += consumed;

            if (consumed < required) {
                finished = false;
                // Try to consume from inventory (max 1 per tick for over-time effect)
                if (outpost.inventory.tryConsume(item, 1)) {
                    consumedResources.put(item, consumed + 1);
                    totalConsumed++;
                }
            }
        }

        if (totalNeeded > 0) {
            constructionProgress = (float) totalConsumed / totalNeeded;
        } else {
            constructionProgress = 1.0f;
        }

        if (finished) {
            status = Status.OPERATIONAL;
            constructionProgress = 1.0f;
        }
    }

    private void updateOperational(AutomatedOutpostState outpost) {
        if (kind.powerDrawEuPerTick > 0 && !outpost.tryConsumeEnergy(kind.powerDrawEuPerTick)) {
            return;
        }
        switch (kind) {
            case MINER -> tickMiner(outpost);
            case POWER -> tickPower(outpost);
            default -> {}
        }
    }

    private void tickMiner(AutomatedOutpostState outpost) {
        // Mining Module logic: produce 1 raw ore every 20 ticks
        if (cooldownTicks <= 0) {
            cooldownTicks = 20;

            // Find celestial body
            GalaxiaCelestialAPI.get(outpost.celestialBodyId)
                .ifPresent(registration -> {
                    ItemStack ore = generateOre(registration);
                    if (ore != null) {
                        outpost.inventory.add(ItemStackWrapper.of(ore), 1);
                    }
                });
        }
    }

    private void tickPower(AutomatedOutpostState outpost) {
        outpost.addEnergy(PowerModuleData.GENERATION_EU_PER_TICK);
    }

    private ItemStack generateOre(CelestialObjectRegistration body) {
        MinerModuleData minerData = data instanceof MinerModuleData typed ? typed : new MinerModuleData();
        if (body.properties()
            .usesGtOreVeins()) {
            List<GtOreVeinDefinition> veins = body.properties()
                .gtOreVeins();
            if (!veins.isEmpty()) {
                GtOreVeinDefinition vein = veins.get(RANDOM.nextInt(veins.size()));
                List<String> oreNames = vein.ores();
                if (!oreNames.isEmpty()) {
                    String chosenOre = oreNames.get(RANDOM.nextInt(oreNames.size()));
                    if (chosenOre == null || chosenOre.isEmpty()) return null;
                    if (minerData.isBlacklisted(chosenOre)) return null;
                    ItemStack stack = GTUtility.getRawOreStack(chosenOre);
                    if (stack == null) return null;
                    ItemStack ore = stack.copy();
                    ore.stackSize = 1;
                    return ore;
                }
            }
            return null;
        }

        List<ItemStack> ores = body.properties()
            .ores();
        if (ores.isEmpty()) return null;
        ItemStack chosen = ores.get(RANDOM.nextInt(ores.size()));
        if (chosen == null) return null;
        ItemStackWrapper wrapper = ItemStackWrapper.of(chosen);
        if (wrapper == null || minerData.isBlacklisted(wrapper.toKey())) return null;

        ItemStack ore = chosen.copy();
        ore.stackSize = 1;
        return ore;
    }

    public long getDisplayedPowerEuPerTick() {
        if (status != Status.OPERATIONAL) return 0L;
        if (kind == OutpostModuleKind.POWER) return -PowerModuleData.GENERATION_EU_PER_TICK;
        return kind.powerDrawEuPerTick;
    }
}
