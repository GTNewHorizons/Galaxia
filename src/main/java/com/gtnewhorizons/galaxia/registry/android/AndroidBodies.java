package com.gtnewhorizons.galaxia.registry.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.core.persistence.FacilityPersistenceManager;

/** Owns body identity, exclusive control and the authoritative physical-state checkpoint. */
public final class AndroidBodies {

    private static final int FORMAT_VERSION = 4;
    public static final int MAX_NAME_LENGTH = 32;
    private static final String CHECKPOINT_FILE = "_android_bodies.dat";

    private final File checkpoint;
    private Checkpoint current = new Checkpoint(Map.of(), Map.of(), Map.of(), Map.of());

    private AndroidBodies(File checkpoint) {
        this.checkpoint = checkpoint;
    }

    @Nonnull
    public static AndroidBodies open(@Nonnull File checkpoint) throws IOException {
        AndroidBodies bodies = new AndroidBodies(checkpoint);
        if (!checkpoint.exists()) return bodies;
        try (FileInputStream input = new FileInputStream(checkpoint)) {
            bodies.current = decode(CompressedStreamTools.readCompressed(input));
        } catch (RuntimeException ex) {
            throw new IOException("Invalid android body checkpoint: " + checkpoint, ex);
        }
        if (!bodies.current.sessions()
            .isEmpty()) {
            // Recovery returns operators to their recorded biological bodies. It never restarts remote control.
            bodies.commit(
                new Checkpoint(
                    bodies.current.androids(),
                    bodies.current.biological(),
                    Map.of(),
                    bodies.current.retired()));
        }
        return bodies;
    }

    @Nonnull
    public static AndroidBodies openWorld(@Nonnull File saveDirectory) throws IOException {
        return open(new File(FacilityPersistenceManager.dataDirectory(saveDirectory), CHECKPOINT_FILE));
    }

    public record Anchor(@Nonnull UUID id, int dimension, int x, int y, int z) {}

    public enum Chassis {

        EV(4, 6_400_000L),
        LUV(6, 102_400_000L),
        UV(8, 1_638_400_000L);

        private final int voltageTier;
        private final long capacity;

        Chassis(int voltageTier, long capacity) {
            this.voltageTier = voltageTier;
            this.capacity = capacity;
        }

        public long capacity() {
            return capacity;
        }

        public long energyMultiplier() {
            return 1L << (voltageTier - EV.voltageTier);
        }

        public long chargeMultiplier() {
            return capacity / EV.capacity;
        }

