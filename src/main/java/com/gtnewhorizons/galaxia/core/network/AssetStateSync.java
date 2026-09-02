package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class AssetStateSync {

    private static final long NO_PUBLICATION = 0L;
    private static final Logger LOG = LogManager.getLogger(AssetStateSync.class);

    public static final Server SERVER = new Server(new ForgeServerTransport());
    public static final Client CLIENT = new Client(
        assetId -> Galaxia.GALAXIA_NETWORK.sendToServer(new ResyncRequest(assetId)));

    private AssetStateSync() {}

    public static final class ResyncRequest implements IMessage {

        private CelestialAsset.ID assetId;

        public ResyncRequest() {}

        ResyncRequest(CelestialAsset.ID assetId) {
            this.assetId = assetId;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            PacketUtil.writeId(buf, assetId);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            assetId = PacketUtil.readAssetId(buf);
        }

        public static final class Handler implements IMessageHandler<ResyncRequest, IMessage> {

            @Override
            public IMessage onMessage(ResyncRequest message, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                if (player != null && message.assetId != null) {
                    ServerTickTaskQueue.schedule(() -> SERVER.publishFullTo(player.getUniqueID(), message.assetId));
                }
                return null;
            }
        }
    }

    public static final class Server {

        private final ServerTransport transport;
        private final Map<CelestialAsset.ID, Publication> publications = new LinkedHashMap<>();
        private final Map<RecipientAsset, Long> recipientCursors = new LinkedHashMap<>();

        Server(ServerTransport transport) {
            this.transport = transport;
        }

        public void publishInteractive(CelestialAsset.ID assetId) {
            CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(assetId);
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            if (asset == null || teamId == null) return;
            publish(teamId, asset);
        }

        public void publishPeriodic() {
            for (CelestialAsset asset : CelestialAssetStore.SERVER.allAssetsInternal()) {
                UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(asset.assetId);
                if (teamId != null) publish(teamId, asset);
                asset.clean();
            }
        }

        public void publishFullTo(UUID recipientId, CelestialAsset.ID assetId) {
            CelestialAsset asset = CelestialAssetStore.SERVER.findAssetInternal(assetId);
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            Publication publication = publications.get(assetId);
            if (asset == null || teamId == null) {
                if (asset != null || teamId != null
                    || publication == null
                    || publication.packet()
                        .syncType() != AssetSyncPacket.ASSET_REMOVED)
                    return;
            } else {
                AssetSyncPacket candidate = statePacket(teamId, asset);
                if (publication == null || !candidate.hasSameState(publication.packet())) {
                    long revision = nextRevision(publication);
                    publication = new Publication(teamId, candidate.withPublishedRevision(revision));
                    publications.put(assetId, publication);
                }
            }
            if (!transport.eligibleRecipients(publication.teamId())
                .contains(recipientId)) return;
            if (send(recipientId, publication.packet())) {
                recipientCursors.put(
                    new RecipientAsset(recipientId, assetId),
                    publication.packet()
                        .publishedRevision());
            }
        }

        public boolean destroyAsset(CelestialAsset.ID assetId) {
            if (assetId == null) return false;
            UUID teamId = CelestialAssetStore.SERVER.getTeamIdInternal(assetId);
            if (teamId == null || !CelestialAssetStore.SERVER.destroyAssetInternal(assetId)) return false;
            publishRemoved(teamId, assetId);
            return true;
        }

        public void publishRemoved(UUID teamId, CelestialAsset.ID assetId) {
            if (teamId == null || assetId == null) return;
            Publication previous = publications.get(assetId);
            long revision = nextRevision(previous);
            Publication removal = new Publication(teamId, AssetSyncPacket.assetRemoved(assetId, revision));
            publications.put(assetId, removal);
            for (UUID recipientId : transport.eligibleRecipients(teamId)) {
                if (recipientId != null && send(recipientId, removal.packet())) {
                    recipientCursors.put(
                        new RecipientAsset(recipientId, assetId),
                        removal.packet()
                            .publishedRevision());
                }
            }
        }

        private void publish(UUID teamId, CelestialAsset asset) {
            CelestialAsset.ID assetId = asset.assetId;
            AssetSyncPacket candidate = statePacket(teamId, asset);
            Publication previous = publications.get(assetId);
            boolean changed = previous == null || !candidate.hasSameState(previous.packet());
            Publication publication = previous;
            if (changed) {
                long revision = nextRevision(previous);
                publication = new Publication(teamId, candidate.withPublishedRevision(revision));
                publications.put(assetId, publication);
                if (previous != null && !asset.isDirty()) {
                    LOG.warn("Asset {} state changed without dirty state; publishing recovery", assetId);
                }
            }
            if (publication == null) return;
            for (UUID recipientId : transport.eligibleRecipients(teamId)) {
                if (recipientId == null) continue;
                RecipientAsset key = new RecipientAsset(recipientId, assetId);
                if (recipientCursors.getOrDefault(key, NO_PUBLICATION) == publication.packet()
                    .publishedRevision()) continue;
                if (send(recipientId, publication.packet())) {
                    recipientCursors.put(
                        key,
                        publication.packet()
                            .publishedRevision());
                }
            }
        }

        private static long nextRevision(Publication publication) {
            return publication == null ? 1L
                : Math.incrementExact(
                    publication.packet()
                        .publishedRevision());
        }

        private static AssetSyncPacket statePacket(UUID teamId, CelestialAsset asset) {
            Map<ModuleInstance.ID, HammerDispatchStatus.Status> statuses = asset instanceof AutomatedFacility facility
                ? HammerDispatchStatus.inspectAll(
                    facility,
                    CelestialAssetStore.SERVER.listAssetsInSystemInternal(asset.systemKey, teamId),
                    GalaxiaCelestialAPI.currentOrbitalTime())
                : Map.of();
            return AssetSyncPacket.state(teamId, asset, statuses);
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
                int frameCount = Math.ceilDiv(totalSize, AssetStateFramePacket.MAX_FRAME_PAYLOAD_BYTES);
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

        private record Publication(UUID teamId, AssetSyncPacket packet) {}

        private record RecipientAsset(UUID recipientId, CelestialAsset.ID assetId) {}
    }

    public static final class Client {

        static final long ASSEMBLY_TIMEOUT_MILLIS = 30_000L;
        private static final long MAX_PENDING_ASSEMBLY_BYTES = 2L * AssetStateFramePacket.MAX_LOGICAL_UPDATE_BYTES;

        private final ClientTransport transport;
        private final LongSupplier currentTimeMillis;
        private final Map<CelestialAsset.ID, Long> appliedRevisions = new LinkedHashMap<>();
        private final Map<CelestialAsset.ID, Map<ModuleInstance.ID, HammerDispatchStatus.Status>> hammerStatuses = new LinkedHashMap<>();
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
            apply(packet);
        }

        public void tick() {
            expireAssemblies();
        }

        public void clear() {
            appliedRevisions.clear();
            hammerStatuses.clear();
            pendingRecovery.clear();
            assemblies.clear();
            pendingAssemblyBytes = 0L;
        }

        public @Nullable HammerDispatchStatus.Status hammerDispatchStatus(CelestialAsset.ID assetId,
            ModuleInstance.ID moduleId) {
            Map<ModuleInstance.ID, HammerDispatchStatus.Status> assetStatuses = hammerStatuses.get(assetId);
            return assetStatuses == null ? null : assetStatuses.get(moduleId);
        }

        private void apply(AssetSyncPacket packet) {
            switch (packet.syncType()) {
                case AssetSyncPacket.CLEAR -> {
                    CelestialClient.clear();
                    clear();
                }
                case AssetSyncPacket.ASSET_REMOVED -> applyRemoval(packet);
                case AssetSyncPacket.STATE -> applyState(packet);
                default -> requestRecovery(packet.assetId());
            }
        }

        private void applyRemoval(AssetSyncPacket packet) {
            long appliedRevision = appliedRevisions.getOrDefault(packet.assetId(), NO_PUBLICATION);
            if (packet.publishedRevision() <= appliedRevision) return;
            CelestialAssetStore.CLIENT.destroyAssetInternal(packet.assetId());
            hammerStatuses.remove(packet.assetId());
            appliedRevisions.put(packet.assetId(), packet.publishedRevision());
            pendingRecovery.remove(packet.assetId());
        }

        private void applyState(AssetSyncPacket packet) {
            long appliedRevision = appliedRevisions.getOrDefault(packet.assetId(), NO_PUBLICATION);
            if (packet.publishedRevision() <= appliedRevision) return;
            try {
                AssetSyncPacket.StatePayload state = packet.state();
                AssetState.Decoded decoded = AssetState.decode(state.assetState());
                if (!packet.assetId()
                    .equals(decoded.asset().assetId)) {
                    throw new IllegalArgumentException("Framed asset ID does not match canonical state");
                }
                CelestialAsset current = CelestialAssetStore.CLIENT.findAssetInternal(packet.assetId());
                if (current == null) {
                    CelestialAssetStore.CLIENT.registerAssetInternal(decoded.teamId(), decoded.asset());
                } else {
                    AssetState
                        .replace(CelestialAssetStore.CLIENT.getTeamIdInternal(packet.assetId()), current, decoded);
                }
                hammerStatuses.put(packet.assetId(), state.hammerStatuses());
                appliedRevisions.put(packet.assetId(), packet.publishedRevision());
                pendingRecovery.remove(packet.assetId());
            } catch (RuntimeException ex) {
                LOG.warn("Rejected canonical asset state for {}: {}", packet.assetId(), ex.getMessage());
                requestRecovery(packet.assetId());
            }
        }

        private void requestRecovery(CelestialAsset.ID assetId) {
            if (assetId != null && pendingRecovery.add(assetId)) {
                LOG.warn("Rejected asset state for {}; requesting full recovery", assetId);
                transport.requestFull(assetId);
            }
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

        private static AssetSyncPacket decode(byte[] encoded, CelestialAsset.ID framedAssetId) {
            ByteBuf buffer = Unpooled.wrappedBuffer(encoded);
            try {
                AssetSyncPacket packet = new AssetSyncPacket();
                packet.fromBytes(buffer, framedAssetId);
                return packet;
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
            List<UUID> recipients = new ArrayList<>();
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
