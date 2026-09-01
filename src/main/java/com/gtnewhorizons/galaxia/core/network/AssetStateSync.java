package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class AssetStateSync {

    private static final long NO_PUBLICATION = 0L;
    private static final long PUBLICATION_INCREMENT = 1L;
    private static final Logger LOG = LogManager.getLogger(AssetStateSync.class);

    public static final Server SERVER = new Server(new ForgeServerTransport());
    public static final Client CLIENT = new Client(
        assetId -> Galaxia.GALAXIA_NETWORK.sendToServer(new AssetStateResyncRequestPacket(assetId)));

    private AssetStateSync() {}

    private static byte[] encodeProjection(AssetSyncPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static AssetSyncPacket publicationForRecipient(AssetSyncPacket full, AssetSyncPacket replacement,
        long baseRevision, long publishedRevision, boolean projectionChanged) {
        if (!projectionChanged && baseRevision > NO_PUBLICATION) return null;
        return projectionChanged && baseRevision > NO_PUBLICATION
            && baseRevision == publishedRevision - PUBLICATION_INCREMENT ? replacement : full;
    }

    public static final class Server {

        private final ServerTransport transport;
        private final Map<CelestialAsset.ID, Long> publishedRevisions = new LinkedHashMap<>();
        private final Map<RecipientAsset, Long> recipientCursors = new LinkedHashMap<>();
        private final Map<CelestialAsset.ID, byte[]> projectionFingerprints = new LinkedHashMap<>();

        Server(ServerTransport transport) {
            this.transport = transport;
        }

        public void publishInteractive(CelestialAsset.ID assetId) {
            CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(assetId);
            if (asset == null) return;
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            if (teamId == null) return;
            AssetSyncPacket full = AssetSyncPacket.fullSync(asset);
            publish(asset, teamId, full, encodeProjection(full), true);
        }

        public void publishPeriodic() {
            for (CelestialAsset asset : CelestialAssetStore.SERVER.allAssetsInternal()) {
                publishPeriodic(asset);
            }
        }

        private void publishPeriodic(CelestialAsset asset) {
            AssetSyncPacket full = AssetSyncPacket.fullSync(asset);
            byte[] fingerprint = encodeProjection(full);
            byte[] previous = projectionFingerprints.get(asset.assetId);
            boolean projectionChanged = !Arrays.equals(previous, fingerprint);
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(asset.assetId);
            if (teamId != null) {
                if (projectionChanged && previous != null && !asset.isDirty()) {
                    LOG.warn("Asset {} projection changed without dirty state; publishing recovery", asset.assetId);
                }
                publish(asset, teamId, full, fingerprint, projectionChanged);
            }
            asset.clean();
        }

        public void publishFullTo(UUID recipientId, CelestialAsset.ID assetId) {
            CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(assetId);
            if (asset == null) return;
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            if (teamId == null || !transport.eligibleRecipients(teamId)
                .contains(recipientId)) return;
            long publishedRevision = publishedRevisions.getOrDefault(assetId, NO_PUBLICATION);
            if (publishedRevision == NO_PUBLICATION) {
                publishedRevision = publishedRevisions.merge(assetId, PUBLICATION_INCREMENT, Long::sum);
            }
            AssetSyncPacket full = AssetSyncPacket.fullSync(asset);
            if (send(recipientId, full.withPublishedRevision(NO_PUBLICATION, publishedRevision))) {
                recipientCursors.put(new RecipientAsset(recipientId, assetId), publishedRevision);
            }
        }

        public boolean destroyAsset(CelestialAsset.ID assetId) {
            if (assetId == null) return false;
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            if (teamId == null) return false;
            if (!CelestialAssetStore.SERVER.destroyAssetInternal(assetId)) return false;
            publishRemoved(teamId, assetId);
            return true;
        }

        public void publishRemoved(UUID teamId, CelestialAsset.ID assetId) {
            if (teamId == null || assetId == null) return;
            long publishedRevision = publishedRevisions.merge(assetId, PUBLICATION_INCREMENT, Long::sum);
            AssetSyncPacket removal = AssetSyncPacket.assetRemoved(assetId)
                .withPublishedRevision(NO_PUBLICATION, publishedRevision);
            for (UUID recipientId : transport.eligibleRecipients(teamId)) {
                if (recipientId != null) send(recipientId, removal);
            }
            publishedRevisions.remove(assetId);
            projectionFingerprints.remove(assetId);
            recipientCursors.keySet()
                .removeIf(key -> assetId.equals(key.assetId()));
        }

        private void publish(CelestialAsset asset, UUID teamId, AssetSyncPacket full, byte[] fingerprint,
            boolean projectionChanged) {
            CelestialAsset.ID assetId = asset.assetId;
            AssetSyncPacket replacement = AssetSyncPacket.stateReplacement(asset);
            long publishedRevision = projectionChanged
                ? publishedRevisions.merge(assetId, PUBLICATION_INCREMENT, Long::sum)
                : publishedRevisions.getOrDefault(assetId, NO_PUBLICATION);
            if (publishedRevision == NO_PUBLICATION) return;
            for (UUID recipientId : transport.eligibleRecipients(teamId)) {
                if (recipientId == null) continue;
                RecipientAsset cursorKey = new RecipientAsset(recipientId, assetId);
                long baseRevision = recipientCursors.getOrDefault(cursorKey, NO_PUBLICATION);
                AssetSyncPacket publication = publicationForRecipient(
                    full,
                    replacement,
                    baseRevision,
                    publishedRevision,
                    projectionChanged);
                if (publication == null) continue;
                if (send(recipientId, publication.withPublishedRevision(baseRevision, publishedRevision))) {
                    recipientCursors.put(cursorKey, publishedRevision);
                }
            }
            projectionFingerprints.put(assetId, fingerprint);
        }

        public void resetRecipient(UUID recipientId) {
            if (recipientId == null) return;
            recipientCursors.keySet()
                .removeIf(key -> recipientId.equals(key.recipientId()));
            send(recipientId, AssetSyncPacket.clear());
        }

        public static List<AssetStateFramePacket> frame(AssetSyncPacket packet) {
            if (packet == null) throw new IllegalArgumentException("Asset update is required");
            ByteBuf buffer = Unpooled.buffer();
            try {
                packet.toBytes(buffer);
                int totalSize = buffer.readableBytes();
                if (totalSize < 1 || totalSize > AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES) {
                    throw new IllegalArgumentException("Asset update exceeds the bounded framing limit: " + totalSize);
                }
                int frameCount = (totalSize + AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES - 1)
                    / AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES;
                UUID updateId = UUID.randomUUID();
                List<AssetStateFramePacket> frames = new ArrayList<>(frameCount);
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    int payloadLength = Math.min(
                        AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES,
                        totalSize - frameIndex * AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES);
                    byte[] payload = new byte[payloadLength];
                    buffer.readBytes(payload);
                    frames.add(
                        new AssetStateFramePacket(
                            packet.assetId(),
                            updateId,
                            frameIndex,
                            frameCount,
                            totalSize,
                            payload));
                }
                return List.copyOf(frames);
            } finally {
                buffer.release();
            }
        }

        private boolean send(UUID recipientId, AssetSyncPacket packet) {
            try {
                for (AssetStateFramePacket frame : frame(packet)) transport.send(recipientId, frame);
                return true;
            } catch (RuntimeException ex) {
                LOG.warn(
                    "Rejected oversized or malformed asset publication for {}: {}",
                    packet.assetId(),
                    ex.getMessage());
                return false;
            }
        }

        private record RecipientAsset(UUID recipientId, CelestialAsset.ID assetId) {}
    }

    public static final class Client {

        static final long ASSEMBLY_TIMEOUT_MILLIS = 30_000L;
        private static final long NO_PENDING_ASSEMBLY_BYTES = 0L;
        private static final long MAX_PENDING_ASSEMBLY_BYTES = 32L * 1024L * 1024L;

        private final ClientTransport transport;
        private final LongSupplier currentTimeMillis;
        private final Map<CelestialAsset.ID, Long> appliedRevisions = new LinkedHashMap<>();
        private final Set<CelestialAsset.ID> pendingRecovery = new LinkedHashSet<>();
        private final Map<AssemblyKey, Assembly> assemblies = new LinkedHashMap<>();
        private long pendingAssemblyBytes;

        Client(ClientTransport transport) {
            this(transport, System::currentTimeMillis);
        }

        Client(ClientTransport transport, LongSupplier currentTimeMillis) {
            this.transport = transport;
            this.currentTimeMillis = currentTimeMillis;
        }

        public void receive(AssetStateFramePacket frame) {
            expireAssemblies();
            if (frame == null) return;
            AssemblyKey key = new AssemblyKey(frame.assetId(), frame.updateId());
            if (!frame.isValid()) {
                reject(key, frame.assetId());
                return;
            }
            Assembly assembly = assemblies.get(key);
            if (assembly == null) {
                if (pendingAssemblyBytes + frame.declaredTotalSize() > MAX_PENDING_ASSEMBLY_BYTES) {
                    requestRecovery(frame.assetId());
                    return;
                }
                assembly = new Assembly(frame, currentTimeMillis.getAsLong());
                assemblies.put(key, assembly);
                pendingAssemblyBytes += frame.declaredTotalSize();
            } else if (!assembly.hasCompatibleMetadata(frame)) {
                reject(key, frame.assetId());
                return;
            }
            if (!assembly.accept(frame)) {
                reject(key, frame.assetId());
                return;
            }
            if (!assembly.isComplete()) return;

            byte[] encoded = assembly.join();
            removeAssembly(key);
            AssetSyncPacket packet = decode(encoded, frame.assetId());
            if (packet == null) {
                requestRecovery(frame.assetId());
                return;
            }
            discardAssemblies(frame.assetId());
            apply(packet);
        }

        public void tick() {
            expireAssemblies();
        }

        public void clear() {
            appliedRevisions.clear();
            pendingRecovery.clear();
            assemblies.clear();
            pendingAssemblyBytes = NO_PENDING_ASSEMBLY_BYTES;
        }

        public void removeAsset(CelestialAsset.ID assetId) {
            appliedRevisions.remove(assetId);
            pendingRecovery.remove(assetId);
            discardAssemblies(assetId);
        }

        private void apply(AssetSyncPacket packet) {
            switch (packet.syncType()) {
                case AssetSyncPacket.CLEAR -> ClientStateLifecycle.clearAll();
                case AssetSyncPacket.ASSET_REMOVED -> {
                    CelestialAssetStore.CLIENT.destroyAssetInternal(packet.assetId());
                    removeAsset(packet.assetId());
                }
                case AssetSyncPacket.FULL_SYNC -> applyFull(packet);
                case AssetSyncPacket.STATE_REPLACEMENT -> applyReplacement(packet);
                default -> requestRecovery(packet.assetId());
            }
        }

        static boolean handleFull(AssetSyncPacket packet) {
            CelestialAsset current = CelestialAssetStore.CLIENT.findAssetInternal(packet.assetId);
            CelestialAsset prepared;
            try {
                prepared = prepareFull(packet);
                validateCompatibleIdentity(current, packet);
            } catch (RuntimeException ex) {
                LOG.warn("Rejected full asset sync for {}: {}", packet.assetId, ex.getMessage());
                return false;
            }

            if (current == null) {
                if (prepared instanceof Station) {
                    CelestialClient.add(prepared);
                } else {
                    CelestialAssetStore.CLIENT.registerAssetInternal(packet.teamId, prepared);
                }
                return true;
            }

            AssetSyncPacket normalized = AssetSyncPacket.fullSync(prepared);
            applyFullState(current, normalized);
            return true;
        }

        private static CelestialAsset prepareFull(AssetSyncPacket packet) {
            validateRequiredIdentity(packet);
            CelestialAsset prepared = createEmptyAsset(packet);
            applyFullState(prepared, packet);
            return prepared;
        }

        private static void validateRequiredIdentity(AssetSyncPacket packet) {
            if (packet == null) throw new IllegalArgumentException("missing asset state");
            if (packet.assetId == null) throw new IllegalArgumentException("missing asset id");
            if (packet.assetKind == null) throw new IllegalArgumentException("missing asset kind");
            if (packet.celestialBodyKey == null) throw new IllegalArgumentException("missing celestial body");
            if (packet.assetStatus == null) throw new IllegalArgumentException("missing asset status");
        }

        private static CelestialAsset createEmptyAsset(AssetSyncPacket packet) {
            return switch (packet.assetKind) {
                case STATION -> new Station(packet.assetId, packet.celestialBodyKey, packet.assetStatus);
                case AUTOMATED_OUTPOST, AUTOMATED_STATION -> CelestialAsset
                    .create(packet.assetId, packet.celestialBodyKey, packet.assetKind, packet.assetStatus);
                case SATELLITE -> {
                    if (packet.satelliteKind == null) throw new IllegalArgumentException("missing satellite kind");
                    yield new Satellite(
                        packet.assetId,
                        packet.celestialBodyKey,
                        packet.assetStatus,
                        packet.satelliteKind);
                }
            };
        }

        private static void validateCompatibleIdentity(CelestialAsset current, AssetSyncPacket packet) {
            if (current == null) return;
            validateCompatibleAssetIdentity(current, packet);
            validateCompatibleTeam(current, packet);
            validateCompatibleSatelliteKind(current, packet);
        }

        private static void validateCompatibleAssetIdentity(CelestialAsset current, AssetSyncPacket packet) {
            if (current.kind != packet.assetKind
                || !Objects.equals(current.celestialObjectKey, packet.celestialBodyKey)) {
                throw new IllegalArgumentException("incompatible asset identity");
            }
        }

        private static void validateCompatibleTeam(CelestialAsset current, AssetSyncPacket packet) {
            UUID currentTeam = CelestialAssetStore.CLIENT.getTeamIdInternal(current.assetId);
            if (currentTeam != null && packet.teamId != null && !currentTeam.equals(packet.teamId)) {
                throw new IllegalArgumentException("incompatible asset team");
            }
        }

        private static void validateCompatibleSatelliteKind(CelestialAsset current, AssetSyncPacket packet) {
            if (current instanceof Satellite satellite && satellite.satelliteKind() != packet.satelliteKind) {
                throw new IllegalArgumentException("incompatible satellite kind");
            }
        }

        private static void applyFullState(CelestialAsset asset, AssetSyncPacket packet) {
            switch (packet.assetKind) {
                case STATION -> applyStationFullState(asset, packet);
                case AUTOMATED_OUTPOST, AUTOMATED_STATION -> applyFacilityFullState(asset, packet);
                case SATELLITE -> validateSatelliteFullState(asset, packet);
            }

            if (packet.displayName != null && !packet.displayName.isBlank()) {
                asset.setDisplayName(packet.displayName);
            }
            asset.updateStatus(packet.assetStatus);
            asset.setStateRevision(packet.stateRevision);
        }

        private static void applyStationFullState(CelestialAsset asset, AssetSyncPacket packet) {
            if (!(asset instanceof Station station)) throw new IllegalArgumentException("wrong station state");
            station.setController(packet.stationControllerPos);
            station.logisticsConfig.clear();
            for (AssetSyncPacket delta : packet.fullSyncDeltas) {
                handleDelta(station, delta);
            }
        }

        private static void applyFacilityFullState(CelestialAsset asset, AssetSyncPacket packet) {
            if (!(asset instanceof AutomatedFacility state)) {
                throw new IllegalArgumentException("wrong automated facility state");
            }
            state.setEnergyStored(packet.energyStored);
            state.setStationFeatureSalt(packet.stationFeatureSalt);
            state.loadUpkeepCredits(packet.upkeepCredits);
            state.clearModules();
            state.settingsGroups()
                .clear();
            state.clear();
            state.logisticsConfig.clear();
            StationLayout layout = state.stationLayout();
            if (layout != null) layout.loadFromSnapshot(Collections.emptyMap());
            for (AssetSyncPacket delta : packet.fullSyncDeltas) {
                handleDelta(state, delta);
            }
        }

        private static void validateSatelliteFullState(CelestialAsset asset, AssetSyncPacket packet) {
            if (!(asset instanceof Satellite satellite) || satellite.satelliteKind() != packet.satelliteKind) {
                throw new IllegalArgumentException("wrong satellite state");
            }
        }

        static void handleDelta(CelestialAsset asset, AssetSyncPacket packet) {
            switch (packet.syncType) {
                case AssetSyncPacket.MODULE_ADDED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                    } else {
                        state.addModule(packet.moduleData);
                    }
                    StationLayout layout = state.stationLayout();
                    ModuleInstance module = packet.moduleData;
                    if (layout != null && module.anchorOrNull() != null) {
                        layout.place(module);
                    }
                    syncModuleGroupMembership(state, module);
                }
                case AssetSyncPacket.INVENTORY_UPDATE -> {
                    if (packet.resource != null && asset instanceof IDistributedInventory inventory) {
                        inventory.updateContents(packet.resource, packet.inventoryDelta);
                    }
                }
                case AssetSyncPacket.INVENTORY_BOUNDS_SNAPSHOT -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong inventory bounds packet target");
                    }
                    if (packet.inventoryBoundSnapshot != null) {
                        for (Map.Entry<InventoryKey, InventoryBounds> e : packet.inventoryBoundSnapshot.entrySet()) {
                            InventoryKey key = e.getKey();
                            InventoryBounds bounds = e.getValue();
                            if (bounds.hasLow()) {
                                state.setBound(key, bounds.low(), true);
                            }
                            if (bounds.hasUpper()) {
                                state.setBound(key, bounds.upper(), false);
                            }
                        }
                    }
                }
                case AssetSyncPacket.LOGISTICS_CONFIG_UPDATED -> {
                    if (packet.resource != null) {
                        asset.logisticsConfig.set(packet.resource, packet.logConfig);
                    }
                }
                case AssetSyncPacket.LAYOUT_TILE_UPDATED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    ModuleInstance module = findModuleById(state, packet.tileModuleId);
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.place(packet.tileCoord, new PlacedTile(module, packet.tileState));
                }
                case AssetSyncPacket.SETTINGS_GROUP_UPDATED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    state.settingsGroups()
                        .sync(
                            packet.settingsGroupId,
                            packet.settingsGroupKind,
                            packet.settingsGroupName,
                            packet.settingsGroupJoinable,
                            AssetSyncPacket.copySettingsGroupPayload(packet.settingsGroupSettings));
                    state.applySettingsGroupsToModules();
                }
                case AssetSyncPacket.FILTER_UPDATED -> {
                    if (asset instanceof AutomatedFacility af) af.setFilters(packet.filterItems, packet.filterItem);
                }
                default -> throw new IllegalArgumentException(
                    "Unsupported full asset state delta type: " + packet.syncType);
            }
        }

        static ModuleInstance findModuleById(AutomatedFacility state, ModuleInstance.ID id) {
            if (id == null) return null;
            for (ModuleInstance module : state.modules()) {
                if (module.id.equals(id)) return module;
            }
            return null;
        }

        private static void syncModuleGroupMembership(AutomatedFacility state, ModuleInstance module) {
            if (module.groupId() == 0 || module.anchorOrNull() == null) return;
            SettingsGroup group = state.settingsGroups()
                .get(module.groupId());
            if (group == null) {
                throw new IllegalStateException(
                    "Client received module " + module.id + " for missing settings group " + module.groupId());
            }
            if (!group.members()
                .contains(module.anchorOrNull())) {
                state.settingsGroups()
                    .addMember(module.groupId(), module.anchor());
            }
        }

        private void applyFull(AssetSyncPacket packet) {
            if (!handleFull(packet)) {
                requestRecovery(packet.assetId());
                return;
            }
            appliedRevisions.put(packet.assetId(), packet.publishedRevision());
            pendingRecovery.remove(packet.assetId());
        }

        private void applyReplacement(AssetSyncPacket packet) {
            long appliedRevision = appliedRevisions.getOrDefault(packet.assetId(), NO_PUBLICATION);
            if (packet.publishedRevision() <= appliedRevision) return;
            if (packet.basePublishedRevision() != appliedRevision) {
                requestRecovery(packet.assetId());
                return;
            }
            if (!handleFull(packet)) {
                requestRecovery(packet.assetId());
                return;
            }
            appliedRevisions.put(packet.assetId(), packet.publishedRevision());
        }

        private void requestRecovery(CelestialAsset.ID assetId) {
            if (assetId != null && pendingRecovery.add(assetId)) transport.requestFull(assetId);
        }

        private void expireAssemblies() {
            long now = currentTimeMillis.getAsLong();
            Iterator<Map.Entry<AssemblyKey, Assembly>> iterator = assemblies.entrySet()
                .iterator();
            while (iterator.hasNext()) {
                Map.Entry<AssemblyKey, Assembly> entry = iterator.next();
                if (now - entry.getValue().createdAtMillis <= ASSEMBLY_TIMEOUT_MILLIS) continue;
                pendingAssemblyBytes -= entry.getValue().declaredTotalSize;
                iterator.remove();
                requestRecovery(
                    entry.getKey()
                        .assetId());
            }
        }

        private void reject(AssemblyKey key, CelestialAsset.ID assetId) {
            removeAssembly(key);
            requestRecovery(assetId);
        }

        private void removeAssembly(AssemblyKey key) {
            Assembly removed = assemblies.remove(key);
            if (removed != null) pendingAssemblyBytes -= removed.declaredTotalSize;
        }

        private void discardAssemblies(CelestialAsset.ID assetId) {
            Iterator<Map.Entry<AssemblyKey, Assembly>> iterator = assemblies.entrySet()
                .iterator();
            while (iterator.hasNext()) {
                Map.Entry<AssemblyKey, Assembly> entry = iterator.next();
                if (!java.util.Objects.equals(
                    assetId,
                    entry.getKey()
                        .assetId()))
                    continue;
                pendingAssemblyBytes -= entry.getValue().declaredTotalSize;
                iterator.remove();
            }
        }

        private static AssetSyncPacket decode(byte[] encoded, CelestialAsset.ID framedAssetId) {
            ByteBuf buffer = Unpooled.wrappedBuffer(encoded);
            try {
                AssetSyncPacket packet = new AssetSyncPacket();
                packet.fromBytes(buffer);
                if (buffer.isReadable()) return null;
                if (packet.syncType() == AssetSyncPacket.CLEAR) {
                    return framedAssetId == null ? packet : null;
                }
                return packet.assetId() != null && packet.assetId()
                    .equals(framedAssetId) ? packet : null;
            } catch (RuntimeException ex) {
                return null;
            } finally {
                buffer.release();
            }
        }

        private record AssemblyKey(CelestialAsset.ID assetId, UUID updateId) {}

        private static final class Assembly {

            private final int frameCount;
            private final int declaredTotalSize;
            private final long createdAtMillis;
            private final byte[][] payloads;
            private int receivedFrames;

            private Assembly(AssetStateFramePacket first, long createdAtMillis) {
                frameCount = first.frameCount();
                declaredTotalSize = first.declaredTotalSize();
                this.createdAtMillis = createdAtMillis;
                payloads = new byte[frameCount][];
            }

            private boolean hasCompatibleMetadata(AssetStateFramePacket frame) {
                return frame.frameCount() == frameCount && frame.declaredTotalSize() == declaredTotalSize;
            }

            private boolean accept(AssetStateFramePacket frame) {
                byte[] incoming = frame.payload();
                byte[] existing = payloads[frame.frameIndex()];
                if (existing != null) return Arrays.equals(existing, incoming);
                payloads[frame.frameIndex()] = incoming;
                receivedFrames++;
                return true;
            }

            private boolean isComplete() {
                return receivedFrames == frameCount;
            }

            private byte[] join() {
                byte[] joined = new byte[declaredTotalSize];
                int offset = 0;
                for (byte[] payload : payloads) {
                    System.arraycopy(payload, 0, joined, offset, payload.length);
                    offset += payload.length;
                }
                return joined;
            }
        }
    }

    interface ServerTransport {

        Collection<UUID> eligibleRecipients(UUID teamId);

        void send(UUID recipientId, AssetStateFramePacket packet);
    }

    interface ClientTransport {

        void requestFull(CelestialAsset.ID assetId);
    }

    private static final class ForgeServerTransport implements ServerTransport {

        @Override
        public Collection<UUID> eligibleRecipients(UUID teamId) {
            List<UUID> recipients = new java.util.ArrayList<>();
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return recipients;
            for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
                if (player != null && teamId.equals(GTTeamsCompat.getTeam(player))) {
                    recipients.add(player.getUniqueID());
                }
            }
            return recipients;
        }

        @Override
        public void send(UUID recipientId, AssetStateFramePacket packet) {
            for (EntityPlayerMP player : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                if (player != null && recipientId.equals(player.getUniqueID())) {
                    Galaxia.GALAXIA_NETWORK.sendTo(packet, player);
                    return;
                }
            }
        }
    }
}
