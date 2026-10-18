package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.fluid.FluidInteractions;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.core.state.CelestialObjectKeyState;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.CostModel;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.VehicleScheduleUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.VehicleScheduleUI.Catalog;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.VehicleScheduleUI.EndpointChoice;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.VehicleScheduleUI.StationChoice;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native panel synchronization for one local draft and its server-owned vehicle. */
public final class RocketScheduleSyncHandler extends SyncHandler<RocketScheduleSyncHandler> {

    private static final String PREFIX = "galaxia.rocket.schedule.";
    private static final int SAVE = 1;
    private static final int SELECT = 2;
    private static final int START = 3;
    private static final int REVIEW = 4;
    private static final int SEARCH_LANDING = 5;
    private static final int ACCEPT_LANDING = 6;
    private static final int CANCEL_LANDING = 7;
    private static final int OPEN_BODY_MAP = 8;
    private static final int RETURN_BODY_MAP = 9;
    private static final NavigationFactory NAVIGATION_FACTORY = new NavigationFactory();
    private static final int LANDING_RESULT = 103;
    private static final int STATE = 101;
    private static final int FAILURE = 102;

    @Nullable
    private final Supplier<UUID> fixedVehicle;
    private final VehicleScheduleUI.Session session = new VehicleScheduleUI.Session();
    @Nullable
    private UUID selectedVehicle;
    @Nullable
    private NBTTagCompound lastState;
    private int catalogTicks;
    @Nullable
    private CelestialServerRuntime.LandingSearch landingSearch;
    @Nullable
    private UUID landingVehicle;
    private long landingRevision;
    private long landingQuery;
    @Nullable
    private NavigationGuiData navigationData;
    @Nullable
    private CelestialObjectKey mapCandidate;

    public RocketScheduleSyncHandler(@Nullable Supplier<UUID> fixedVehicle) {
        this.fixedVehicle = fixedVehicle;
        bindVehicle();
        allowC2S();
        session.commands(this::save, this::start, this::review);
        session.manualCommands(this::searchLanding, this::acceptLanding, this::cancelLanding);
        session.bodyMap(this::openBodyMap);
    }

    public static void initNavigationFactory() {
        GuiManager.registerFactory(NAVIGATION_FACTORY);
    }

    /** A null source opens the team's fleet. A physical controller supplies its current vehicle ID. */
    public static ModularPanel build(PanelSyncManager syncManager, @Nullable Supplier<UUID> source) {
        return build(syncManager, source, null);
    }

    private static ModularPanel build(PanelSyncManager syncManager, @Nullable Supplier<UUID> source,
        @Nullable NavigationGuiData navigation) {
        var handler = new RocketScheduleSyncHandler(source);
        handler.navigationData = navigation;
        if (navigation != null) {
            handler.selectedVehicle = navigation.vehicleId;
            navigation.controls = handler;
            if (syncManager.isClient()) handler.session.restoreNavigation(navigation.state);
        }
        syncManager.syncValue("rocket_schedule", handler);
        syncManager.addOpenListener(player -> handler.bindVehicle());
        syncManager.addCloseListener(player -> handler.landingSearch = null);
        if (navigation == null || !navigation.map) return VehicleScheduleUI.build(handler.session);
        var choices = VehicleScheduleUI.bodySelectionControls(
            handler.session,
            () -> handler.mapCandidate,
            () -> handler.returnFromBodyMap(true),
            () -> handler.returnFromBodyMap(false));
        return GalacticMapWidget
            .buildBodyPicker(syncManager, navigation.getPlayer(), body -> handler.mapCandidate = body, choices);
    }

