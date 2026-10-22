package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongUnaryOperator;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

/** Bounded typed conversion at one physical Computer. Hardware pays for each completed operation. */
public final class ComputerDataProcessor implements SatelliteDataEndpointRegistry.Endpoint {

    private static final long INPUT_BUFFER_TICKS = 2L;
    private final UUID id;
    private final UUID teamId;
    private final CelestialObjectKey bodyKey;
    private final SatelliteDataType inputType;
    private final SatelliteDataType outputType;
    private final Map<SatelliteDataKey, Long> input = new LinkedHashMap<>();
    private long processingCapacity;

    public ComputerDataProcessor(UUID id, UUID teamId, CelestialObjectKey bodyKey, SatelliteDataType inputType,
        SatelliteDataType outputType) {
        this.id = id;
        this.teamId = teamId;
        this.bodyKey = bodyKey;
        this.inputType = inputType;
        this.outputType = outputType;
    }

    public void setProcessingCapacity(long capacity) {
        processingCapacity = Math.max(0L, capacity);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public CelestialObjectKey bodyKey() {
        return bodyKey;
    }

    @Override
    public boolean consumes() {
        return processingCapacity > 0L;
    }

    @Override
    public SatelliteDataKey demandKey() {
        return SatelliteDataKey.any(inputType);
    }

    @Override
    public long amountDeciKb() {
        return Math.max(
            0L,
            INPUT_BUFFER_TICKS * processingCapacity - input.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum());
    }

    @Override
    public long accept(SatelliteDataKey key, long amount) {
        if (!consumes() || amount <= 0L || !key.hasOrigin() || !demandKey().matchesProduced(key)) return 0L;
        long accepted = Math.min(amount, amountDeciKb());
        if (accepted > 0L) input.merge(key, accepted, Long::sum);
        return accepted;
    }

    /** Distributes one controller tick's paid capacity across waiting origins, rotating partial work fairly. */
    public long process(LongUnaryOperator paidOperation) {
        if (!consumes() || input.isEmpty()) return 0L;
        long completed = 0L;
        for (SatelliteDataKey key : List.copyOf(input.keySet())) {
            long available = input.get(key);
            SatelliteDataKey output = SatelliteDataKey.origin(outputType, key.origin());
            long requested = Math.min(
                processingCapacity - completed,
                Math.min(available, SatelliteNetworkService.outputRoom(teamId, bodyKey, output)));
            long processed = requested > 0L ? paidOperation.applyAsLong(requested) : 0L;
            if (processed > 0L) {
                SatelliteNetworkService.offerData(teamId, bodyKey, output, processed);
                completed += processed;
            }
            input.remove(key);
            if (available > processed) input.put(key, available - processed);
            if (completed >= processingCapacity || processed < requested) break;
        }
        return completed;
    }
}