        public String translationKey() {
            return "galaxia.android.chassis." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record AndroidBody(@Nonnull UUID id, @Nonnull UUID teamId, @Nonnull Anchor dock, @Nonnull BodyState state,
        long battery, @Nonnull Chassis chassis, int destructionTicks, boolean portalWarning, @Nonnull String name) {

        public AndroidBody {
            if (state.dimension() != dock.dimension()) {
                throw new IllegalArgumentException("An android body must remain in its dock dimension");
            }
            if (battery < 0 || battery > chassis.capacity() || destructionTicks < -1) {
                throw new IllegalArgumentException("Invalid android battery or destruction countdown");
            }
            if (portalWarning && destructionTicks < 0)
                throw new IllegalArgumentException("A portal warning needs an active countdown");
            validateName(name);
        }

        public boolean armed() {
            return destructionTicks >= 0;
        }

        public long capacity() {
            return chassis.capacity();
        }

        public boolean canRetreatFromPortal() {
            return portalWarning && destructionTicks > 0;
        }
    }

    public record BiologicalBody(@Nonnull UUID id, @Nonnull UUID operatorId, @Nonnull Anchor capsule,
        @Nonnull BodyState state) {

        public BiologicalBody {
            if (id.equals(operatorId))
                throw new IllegalArgumentException("A biological body needs its own entity identity");
        }
    }

    public record Session(@Nonnull UUID id, @Nonnull UUID operatorId, @Nonnull UUID bodyId) {}

    /**
     * Completes a dock job once. The returned identity remains completed after destruction, so callers must check
     * {@link #body(UUID)} before creating a world projection.
     */
    @Nonnull
    public UUID manufacture(@Nonnull UUID operationId, @Nonnull UUID teamId, @Nonnull Anchor dock,
        @Nonnull BodyState initialState, @Nonnull Chassis chassis, long initialCharge) throws IOException {
        AndroidBody existing = current.androids()
            .get(operationId);
        RetiredBody retired = current.retired()
            .get(operationId);
        if (existing != null || retired != null) {
            UUID owner = existing != null ? existing.teamId() : retired.teamId();
            Anchor originalDock = existing != null ? existing.dock() : retired.dock();
            Chassis originalChassis = existing != null ? existing.chassis() : retired.chassis();
            if (!owner.equals(teamId) || !originalDock.equals(dock) || originalChassis != chassis) {
                throw new IllegalStateException("A manufacture identity belongs to another team or dock");
            }
            return operationId;
        }
        AndroidBody body = new AndroidBody(
            operationId,
            teamId,
            dock,
            initialState,
            initialCharge,
            chassis,
            -1,
            false,
            "");
        commit(withBody(body));
        return operationId;
    }

    @Nonnull
    public Optional<AndroidBody> body(@Nonnull UUID bodyId) {
        return Optional.ofNullable(
            current.androids()
                .get(bodyId));
    }

    public boolean wasManufactured(@Nonnull UUID operationId) {
        return current.androids()
            .containsKey(operationId)
            || current.retired()
                .containsKey(operationId);
    }

    public boolean isCapsuleOccupied(@Nonnull Anchor capsule) {
        return current.sessions()
            .values()
            .stream()
            .anyMatch(
                active -> current.biological()
                    .get(active.operatorId())
                    .capsule()
                    .id()
                    .equals(capsule.id()));
    }

    @Nonnull
    public List<AndroidBody> bodies(@Nonnull UUID teamId) {
        return current.androids()
            .values()
            .stream()
            .filter(
                body -> body.teamId()
                    .equals(teamId))
            .toList();
    }

    public void rename(@Nonnull UUID teamId, @Nonnull UUID bodyId, @Nonnull String name) throws IOException {
        AndroidBody body = requireBody(bodyId);
        if (!body.teamId()
            .equals(teamId)) throw new IllegalStateException("Android body belongs to another team");
        String normalized = name.strip();
        validateName(normalized);
        if (body.name()
            .equals(normalized)) return;
        commit(
            withBody(
                new AndroidBody(
                    body.id(),
                    body.teamId(),
                    body.dock(),
                    body.state(),
                    body.battery(),
                    body.chassis(),
                    body.destructionTicks(),
                    body.portalWarning(),
                    normalized)));
    }

    private static void validateName(String name) {
        if (name.length() > MAX_NAME_LENGTH || name.indexOf('\u00a7') >= 0
            || name.chars()
                .anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid android name");
        }
    }

    @Nonnull
    public List<AndroidBody> allBodies() {
        return List.copyOf(
            current.androids()
                .values());
    }

    @Nonnull
    public Optional<BiologicalBody> biological(@Nonnull UUID operatorId) {
        return Optional.ofNullable(
            current.biological()
                .get(operatorId));
    }

    @Nonnull
    public Optional<Session> session(@Nonnull UUID operatorId) {
        return Optional.ofNullable(
            current.sessions()
                .get(operatorId));
    }

    @Nonnull
    public List<Session> sessions() {
        return List.copyOf(
            current.sessions()
                .values());
    }

    @Nonnull
    public Session activate(@Nonnull UUID operatorId, @Nonnull UUID teamId, @Nonnull UUID bodyId,
        @Nonnull Anchor capsule, @Nonnull BodyState biologicalState) throws IOException {
        AndroidBody body = requireBody(bodyId);
        if (current.sessions()
            .containsKey(operatorId) || isControlled(bodyId)) {
            throw new IllegalStateException("Operator or android body is already assigned");
        }
        if (!body.teamId()
            .equals(teamId) || body.armed()
            || body.battery() <= 0
            || body.state()
                .health() <= 0) {
            throw new IllegalStateException("Android body is unavailable for this team");
        }
        requireCapsuleDimension(capsule, biologicalState);
        if (isCapsuleOccupied(capsule)) throw new IllegalStateException("Control capsule is already occupied");
        Map<UUID, BiologicalBody> biological = new LinkedHashMap<>(current.biological());
        biological.put(operatorId, updatedBiological(operatorId, capsule, biologicalState));
        Map<UUID, Session> sessions = new LinkedHashMap<>(current.sessions());
        Session session = new Session(UUID.randomUUID(), operatorId, bodyId);
        sessions.put(operatorId, session);
        commit(new Checkpoint(current.androids(), biological, sessions, current.retired()));
        return session;
    }

    @Nonnull
    public BodyState deactivate(@Nonnull UUID operatorId, @Nonnull BodyState androidState) throws IOException {
        Session session = session(operatorId)
            .orElseThrow(() -> new IllegalStateException("Operator has no active body"));
        AndroidBody body = requireBody(session.bodyId());
        Map<UUID, Session> sessions = new LinkedHashMap<>(current.sessions());
        sessions.remove(operatorId);
        Map<UUID, AndroidBody> androids = new LinkedHashMap<>(current.androids());
        androids.put(body.id(), withState(body, androidState));
        BodyState biological = current.biological()
            .get(operatorId)
            .state();
        commit(new Checkpoint(androids, current.biological(), sessions, current.retired()));
        return biological;
    }

    /** Updates live projections. The next transition or periodic checkpoint commits the observed state. */
    public void observeOperator(@Nonnull UUID operatorId, @Nonnull BodyState state) {
        Session session = current.sessions()
            .get(operatorId);
        if (session != null) {
            current = withBody(withState(requireBody(session.bodyId()), state));
        } else {
            BiologicalBody body = biological(operatorId)
                .orElseThrow(() -> new IllegalStateException("Operator is not enrolled"));
            Map<UUID, BiologicalBody> biological = new LinkedHashMap<>(current.biological());
            biological.put(operatorId, new BiologicalBody(body.id(), operatorId, body.capsule(), state));
            current = new Checkpoint(current.androids(), biological, current.sessions(), current.retired());
        }
    }

    public void observeBody(@Nonnull UUID bodyId, @Nonnull BodyState state) {
        requireInactive(bodyId);
        current = withBody(withState(requireBody(bodyId), state));
    }

    public void observeBiological(@Nonnull UUID operatorId, @Nonnull BodyState state) {
        if (!current.sessions()
            .containsKey(operatorId)) {
            throw new IllegalStateException("Biological projection requires an active control session");
        }
        BiologicalBody body = current.biological()
            .get(operatorId);
        requireCapsuleDimension(body.capsule(), state);
        Map<UUID, BiologicalBody> biological = new LinkedHashMap<>(current.biological());
        biological.put(operatorId, new BiologicalBody(body.id(), operatorId, body.capsule(), state));
        current = new Checkpoint(current.androids(), biological, current.sessions(), current.retired());
    }

    public void setBattery(@Nonnull UUID bodyId, long battery) {
        AndroidBody body = requireBody(bodyId);
        current = withBody(
            new AndroidBody(
                body.id(),
                body.teamId(),
                body.dock(),
                body.state(),
                battery,
                body.chassis(),
                body.destructionTicks(),
                body.portalWarning(),
                body.name()));
    }

    public void armDestruction(@Nonnull UUID bodyId, int ticks) throws IOException {
        armDestruction(bodyId, ticks, false);
    }

    public void warnPortalTransfer(@Nonnull UUID bodyId, int ticks) throws IOException {
        armDestruction(bodyId, ticks, true);
    }

    private void armDestruction(UUID bodyId, int ticks, boolean portalWarning) throws IOException {
        if (ticks < 0) throw new IllegalArgumentException("Destruction countdown cannot be negative");
        AndroidBody body = requireBody(bodyId);
        if (body.armed() && (portalWarning || !body.portalWarning())) return;
        commit(withBody(withCountdown(body, body.armed() ? body.destructionTicks() : ticks, portalWarning)));
    }

    public boolean cancelPortalWarning(@Nonnull UUID bodyId) throws IOException {
        AndroidBody body = requireBody(bodyId);
        if (!body.canRetreatFromPortal()) return false;
        commit(withBody(withCountdown(body, -1, false)));
        return true;
    }

    public void setDestructionTicks(@Nonnull UUID bodyId, int ticks) {
        AndroidBody body = requireBody(bodyId);
        if (!body.armed()) throw new IllegalStateException("Android destruction is not armed");
        if (ticks < 0 || ticks > body.destructionTicks()) {
            throw new IllegalArgumentException("An armed destruction countdown cannot be cancelled or extended");
        }
        current = withBody(withCountdown(body, ticks, body.portalWarning()));
    }

    @Nonnull
    public List<ItemStack> destroy(@Nonnull UUID bodyId) throws IOException {
        requireInactive(bodyId);
        AndroidBody body = requireBody(bodyId);
        List<ItemStack> drops = body.state()
            .carriedItems();
        Map<UUID, AndroidBody> androids = new LinkedHashMap<>(current.androids());
        androids.remove(bodyId);
        Map<UUID, RetiredBody> retired = new LinkedHashMap<>(current.retired());
        retired.put(bodyId, new RetiredBody(body.id(), body.teamId(), body.dock(), body.chassis()));
        commit(new Checkpoint(androids, current.biological(), current.sessions(), retired));
        return drops;
    }

    public void repair(@Nonnull UUID bodyId, float health) throws IOException {
        requireInactive(bodyId);
        AndroidBody body = requireBody(bodyId);
        if (body.armed()) throw new IllegalStateException("An armed android cannot be repaired");
        if (health < body.state()
            .health()) throw new IllegalArgumentException("Repair cannot lower body health");
        commit(
            withBody(
                withState(
                    body,
                    body.state()
                        .withHealth(health))));
    }

    public void mergeTeams(@Nonnull UUID consumedTeam, @Nonnull UUID survivingTeam) throws IOException {
        if (consumedTeam.equals(survivingTeam)) return;
        Map<UUID, AndroidBody> androids = new LinkedHashMap<>(current.androids());
        Set<UUID> transferred = new HashSet<>();
        for (AndroidBody body : current.androids()
            .values()) {
            if (!body.teamId()
                .equals(consumedTeam)) continue;
            transferred.add(body.id());
            androids.put(
                body.id(),
                new AndroidBody(
                    body.id(),
                    survivingTeam,
                    body.dock(),
                    body.state(),
                    body.battery(),
                    body.chassis(),
                    body.destructionTicks(),
                    body.portalWarning(),
                    body.name()));
        }
        Map<UUID, RetiredBody> retired = new LinkedHashMap<>(current.retired());
        boolean retiredChanged = false;
        for (RetiredBody body : current.retired()
            .values()) {
            if (!body.teamId()
                .equals(consumedTeam)) continue;
            retired.put(body.id(), new RetiredBody(body.id(), survivingTeam, body.dock(), body.chassis()));
            retiredChanged = true;
        }
        if (transferred.isEmpty() && !retiredChanged) return;
        Map<UUID, Session> sessions = new LinkedHashMap<>(current.sessions());
        sessions.values()
            .removeIf(session -> transferred.contains(session.bodyId()));
        commit(new Checkpoint(androids, current.biological(), sessions, retired));
    }

    public void checkpoint() throws IOException {
        commit(current);
    }

    private void commit(Checkpoint candidate) throws IOException {
        FacilityPersistenceManager.writeCommittedNbt(checkpoint, encode(candidate));
        current = candidate;
    }

    private Checkpoint withBody(AndroidBody replacement) {
        Map<UUID, AndroidBody> androids = new LinkedHashMap<>(current.androids());
        androids.put(replacement.id(), replacement);
        return new Checkpoint(androids, current.biological(), current.sessions(), current.retired());
    }

    private BiologicalBody updatedBiological(UUID operatorId, Anchor capsule, BodyState state) {
        BiologicalBody previous = current.biological()
            .get(operatorId);
        return new BiologicalBody(previous == null ? UUID.randomUUID() : previous.id(), operatorId, capsule, state);
    }

    private static AndroidBody withState(AndroidBody body, BodyState state) {
        return new AndroidBody(
            body.id(),
            body.teamId(),
            body.dock(),
            state,
            body.battery(),
            body.chassis(),
            body.destructionTicks(),
            body.portalWarning(),
            body.name());
    }

    private static AndroidBody withCountdown(AndroidBody body, int ticks, boolean portalWarning) {
        return new AndroidBody(
            body.id(),
            body.teamId(),
            body.dock(),
            body.state(),
            body.battery(),
            body.chassis(),
            ticks,
            portalWarning,
            body.name());
    }

    private AndroidBody requireBody(UUID bodyId) {
        return body(bodyId).orElseThrow(() -> new IllegalStateException("Android body does not exist"));
    }

    public boolean isControlled(@Nonnull UUID bodyId) {
        return current.sessions()
            .values()
            .stream()
            .anyMatch(
                session -> session.bodyId()
                    .equals(bodyId));
    }

    private void requireInactive(UUID bodyId) {
        if (isControlled(bodyId)) throw new IllegalStateException("Android body is currently controlled");
    }

    private record RetiredBody(UUID id, UUID teamId, Anchor dock, Chassis chassis) {}

    private record Checkpoint(Map<UUID, AndroidBody> androids, Map<UUID, BiologicalBody> biological,
        Map<UUID, Session> sessions, Map<UUID, RetiredBody> retired) {

        private Checkpoint {
            androids = Map.copyOf(androids);
            biological = Map.copyOf(biological);
            sessions = Map.copyOf(sessions);
            retired = Map.copyOf(retired);
        }
    }

    private static NBTTagCompound encode(Checkpoint state) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", FORMAT_VERSION);
        NBTTagList androids = new NBTTagList();
        for (AndroidBody body : state.androids()
            .values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "id",
                body.id()
                    .toString());
            tag.setString(
                "team",
                body.teamId()
                    .toString());
            tag.setTag("dock", encodeAnchor(body.dock()));
            tag.setTag(
                "state",
                body.state()
                    .write());
            tag.setLong("battery", body.battery());
            tag.setString(
                "chassis",
                body.chassis()
                    .name());
            tag.setInteger("destructionTicks", body.destructionTicks());
            tag.setBoolean("portalWarning", body.portalWarning());
            tag.setString("name", body.name());
            androids.appendTag(tag);
        }
        root.setTag("androids", androids);
        NBTTagList biological = new NBTTagList();
        for (BiologicalBody body : state.biological()
            .values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "id",
                body.id()
                    .toString());
            tag.setString(
                "operator",
                body.operatorId()
                    .toString());
            tag.setTag("capsule", encodeAnchor(body.capsule()));
            tag.setTag(
                "state",
                body.state()
                    .write());
            biological.appendTag(tag);
        }
        root.setTag("biological", biological);
        NBTTagList sessions = new NBTTagList();
        for (Session session : state.sessions()
            .values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "id",
                session.id()
                    .toString());
            tag.setString(
                "operator",
                session.operatorId()
                    .toString());
            tag.setString(
                "body",
                session.bodyId()
                    .toString());
            sessions.appendTag(tag);
        }
        root.setTag("sessions", sessions);
        NBTTagList retired = new NBTTagList();
        for (RetiredBody body : state.retired()
            .values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "id",
                body.id()
                    .toString());
            tag.setString(
                "team",
                body.teamId()
                    .toString());
            tag.setTag("dock", encodeAnchor(body.dock()));
            tag.setString(
                "chassis",
                body.chassis()
                    .name());
            retired.appendTag(tag);
        }
        root.setTag("retired", retired);
        return root;
    }

    private static Checkpoint decode(NBTTagCompound root) {
        require(root, "version", NBT.TAG_INT);
        if (root.getInteger("version") != FORMAT_VERSION)
            throw new IllegalArgumentException("Unsupported body checkpoint version");
        Map<UUID, AndroidBody> androids = new LinkedHashMap<>();
        NBTTagList encodedAndroids = list(root, "androids");
        for (int i = 0; i < encodedAndroids.tagCount(); i++) {
            NBTTagCompound tag = encodedAndroids.getCompoundTagAt(i);
            require(tag, "battery", NBT.TAG_LONG);
            require(tag, "chassis", NBT.TAG_STRING);
            require(tag, "destructionTicks", NBT.TAG_INT);
            require(tag, "portalWarning", NBT.TAG_BYTE);
            require(tag, "name", NBT.TAG_STRING);
            AndroidBody body = new AndroidBody(
                uuid(tag, "id"),
                uuid(tag, "team"),
                decodeAnchor(compound(tag, "dock")),
                BodyState.read(compound(tag, "state")),
                tag.getLong("battery"),
                Chassis.valueOf(tag.getString("chassis")),
                tag.getInteger("destructionTicks"),
                tag.getBoolean("portalWarning"),
                tag.getString("name"));
            if (androids.put(body.id(), body) != null) throw new IllegalArgumentException("Duplicate android identity");
        }
        Map<UUID, RetiredBody> retired = new LinkedHashMap<>();
        NBTTagList encodedRetired = list(root, "retired");
        for (int i = 0; i < encodedRetired.tagCount(); i++) {
            NBTTagCompound tag = encodedRetired.getCompoundTagAt(i);
            RetiredBody body = new RetiredBody(
                uuid(tag, "id"),
                uuid(tag, "team"),
                decodeAnchor(compound(tag, "dock")),
                Chassis.valueOf(tag.getString("chassis")));
            if (androids.containsKey(body.id()) || retired.put(body.id(), body) != null) {
                throw new IllegalArgumentException("Duplicate retired android identity");
            }
        }
        Map<UUID, BiologicalBody> biological = new LinkedHashMap<>();
        Set<UUID> physicalIds = new HashSet<>(androids.keySet());
        physicalIds.addAll(retired.keySet());
        NBTTagList encodedBiological = list(root, "biological");
        for (int i = 0; i < encodedBiological.tagCount(); i++) {
            NBTTagCompound tag = encodedBiological.getCompoundTagAt(i);
            BiologicalBody body = new BiologicalBody(
                uuid(tag, "id"),
                uuid(tag, "operator"),
                decodeAnchor(compound(tag, "capsule")),
                BodyState.read(compound(tag, "state")));
            if (!physicalIds.add(body.id()) || biological.put(body.operatorId(), body) != null) {
                throw new IllegalArgumentException("Duplicate biological identity");
            }
        }
        Map<UUID, Session> sessions = new LinkedHashMap<>();
        Set<UUID> sessionIds = new HashSet<>();
        Set<UUID> assignedBodies = new HashSet<>();
        Set<UUID> occupiedCapsules = new HashSet<>();
        NBTTagList encodedSessions = list(root, "sessions");
        for (int i = 0; i < encodedSessions.tagCount(); i++) {
            NBTTagCompound tag = encodedSessions.getCompoundTagAt(i);
            Session session = new Session(uuid(tag, "id"), uuid(tag, "operator"), uuid(tag, "body"));
            if (!androids.containsKey(session.bodyId()) || !biological.containsKey(session.operatorId())) {
                throw new IllegalArgumentException("Session references a missing body or operator");
            }
            BiologicalBody operator = biological.get(session.operatorId());
            requireCapsuleDimension(operator.capsule(), operator.state());
            if (sessions.put(session.operatorId(), session) != null || !sessionIds.add(session.id())
                || !assignedBodies.add(session.bodyId())
                || !occupiedCapsules.add(
                    biological.get(session.operatorId())
                        .capsule()
                        .id())) {
                throw new IllegalArgumentException("Duplicate body control assignment");
            }
        }
        return new Checkpoint(androids, biological, sessions, retired);
    }

    private static NBTTagCompound encodeAnchor(Anchor anchor) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "id",
            anchor.id()
                .toString());
        tag.setInteger("dimension", anchor.dimension());
        tag.setInteger("x", anchor.x());
        tag.setInteger("y", anchor.y());
        tag.setInteger("z", anchor.z());
        return tag;
    }

    private static Anchor decodeAnchor(NBTTagCompound tag) {
        require(tag, "dimension", NBT.TAG_INT);
        require(tag, "x", NBT.TAG_INT);
        require(tag, "y", NBT.TAG_INT);
        require(tag, "z", NBT.TAG_INT);
        return new Anchor(
            uuid(tag, "id"),
            tag.getInteger("dimension"),
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"));
    }

    private static UUID uuid(NBTTagCompound tag, String key) {
        require(tag, key, NBT.TAG_STRING);
        return UUID.fromString(tag.getString(key));
    }

    private static NBTTagCompound compound(NBTTagCompound tag, String key) {
        require(tag, key, NBT.TAG_COMPOUND);
        return tag.getCompoundTag(key);
    }

    private static NBTTagList list(NBTTagCompound tag, String key) {
        require(tag, key, NBT.TAG_LIST);
        NBTTagList list = (NBTTagList) tag.getTag(key);
        if (list.tagCount() > 0 && list.func_150303_d() != NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid body checkpoint list: " + key);
        }
        return list;
    }

    private static void require(NBTTagCompound tag, String key, int type) {
        if (!tag.hasKey(key, type))
            throw new IllegalArgumentException("Missing or invalid body checkpoint field: " + key);
    }

    private static void requireCapsuleDimension(Anchor capsule, BodyState state) {
        if (state.dimension() != capsule.dimension()) {
            throw new IllegalArgumentException("A controlled biological body must remain in its capsule dimension");
        }
    }
}