    private void openBodyMap(VehicleScheduleUI.Session draft) {
        syncToServer(OPEN_BODY_MAP, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            ByteBufUtils.writeTag(buf, draft.navigationState());
        });
    }

    private void returnFromBodyMap(boolean select) {
        if (navigationData == null || session.pending()) return;
        if (select && !session.selectDestinationBody(mapCandidate)) return;
        syncToServer(RETURN_BODY_MAP, buf -> {
            PacketUtil.writeId(buf, navigationData.vehicleId);
            ByteBufUtils.writeTag(buf, select ? session.navigationState() : navigationData.state);
            buf.writeBoolean(select);
            if (select) PacketUtil.writeCelestialObjectKey(buf, mapCandidate);
        });
    }

    private void navigate(EntityPlayerMP player, Rocket rocket, NBTTagCompound state, boolean map,
        @Nullable CelestialObjectKey selectedBody) {
        var restored = new VehicleScheduleUI.Session();
        restored.restoreNavigation(state);
        var encodedVehicle = UUID.fromString(new NbtReader(state, "rocketNavigation").string("vehicle"));
        if (!rocket.id()
            .equals(encodedVehicle)) {
            failure(PREFIX + "forbidden");
            return;
        }
        restored.receive(rocket.scheduleView(), catalog(player, rocket), null, false);
        if (selectedBody != null && !restored.canSelectDestinationBody(selectedBody)) {
            failure(PREFIX + "forbidden");
            return;
        }
        var sourceContainer = navigationData == null ? getSyncManager().getContainer() : navigationData.sourceContainer;
        var sourceBinding = navigationData == null ? fixedVehicle : navigationData.sourceBinding;
        var data = new NavigationGuiData(
            player,
            rocket.id(),
            state,
            map,
            sourceBinding != null,
            sourceContainer,
            sourceBinding);
        if (!NAVIGATION_FACTORY.canInteractWith(player, data)) {
            failure(PREFIX + "forbidden");
            return;
        }
        GalacticMapWidget.prepareOpen(player);
        GuiManager.open(NAVIGATION_FACTORY, data, player);
    }

    /** The source container contributes only its existing live access gate. Its disposed UI is never reopened. */
    private static final class NavigationGuiData extends GuiData {

        private final UUID vehicleId;
        private final NBTTagCompound state;
        private final boolean map;
        private final boolean fixed;
        @Nullable
        private final ModularContainer sourceContainer;
        @Nullable
        private final Supplier<UUID> sourceBinding;
        @Nullable
        private RocketScheduleSyncHandler controls;

        private NavigationGuiData(EntityPlayer player, UUID vehicleId, NBTTagCompound state, boolean map, boolean fixed,
            @Nullable ModularContainer sourceContainer, @Nullable Supplier<UUID> sourceBinding) {
            super(player);
            if (state == null) throw new IllegalArgumentException("Missing rocket navigation state");
            this.vehicleId = vehicleId;
            this.state = (NBTTagCompound) state.copy();
            this.map = map;
            this.fixed = fixed;
            this.sourceContainer = sourceContainer;
            this.sourceBinding = sourceBinding;
        }
    }

    private static final class NavigationFactory implements UIFactory<NavigationGuiData> {

        @Override
        public String getFactoryName() {
            return "galaxia:rocket_navigation";
        }

        @Override
        public ModularPanel createPanel(NavigationGuiData data, PanelSyncManager syncManager, UISettings settings) {
            if (!data.isClient() && !canInteractWith(data.getPlayer(), data))
                return ModularPanel.defaultPanel("galaxia:rocket_access_denied")
                    .child(
                        IKey.lang(PREFIX + "forbidden")
                            .asWidget());
            Supplier<UUID> source = data.isClient() ? data.fixed ? () -> data.vehicleId : null : data.sourceBinding;
            return build(syncManager, source, data);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ModularScreen createScreen(NavigationGuiData data, ModularPanel panel) {
            GalacticMapWidget.prepareClientOpen();
            return new ModularScreen(Galaxia.MODID, panel) {

                @Override
                public boolean onKeyPressed(char character, int keyCode) {
                    if (data.map && (keyCode == Keyboard.KEY_ESCAPE
                        || keyCode == Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode())) {
                        if (data.controls != null) data.controls.returnFromBodyMap(false);
                        return true;
                    }
                    return super.onKeyPressed(character, keyCode);
                }
            };
        }

        @Override
        public boolean canInteractWith(EntityPlayer player, NavigationGuiData data) {
            if (player != data.getPlayer()) return false;
            if (data.isClient()) return true;
            if (data.sourceContainer == null || !data.sourceContainer.canInteractWith(player)
                || data.sourceBinding != null && !data.vehicleId.equals(data.sourceBinding.get())) return false;
            var rocket = Galaxia.proxy.serverRuntime()
                .rockets()
                .find(data.vehicleId);
            return rocket != null && rocket.teamId()
                .equals(GTTeamsCompat.getTeam(player))
                && GTTeamsCompat.hasPermission(rocket.teamId(), player, TeamAction.CONFIGURE_LOGISTICS);
        }

        @Override
        public void writeGuiData(NavigationGuiData data, PacketBuffer buffer) {
            PacketUtil.writeId(buffer, data.vehicleId);
            buffer.writeBoolean(data.map);
            buffer.writeBoolean(data.fixed);
            ByteBufUtils.writeTag(buffer, data.state);
        }

        @Override
        public NavigationGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            UUID vehicle = PacketUtil.readId(buffer);
            boolean map = buffer.readBoolean();
            boolean fixed = buffer.readBoolean();
            return new NavigationGuiData(player, vehicle, ByteBufUtils.readTag(buffer), map, fixed, null, null);
        }
    }

    private void bindVehicle() {
        if (fixedVehicle != null) selectedVehicle = fixedVehicle.get();
        lastState = null;
    }

    private void save(VehicleScheduleUI.Session draft) {
        syncToServer(SAVE, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(draft.baseRevision());
            ByteBufUtils.writeTag(
                buf,
                draft.draft()
                    .serializeNBT());
        });
    }

    private void select(UUID vehicleId) {
        syncToServer(SELECT, buf -> PacketUtil.writeId(buf, vehicleId));
    }

    private void start(VehicleScheduleUI.Session draft) {
        syncToServer(START, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(
                draft.view()
                    .manualMission() == null ? draft.baseRevision()
                        : draft.view()
                            .revision());
        });
    }

    private void review(VehicleScheduleUI.Session draft, boolean proceed) {
        syncToServer(REVIEW, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(
                draft.view()
                    .revision());
            buf.writeBoolean(proceed);
        });
    }

    private void searchLanding(VehicleScheduleUI.Session draft) {
        syncToServer(SEARCH_LANDING, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(
                draft.view()
                    .revision());
            buf.writeLong(draft.landingQuery());
            ByteBufUtils.writeTag(buf, CelestialObjectKeyState.encode(draft.manualBody()));
            buf.writeBoolean(
                !draft.manualX()
                    .isBlank());
            if (!draft.manualX()
                .isBlank()) buf.writeInt(Integer.parseInt(draft.manualX()));
            buf.writeBoolean(
                !draft.manualZ()
                    .isBlank());
            if (!draft.manualZ()
                .isBlank()) buf.writeInt(Integer.parseInt(draft.manualZ()));
        });
    }

    private void acceptLanding(VehicleScheduleUI.Session draft) {
        syncToServer(ACCEPT_LANDING, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(
                draft.view()
                    .revision());
            ByteBufUtils.writeTag(
                buf,
                draft.landingSite()
                    .serializeNBT());
            buf.writeDouble(
                draft.manualRoute()
                    .allowance()
                    .orElseThrow());
            PacketUtil.writeString(
                buf,
                draft.manualRoute()
                    .priority()
                    .name());
        });
    }

    private void cancelLanding(VehicleScheduleUI.Session draft) {
        syncToServer(CANCEL_LANDING, buf -> {
            PacketUtil.writeId(
                buf,
                draft.view()
                    .vehicleId());
            buf.writeLong(
                draft.view()
                    .revision());
        });
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == LANDING_RESULT) {
            UUID vehicle = PacketUtil.readId(buf);
            long revision = buf.readLong();
            long query = buf.readLong();
            var site = buf.readBoolean() ? VehicleSchedule.Place.deserializeNBT(ByteBufUtils.readTag(buf)) : null;
            String problem = buf.readBoolean() ? PacketUtil.readString(buf) : null;
            session.receiveLandingSearch(vehicle, revision, query, site, problem);
            return;
        }
        if (id == FAILURE) {
            session.fail(PacketUtil.readString(buf));
            return;
        }
        if (id != STATE) return;
        var view = Rocket.ScheduleView.deserializeNBT(ByteBufUtils.readTag(buf));
        var catalog = readCatalog(ByteBufUtils.readTag(buf));
        int count = buf.readVarIntFromBuffer();
        if (count < 0 || count > buf.readableBytes() / 16) throw new IOException("Invalid vehicle list length");
        var vehicles = new ArrayList<UUID>();
        for (int i = 0; i < count; i++) vehicles.add(PacketUtil.readId(buf));
        boolean acknowledgement = buf.readBoolean();
        String problem = buf.readBoolean() ? PacketUtil.readString(buf) : null;
        session.vehicles(vehicles, this::select);
        session.receive(view, catalog, problem, acknowledgement);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        if (id != SAVE && id != SELECT
            && id != START
            && id != REVIEW
            && id != SEARCH_LANDING
            && id != ACCEPT_LANDING
            && id != CANCEL_LANDING
            && id != OPEN_BODY_MAP
            && id != RETURN_BODY_MAP) return;
        if (!(getSyncManager().getPlayer() instanceof EntityPlayerMP player)) return;
        PacketBuffer queued = new PacketBuffer(buf.copy());
        ServerTickTaskQueue.schedule(() -> {
            try {
                if (!isValid()) return;
                UUID vehicleId = PacketUtil.readId(queued);
                Rocket rocket = Galaxia.proxy.serverRuntime()
                    .rockets()
                    .find(vehicleId);
                if (rocket == null || !rocket.teamId()
                    .equals(GTTeamsCompat.getTeam(player))
                    || fixedVehicle != null && !vehicleId.equals(selectedVehicle)) {
                    failure(PREFIX + "forbidden");
                    return;
                }
                if (id == SELECT) {
                    selectedVehicle = vehicleId;
                    landingSearch = null;
                    publish(player, rocket, false, null);
                } else {
                    if (!GTTeamsCompat.hasPermission(rocket.teamId(), player, TeamAction.CONFIGURE_LOGISTICS)) {
                        failure(PREFIX + "forbidden");
                        return;
                    }
                    if (id == OPEN_BODY_MAP || id == RETURN_BODY_MAP) {
                        var state = ByteBufUtils.readTag(queued);
                        CelestialObjectKey selectedBody = id == RETURN_BODY_MAP && queued.readBoolean()
                            ? PacketUtil.readCelestialObjectKey(queued)
                            : null;
                        navigate(player, rocket, state, id == OPEN_BODY_MAP, selectedBody);
                        return;
                    }
                    long revision = queued.readLong();
                    Rocket.CommandResult result;
                    if (id == SEARCH_LANDING) {
                        landingSearch = null;
                        long query = queued.readLong();
                        var body = CelestialObjectKeyState.decode(ByteBufUtils.readTag(queued));
                        Integer x = queued.readBoolean() ? queued.readInt() : null;
                        Integer z = queued.readBoolean() ? queued.readInt() : null;
                        if (!knownBodies(player, rocket).contains(body)) {
                            landingResult(vehicleId, revision, query, null, PREFIX + "forbidden");
                            return;
                        }
                        landingVehicle = vehicleId;
                        landingRevision = revision;
                        landingQuery = query;
                        landingSearch = Galaxia.proxy.serverRuntime()
                            .beginRocketLandingSearch(player, vehicleId, revision, body, x, z);
                        if (landingSearch == null) landingResult(
                            vehicleId,
                            revision,
                            query,
                            null,
                            PREFIX + (revision == rocket.revision() ? "blocked" : "stale"));
                        return;
                    } else if (id == ACCEPT_LANDING) {
                        landingSearch = null;
                        var exactSite = VehicleSchedule.Place.deserializeNBT(ByteBufUtils.readTag(queued));
                        var route = new VehicleSchedule.RouteSettings(
                            queued.readDouble(),
                            RoutePriority.valueOf(PacketUtil.readString(queued)));
                        result = knownBodies(player, rocket).contains(exactSite.body()) ? Galaxia.proxy.serverRuntime()
                            .acceptRocketLanding(player, vehicleId, revision, exactSite, route)
                            : Rocket.CommandResult.FORBIDDEN;
                    } else if (id == CANCEL_LANDING) {
                        landingSearch = null;
                        result = Galaxia.proxy.serverRuntime()
                            .cancelRocketLanding(player, vehicleId, revision);
                    } else if (id == START) {
                        result = Galaxia.proxy.serverRuntime()
                            .startRocket(player, vehicleId, revision);
                    } else if (id == REVIEW) {
                        result = Galaxia.proxy.serverRuntime()
                            .reviewRocketRisk(player, vehicleId, revision, queued.readBoolean());
                    } else {
                        var replacement = VehicleSchedule.deserializeNBT(ByteBufUtils.readTag(queued));
                        var known = knownBodies(player, rocket);
                        if (replacement.visits()
                            .stream()
                            .anyMatch(
                                visit -> !known.contains(
                                    visit.place()
                                        .body()))) {
                            publish(player, rocket, true, PREFIX + "forbidden");
                            return;
                        }
                        result = rocket.saveSchedule(GTTeamsCompat.getTeam(player), revision, replacement);
                    }
                    publish(
                        player,
                        rocket,
                        true,
                        result == Rocket.CommandResult.APPLIED ? null
                            : PREFIX + result.name()
                                .toLowerCase(java.util.Locale.ROOT));
                }
            } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ex) {
                Galaxia.LOG.warn("Rejected Rocket schedule from {}", player.getCommandSenderName(), ex);
                if (isValid()) failure(PREFIX + "invalid_schedule");
            } finally {
                queued.release();
            }
        });
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!(getSyncManager().getPlayer() instanceof EntityPlayerMP player)) return;
        var vehicles = Galaxia.proxy.serverRuntime()
            .rockets()
            .forTeam(GTTeamsCompat.getTeam(player));
        UUID current = selectedVehicle;
        if (current == null && fixedVehicle == null && !vehicles.isEmpty()) current = vehicles.getFirst()
            .id();
        Rocket rocket = current == null ? null
            : Galaxia.proxy.serverRuntime()
                .rockets()
                .find(current);
        if (rocket == null || !rocket.teamId()
            .equals(GTTeamsCompat.getTeam(player))) {
            if (init || lastState != null) failure(PREFIX + "no_vehicle");
            lastState = null;
            return;
        }
        selectedVehicle = current;
        if (landingSearch != null) {
            if (!current.equals(landingVehicle) || rocket.revision() != landingRevision
                || !GTTeamsCompat.hasPermission(rocket.teamId(), player, TeamAction.CONFIGURE_LOGISTICS)) {
                landingSearch = null;
                landingResult(landingVehicle, landingRevision, landingQuery, null, PREFIX + "stale");
            } else {
                landingSearch.advance();
                if (landingSearch.done()) {
                    landingResult(
                        current,
                        landingRevision,
                        landingQuery,
                        landingSearch.result(),
                        landingSearch.problemKey());
                    landingSearch = null;
                }
            }
        }
        var state = rocket.scheduleView()
            .serializeNBT();
        if (init || !state.equals(lastState) || --catalogTicks <= 0) publish(player, rocket, false, null);
    }

    private void landingResult(UUID vehicle, long revision, long query, @Nullable VehicleSchedule.Place site,
        @Nullable String problem) {
        syncToClient(LANDING_RESULT, buf -> {
            PacketUtil.writeId(buf, vehicle);
            buf.writeLong(revision);
            buf.writeLong(query);
            buf.writeBoolean(site != null);
            if (site != null) ByteBufUtils.writeTag(buf, site.serializeNBT());
            buf.writeBoolean(problem != null);
            if (problem != null) PacketUtil.writeString(buf, problem);
        });
    }

    private void failure(String key) {
        syncToClient(FAILURE, buf -> PacketUtil.writeString(buf, key));
    }

    private void publish(EntityPlayerMP player, Rocket rocket, boolean acknowledgement, @Nullable String problem) {
        var view = rocket.scheduleView();
        var catalog = catalog(player, rocket);
        var fleet = fixedVehicle == null ? Galaxia.proxy.serverRuntime()
            .rockets()
            .forTeam(rocket.teamId())
            .stream()
            .map(Rocket::id)
            .toList() : List.of(rocket.id());
        syncToClient(STATE, buf -> {
            ByteBufUtils.writeTag(buf, view.serializeNBT());
            ByteBufUtils.writeTag(buf, writeCatalog(catalog));
            buf.writeVarIntToBuffer(fleet.size());
            for (UUID id : fleet) PacketUtil.writeId(buf, id);
            buf.writeBoolean(acknowledgement);
            buf.writeBoolean(problem != null);
            if (problem != null) PacketUtil.writeString(buf, problem);
        });
        lastState = view.serializeNBT();
        catalogTicks = 20;
    }

    private static Set<CelestialObjectKey> knownBodies(EntityPlayerMP player, Rocket rocket) {
        Set<CelestialObjectKey> known = new LinkedHashSet<>();
        known.add(rocket.bodyKey());
        rocket.schedule()
            .visits()
            .forEach(
                visit -> known.add(
                    visit.place()
                        .body()));
        var manual = rocket.scheduleView()
            .manualMission();
        if (manual != null) manual.visits()
            .forEach(
                visit -> known.add(
                    visit.place()
                        .body()));
        boolean unlocked = GTTeamsCompat.hasAchievement(player, Achievement.STAR_MAP_ACTIVATION);
        if (unlocked) {
            for (var object : CelestialRegistry.getAll()) {
                if (CelestialKnowledgeService.facts(rocket.teamId(), object.key())
                    .discoveryState() == DiscoveryState.DISCOVERED && switch (object.objectClass()) {
                    case PLANET, MOON, GAS_GIANT, ASTEROID -> true;
                    default -> false;
                }) known.add(object.key());
            }
            CelestialKnowledgeService.snapshot(rocket.teamId())
                .forEach((key, facts) -> { if (facts.discoveryState() == DiscoveryState.DISCOVERED) known.add(key); });
        }
        return known;
    }

    private static Catalog catalog(EntityPlayerMP player, Rocket rocket) {
        var known = knownBodies(player, rocket);
        var endpoints = new ArrayList<EndpointChoice>();
        var stations = new ArrayList<StationChoice>();
        Set<InventoryKey> resources = new LinkedHashSet<>();
        for (var stack : player.inventory.mainInventory) {
            if (stack == null) continue;
            resources.add(ItemStackWrapper.of(stack));
            var one = stack.copy();
            one.stackSize = 1;
            var fluid = FluidInteractions.getFluidForItem(one);
            if (fluid != null) resources.add(FluidKey.of(fluid));
        }
        for (var module : rocket.modules()) {
            module.cargoContents()
                .forEach(stack -> resources.add(ItemStackWrapper.of(stack)));
            resources.addAll(
                module.propellantContents()
                    .keySet());
            if (module.freightContents() != null) resources.add(FluidKey.of(module.freightContents()));
        }
        for (var entry : CelestialAssetStore.SERVER.getTeamAssetsInternal(rocket.teamId())
            .entrySet()) {
            if (!known.contains(entry.getKey())) continue;
            for (var asset : entry.getValue()) {
                if (asset.kind == CelestialAsset.Kind.STATION)
                    stations.add(new StationChoice(asset.assetId.id(), entry.getKey(), asset.displayName()));
                var context = asset.location == CelestialAsset.Location.ORBIT ? VehicleSchedule.Context.ORBIT
                    : VehicleSchedule.Context.SURFACE;
                for (var endpoint : asset.endpoints()
                    .values())
                    endpoints.add(
                        new EndpointChoice(
                            entry.getKey(),
                            context,
                            endpoint.id(),
                            endpoint.kind(),
                            endpoint.routingName(),
                            asset.kind == CelestialAsset.Kind.STATION ? asset.assetId.id() : null));
                if (asset instanceof IDistributedInventory inventory) {
                    resources.addAll(
                        inventory.getItemAmounts()
                            .keySet());
                    resources.addAll(
                        inventory.getFluidAmounts()
                            .keySet());
                }
            }
        }
        return new Catalog(
            known.stream()
                .map(
                    key -> CelestialRegistry.get(key)
                        .orElseThrow())
                .toList(),
            endpoints,
            List.copyOf(resources),
            stations,
            GalaxiaCelestialAPI.currentOrbitalTime(),
            ConfigRocket.flight.costModel(),
            GalaxiaCelestialAPI.root());
    }

    private static NBTTagCompound writeCatalog(Catalog catalog) {
        var out = new NBTTagCompound();
        out.setDouble("epoch", catalog.epochOsu());
        out.setTag(
            "root",
            CelestialObjectKeyState.encode(
                catalog.root()
                    .key()));
        var costs = new NBTTagCompound();
        costs.setDouble(
            "gravityLoss",
            catalog.costModel()
                .gravityLossAtOneGMps());
        costs.setDouble(
            "atmosphereLoss",
            catalog.costModel()
                .atmosphereLossAtOneAtmMps());
        costs.setDouble(
            "landingFraction",
            catalog.costModel()
                .atmosphericLandingCostFraction());
        costs.setDouble(
            "burnFraction",
            catalog.costModel()
                .maxOrbitalBurnTimeScaleFraction());
        costs.setDouble(
            "timeScale",
            catalog.costModel()
                .localTimeScale());
        costs.setDouble(
            "dockingDeltaV",
            catalog.costModel()
                .dockingDeltaVMps());
        costs.setDouble(
            "surfaceSeconds",
            catalog.costModel()
                .minimumSurfaceGameSeconds());
        out.setTag("costs", costs);
        var bodies = new NBTTagList();
        for (var body : catalog.bodies()) {
            var encoded = new NBTTagCompound();
            encoded.setTag("body", CelestialObjectKeyState.encode(body.key()));
            bodies.appendTag(encoded);
        }
        out.setTag("bodies", bodies);
        var endpoints = new NBTTagList();
        for (var endpoint : catalog.endpoints()) {
            var encoded = new NBTTagCompound();
            encoded.setTag("body", CelestialObjectKeyState.encode(endpoint.body()));
            encoded.setString(
                "context",
                endpoint.context()
                    .name());
            encoded.setString(
                "id",
                endpoint.endpointId()
                    .toString());
            encoded.setString(
                "kind",
                endpoint.kind()
                    .name());
            encoded.setString("name", endpoint.routingName());
            if (endpoint.stationId() != null) encoded.setString(
                "station",
                endpoint.stationId()
                    .toString());
            endpoints.appendTag(encoded);
        }
        out.setTag("endpoints", endpoints);
        var resources = new NBTTagList();
        for (var resource : catalog.resources()) resources.appendTag(InventoryKeyState.encode(resource));
        out.setTag("resources", resources);
        var stations = new NBTTagList();
        for (var station : catalog.stations()) {
            var encoded = new NBTTagCompound();
            encoded.setString(
                "id",
                station.stationId()
                    .toString());
            encoded.setTag("body", CelestialObjectKeyState.encode(station.body()));
            encoded.setString("name", station.name());
            stations.appendTag(encoded);
        }
        out.setTag("stations", stations);
        return out;
    }

    private static Catalog readCatalog(NBTTagCompound tag) {
        var in = new NbtReader(tag, "rocketScheduleCatalog");
        var bodies = new ArrayList<CelestialObject>();
        var encodedBodies = in.compounds("bodies");
        for (int i = 0; i < encodedBodies.tagCount(); i++) {
            var body = in.element("bodies", i, encodedBodies.getCompoundTagAt(i));
            bodies.add(
                CelestialRegistry.get(CelestialObjectKeyState.decode(body.compound("body")))
                    .orElseThrow());
        }
        var endpoints = new ArrayList<EndpointChoice>();
        var encodedEndpoints = in.compounds("endpoints");
        for (int i = 0; i < encodedEndpoints.tagCount(); i++) {
            var endpoint = in.element("endpoints", i, encodedEndpoints.getCompoundTagAt(i));
            endpoints.add(
                new EndpointChoice(
                    CelestialObjectKeyState.decode(endpoint.compound("body")),
                    endpoint.enumValue(VehicleSchedule.Context.class, "context"),
                    UUID.fromString(endpoint.string("id")),
                    endpoint.enumValue(CelestialAsset.Endpoint.Kind.class, "kind"),
                    endpoint.string("name"),
                    endpoint.tag()
                        .hasKey("station") ? UUID.fromString(endpoint.string("station")) : null));
        }
        var resources = new ArrayList<InventoryKey>();
        var encodedResources = in.compounds("resources");
        for (int i = 0; i < encodedResources.tagCount(); i++)
            resources.add(InventoryKeyState.decode(encodedResources.getCompoundTagAt(i)));
        var stations = new ArrayList<StationChoice>();
        var encodedStations = in.compounds("stations");
        for (int i = 0; i < encodedStations.tagCount(); i++) {
            var station = in.element("stations", i, encodedStations.getCompoundTagAt(i));
            stations.add(
                new StationChoice(
                    UUID.fromString(station.string("id")),
                    CelestialObjectKeyState.decode(station.compound("body")),
                    station.string("name")));
        }
        var costs = in.compound("costs");
        return new Catalog(
            bodies,
            endpoints,
            resources,
            stations,
            in.doubleValue("epoch"),
            new CostModel(
                costs.doubleValue("gravityLoss"),
                costs.doubleValue("atmosphereLoss"),
                costs.doubleValue("landingFraction"),
                costs.doubleValue("burnFraction"),
                costs.doubleValue("timeScale"),
                costs.doubleValue("dockingDeltaV"),
                costs.doubleValue("surfaceSeconds")),
            CelestialRegistry.get(CelestialObjectKeyState.decode(in.compound("root")))
                .orElseThrow());
    }
}
