package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.core.state.CelestialObjectKeyState;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.CostModel;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.LocalVehicle;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.ForecastEstimate;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;

/** Production editor and its local, explicitly saved draft. */
public final class VehicleScheduleUI {

    private static final String PREFIX = "galaxia.rocket.schedule.";
    private static final String SIMPLE_PREFIX = "galaxia.vehicle.simple.";
    private static final double METRES_PER_KILOMETRE = 1000;
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final int INSET = 10;
    private static final int HEADER = 76;
    private static final int LIST_WIDTH = 268;
    private static final int DETAIL_X = 290;
    private static final int DETAIL_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 274;
    private static final int CARD_HEADER_HEIGHT = 52;
    private static final int ACTION_ROW_HEIGHT = 28;
    private static final int CARD_PADDING = 6;
    private static final int CARD_GAP = 6;
    private static final int LIST_PADDING = 4;
    private static final int CARD_HEADER_INSET = 3;
    private static final int ACTION_INDENT = 20;
    private static final int CARD_BUTTON_GAP = 4;
    private static final int ACTION_BUTTON_HEIGHT = 21;
    private static final int ACTION_EDIT_WIDTH = 40;
    private static final int ACTION_REMOVE_WIDTH = 48;
    private static final int CARD_ACTIONS_HEIGHT = ACTION_BUTTON_HEIGHT + 2 * CARD_BUTTON_GAP;
    private static final int INSPECTOR_HEADER_HEIGHT = 40;
    private static final int SECTION_LABEL_HEIGHT = 18;
    private static final int ACTION_CHOICE_HEIGHT = 40;
    private static final int ACTION_CHOICE_DESCRIPTION_Y = 23;
    private static final int DESTINATION_FIELD_Y = 16;
    private static final int ARRIVAL_LABEL_Y = 48;
    private static final int ARRIVAL_FIELD_Y = 64;
    private static final int ARRIVAL_CONTEXT_WIDTH = 96;
    private static final int ARRIVAL_SITE_X = 102;
    private static final int DOCK_POLICY_Y = 88;
    private static final int TRAVEL_HEADER_Y = 116;
    private static final int TRAVEL_DETAILS_Y = 144;
    private static final int TRAVEL_DETAILS_HEIGHT = 224;
    private static final int SUMMARY_ROW_HEIGHT = 18;
    private static final int BODY_SELECTION_WIDTH = 270;
    private static final int BODY_SELECTION_HEIGHT = 96;
    private static final int WARNING_WIDTH = 440;
    private static final int WARNING_HEIGHT = 208;
    private static final int FOOTER_HEIGHT = 46;
    private static final int TEXT = GalaxiaGuiStyle.TEXT;
    private static final int MUTED = GalaxiaGuiStyle.TEXT_DIM;
    private static final int ACCENT = GalaxiaGuiStyle.ACCENT;

    private VehicleScheduleUI() {}

    public record EndpointChoice(CelestialObjectKey body, VehicleSchedule.Context context, UUID endpointId, Kind kind,
        String routingName, @Nullable UUID stationId) {}

    public record StationChoice(UUID stationId, CelestialObjectKey body, String name) {}

    /** Only server-authorized destinations and resource identities are sent to the editor. */
    public record Catalog(List<CelestialObject> bodies, List<EndpointChoice> endpoints, List<InventoryKey> resources,
        List<StationChoice> stations, double epochOsu, @Nullable CostModel costModel, @Nullable CelestialObject root) {

        public Catalog {
            bodies = List.copyOf(bodies);
            endpoints = List.copyOf(endpoints);
            resources = List.copyOf(resources);
            stations = List.copyOf(stations);
            if (!Double.isFinite(epochOsu)) throw new IllegalArgumentException("Invalid orbital epoch");
        }

        public static Catalog empty() {
            return new Catalog(List.of(), List.of(), List.of(), List.of(), 0, null, null);
        }

        private boolean sameChoices(Catalog other) {
            return bodies.equals(other.bodies) && endpoints.equals(other.endpoints)
                && resources.equals(other.resources)
                && stations.equals(other.stations);
        }
    }

    /** A draft belongs to one open editor. It never mutates the server-owned vehicle. */
    public static final class Session {

        @Nullable
        private Rocket.ScheduleView view;
        @Nullable
        private VehicleSchedule draft;
        private Catalog catalog = Catalog.empty();
        private long baseRevision;
        private boolean pending;
        private boolean pendingSave;
        private boolean overview;
        private boolean manual;
        private boolean simple;
        private boolean simpleDirty;
        private boolean simpleChoosingItem;
        private int simpleScroll;
        private int simpleDestinationsScroll;
        private long simpleBaseRevision;
        @Nullable
        private VehicleSchedule.Place simpleHome;
        @Nullable
        private VehicleSchedule.Place simpleDestination;
        @Nullable
        private CelestialObjectKey simpleBody;
        private final Map<InventoryKey, Long> simpleRequests = new LinkedHashMap<>();
        @Nullable
        private Consumer<Session> saveDelivery;
        @Nullable
        private Consumer<Session> stopDelivery;
        @Nullable
        private Consumer<Session> clearDelivery;
        private boolean pendingDeliverySave;
        private boolean pendingDeliveryClear;
        private boolean manualDirty;
        private boolean pendingLanding;
        private boolean pendingLandingCancellation;
        @Nullable
        private CelestialObjectKey manualBody;
        private String manualX = "";
        private String manualZ = "";
        private VehicleSchedule.RouteSettings manualRoute = new VehicleSchedule.RouteSettings(
            OptionalDouble.empty(),
            RoutePriority.PRIORITIZE_DV);
        @Nullable
        private VehicleSchedule.Place landingSite;
        private long landingRevision;
        private long landingQuery;
        private boolean searchingLanding;
        @Nullable
        private String landingProblem;
        @Nullable
        private Consumer<Session> searchLanding;
        @Nullable
        private Consumer<Session> acceptLanding;
        @Nullable
        private Consumer<Session> cancelLanding;
        @Nullable
        private String problemKey;
        @Nullable
        private UUID selectedVisit;
        @Nullable
        private UUID selectedAction;
        @Nullable
        private VehicleSchedule.Leg selectedLeg;
        private Picker picker = Picker.NONE;
        private String search = "";
        private Kind groupKind = Kind.LANDING_PAD;
        @Nullable
        private UUID groupStation;
        private int renderVersion;
        private Consumer<Session> save = ignored -> {};
        @Nullable
        private Consumer<Session> start;
        @Nullable
        private BiConsumer<Session, Boolean> review;
        private Consumer<UUID> selectVehicle = ignored -> {};
        private List<UUID> vehicles = List.of();
        @Nullable
        private UUID navigationVehicle;
        private long navigationRevision;
        private int listScroll;
        private int inspectorScroll;
        private int choicesScroll;
        private boolean navigating;
        @Nullable
        private Consumer<Session> bodyMap;

        public Session bodyMap(Consumer<Session> open) {
            bodyMap = open;
            return this;
        }

        public void requestBodyMap() {
            if (view == null || pending
                || warningRequired()
                || bodyMap == null
                || simple && !simpleEditable()
                || !manual && !simple && (selectedVisit == null || !editable(selectedVisit))) return;
            navigating = true;
            bodyMap.accept(this);
        }

        public boolean canSelectDestinationBody(@Nullable CelestialObjectKey body) {
            return body != null && view != null
                && !pending
                && !warningRequired()
                && catalog.bodies()
                    .stream()
                    .anyMatch(
                        candidate -> candidate.key()
                            .equals(body))
                && (simple ? simpleEditable() && simpleDestinations(body).size() > 0
                    : manual ? view.status() == Rocket.Status.PHYSICAL
                        : selectedVisit != null && visit() != null && editable(selectedVisit));
        }

        public boolean selectDestinationBody(CelestialObjectKey body) {
            if (!canSelectDestinationBody(body)) return false;
            if (simple) {
                if (!body.equals(simpleBody)) {
                    simpleBody = body;
                    simpleDestination = null;
                    simpleDestinationsScroll = 0;
                    simpleDirty = true;
                    changed();
                }
            } else if (manual) manualDestination(body, manualX, manualZ);
            else if (!visit().place()
                .body()
                .equals(body))
                replacePlace(
                    new VehicleSchedule.Place(
                        body,
                        visit().place()
                            .context(),
                        null));
            return true;
        }

        /** Client UI state only. Its original revision remains the Save precondition after navigation. */
        public NBTTagCompound navigationState() {
            var out = new NBTTagCompound();
            out.setString(
                "vehicle",
                view.vehicleId()
                    .toString());
            out.setLong("viewRevision", view.revision());
            out.setLong("baseRevision", baseRevision);
            out.setTag("draft", draft.serializeNBT());
            out.setBoolean("manual", manual);
            out.setBoolean("manualDirty", manualDirty);
            out.setBoolean("overview", overview);
            out.setBoolean("simple", simple);
            out.setBoolean("simpleDirty", simpleDirty);
            out.setLong("simpleRevision", simpleBaseRevision);
            out.setInteger("simpleScroll", simpleScroll);
            out.setInteger("simpleDestinationsScroll", simpleDestinationsScroll);
            out.setBoolean("simpleChoosingItem", simpleChoosingItem);
            out.setString("simpleSearch", simple ? search : "");
            if (simpleHome != null) out.setTag("simpleHome", simpleHome.serializeNBT());
            if (simpleDestination != null) out.setTag("simpleDestination", simpleDestination.serializeNBT());
            if (simpleBody != null) out.setTag("simpleBody", CelestialObjectKeyState.encode(simpleBody));
            out.setTag("simpleRequests", InventoryKeyState.encodeAmounts(simpleRequests));
            if (manualBody != null) out.setTag("manualBody", CelestialObjectKeyState.encode(manualBody));
            out.setString("manualX", manualX);
            out.setString("manualZ", manualZ);
            if (manualRoute.allowance()
                .isPresent())
                out.setDouble(
                    "manualAllowance",
                    manualRoute.allowance()
                        .getAsDouble());
            out.setString(
                "manualPriority",
                manualRoute.priority()
                    .name());
            if (landingSite != null) out.setTag("landingSite", landingSite.serializeNBT());
            out.setLong("landingRevision", landingRevision);
            out.setLong("landingQuery", landingQuery);
            if (selectedVisit != null) out.setString("visit", selectedVisit.toString());
            if (selectedAction != null) out.setString("action", selectedAction.toString());
            if (selectedLeg != null) out.setTag("leg", selectedLeg.serializeNBT());
            out.setInteger("listScroll", listScroll);
            out.setInteger("inspectorScroll", inspectorScroll);
            out.setInteger("choicesScroll", choicesScroll);
            return out;
        }

        public void restoreNavigation(NBTTagCompound encoded) {
            if (encoded == null) throw new IllegalArgumentException("Missing rocket navigation state");
            try {
                var in = new NbtReader(encoded, "rocketNavigation");
                navigationVehicle = UUID.fromString(in.string("vehicle"));
                navigationRevision = in.longValue("viewRevision");
                baseRevision = in.longValue("baseRevision");
                if (baseRevision < 0 || navigationRevision < 0)
                    throw new IllegalArgumentException("Invalid navigation revision");
                draft = VehicleSchedule.deserializeNBT(
                    in.compound("draft")
                        .tag());
                manual = in.bool("manual");
                manualDirty = in.bool("manualDirty");
                overview = in.bool("overview");
                simple = in.bool("simple");
                simpleDirty = in.bool("simpleDirty");
                simpleBaseRevision = in.longValue("simpleRevision");
                if (simpleBaseRevision < 0) throw new IllegalArgumentException("Invalid delivery revision");
                simpleScroll = in.integer("simpleScroll", 0, Integer.MAX_VALUE);
                simpleDestinationsScroll = in.integer("simpleDestinationsScroll", 0, Integer.MAX_VALUE);
                simpleChoosingItem = in.bool("simpleChoosingItem");
                if (simple) search = in.string("simpleSearch");
                simpleHome = encoded.hasKey("simpleHome") ? VehicleSchedule.Place.deserializeNBT(
                    in.compound("simpleHome")
                        .tag())
                    : null;
                simpleDestination = encoded.hasKey("simpleDestination") ? VehicleSchedule.Place.deserializeNBT(
                    in.compound("simpleDestination")
                        .tag())
                    : null;
                simpleBody = encoded.hasKey("simpleBody") ? CelestialObjectKeyState.decode(
                    in.compound("simpleBody")
                        .tag())
                    : null;
                simpleRequests.clear();
                var requests = InventoryKeyState.decodeAmounts(in, "simpleRequests");
                for (var request : requests.entrySet()) {
                    if (!(request.getKey() instanceof ItemStackWrapper) || request.getValue() <= 0)
                        throw new IllegalArgumentException("Invalid simple delivery request");
                }
                simpleRequests.putAll(requests);
                manualBody = encoded.hasKey("manualBody") ? CelestialObjectKeyState.decode(
                    in.compound("manualBody")
                        .tag())
                    : null;
                manualX = in.string("manualX");
                manualZ = in.string("manualZ");
                if (!manualX.isBlank()) Integer.parseInt(manualX);
                if (!manualZ.isBlank()) Integer.parseInt(manualZ);
                manualRoute = new VehicleSchedule.RouteSettings(
                    encoded.hasKey("manualAllowance") ? OptionalDouble.of(in.doubleValue("manualAllowance"))
                        : OptionalDouble.empty(),
                    in.enumValue(RoutePriority.class, "manualPriority"));
                landingSite = encoded.hasKey("landingSite") ? VehicleSchedule.Place.deserializeNBT(
                    in.compound("landingSite")
                        .tag())
                    : null;
                landingRevision = in.longValue("landingRevision");
                landingQuery = in.longValue("landingQuery");
                selectedVisit = encoded.hasKey("visit") ? UUID.fromString(in.string("visit")) : null;
                selectedAction = encoded.hasKey("action") ? UUID.fromString(in.string("action")) : null;
                selectedLeg = encoded.hasKey("leg") ? VehicleSchedule.Leg.deserializeNBT(
                    in.compound("leg")
                        .tag())
                    : null;
                listScroll = in.integer("listScroll", 0, Integer.MAX_VALUE);
                inspectorScroll = in.integer("inspectorScroll", 0, Integer.MAX_VALUE);
                choicesScroll = in.integer("choicesScroll", 0, Integer.MAX_VALUE);
                picker = Picker.NONE;
                navigating = false;
                changed();
            } catch (IllegalStateException invalid) {
                throw new IllegalArgumentException("Invalid rocket navigation state", invalid);
            }
        }

        public Session commands(Consumer<Session> save, @Nullable Consumer<Session> start,
            @Nullable BiConsumer<Session, Boolean> review) {
            this.save = save;
            this.start = start;
            this.review = review;
            return this;
        }

        public Session deliveryCommands(Consumer<Session> save, Consumer<Session> stop, Consumer<Session> clear) {
            saveDelivery = save;
            stopDelivery = stop;
            clearDelivery = clear;
            return this;
        }

        public void showSimple() {
            if (pending || warningRequired()) return;
            simple = true;
            manual = false;
            overview = false;
            picker = Picker.NONE;
            changed();
        }

        public void showAdvanced() {
            if (pending || warningRequired()) return;
            simple = false;
            manual = false;
            overview = false;
            picker = Picker.NONE;
            changed();
        }

        public boolean simpleMode() {
            return simple;
        }

        public long simpleBaseRevision() {
            return simpleBaseRevision;
        }

        public boolean simpleEditable() {
            return view != null && !pending
                && !warningRequired()
                && view.status() == Rocket.Status.PHYSICAL
                && view.manualMission() == null
                && !view.running()
                && (view.delivery() == null || !view.delivery()
                    .enabled());
        }

        private List<EndpointChoice> simpleDestinations(@Nullable CelestialObjectKey body) {
            return catalog.endpoints()
                .stream()
                .filter(
                    endpoint -> endpoint.kind() == Kind.SILO && endpoint.body()
                        .equals(body))
                .filter(
                    endpoint -> simpleHome == null || !(simpleHome.target() instanceof VehicleSchedule.Exact home)
                        || !home.endpointId()
                            .equals(endpoint.endpointId()))
                .toList();
        }

        public void simpleDestination(EndpointChoice endpoint) {
            if (!simpleEditable() || !simpleDestinations(endpoint.body()).contains(endpoint)) return;
            var selected = new VehicleSchedule.Place(
                endpoint.body(),
                endpoint.context(),
                new VehicleSchedule.Exact(endpoint.endpointId(), endpoint.kind()));
            if (selected.equals(simpleDestination)) return;
            simpleBody = endpoint.body();
            simpleDestination = selected;
            simpleDirty = true;
            changed();
        }

        public void simpleRequest(ItemStackWrapper resource, long amount) {
            if (!simpleEditable()) return;
            if (amount < 0) throw new IllegalArgumentException("Delivery amount must be nonnegative");
            if (amount == 0) {
                if (simpleRequests.remove(resource) == null) return;
            } else if (Long.valueOf(amount)
                .equals(simpleRequests.put(resource, amount))) return;
            simpleDirty = true;
            changed();
        }

        @Nullable
        public Rocket.DeliveryPlan simplePlan() {
            if (!hasSimplePlan()) return null;
            return new Rocket.DeliveryPlan(simpleHome, simpleDestination, simpleRequests);
        }

        private boolean hasSimplePlan() {
            return simpleHome != null && simpleHome.target() instanceof VehicleSchedule.Exact home
                && home.kind() == Kind.SILO
                && simpleDestination != null
                && !simpleRequests.isEmpty();
        }

        private boolean newDeliveryOrder() {
            if (view == null || view.delivery() == null) return false;
            var accepted = view.delivery()
                .plan();
            return !java.util.Objects.equals(simpleHome, accepted.home())
                || !java.util.Objects.equals(simpleDestination, accepted.destination())
                || !simpleRequests.equals(accepted.requested());
        }

        private void restoreSimple() {
            var delivery = view.delivery();
            simpleHome = delivery == null ? view.place()
                : delivery.plan()
                    .home();
            simpleDestination = delivery == null ? null
                : delivery.plan()
                    .destination();
            simpleBody = simpleDestination == null ? simpleHome.body() : simpleDestination.body();
            simpleRequests.clear();
            if (delivery != null) simpleRequests.putAll(
                delivery.plan()
                    .requested());
            simpleDirty = false;
            simpleBaseRevision = view.revision();
        }

        public void requestDeliverySave() {
            if (!simpleEditable() || dirty() || !hasSimplePlan() || saveDelivery == null) return;
            pending = true;
            pendingSave = false;
            pendingDeliverySave = true;
            problemKey = null;
            changed();
            saveDelivery.accept(this);
        }

        public void requestDeliveryStop() {
            if (view == null || view.delivery() == null
                || !view.delivery()
                    .enabled()
                || pending
                || stopDelivery == null) return;
            pending = true;
            pendingSave = false;
            changed();
            stopDelivery.accept(this);
        }

        public void requestDeliveryClear() {
            if (!simpleEditable() || view.delivery() == null
                || view.delivery()
                    .enabled()
                || clearDelivery == null) return;
            pending = true;
            pendingSave = false;
            pendingDeliveryClear = true;
            changed();
            clearDelivery.accept(this);
        }

        public Session manualCommands(Consumer<Session> search, Consumer<Session> accept, Consumer<Session> cancel) {
            searchLanding = search;
            acceptLanding = accept;
            cancelLanding = cancel;
            return this;
        }

        public boolean manualMode() {
            return manual;
        }

        public CelestialObjectKey manualBody() {
            return manualBody;
        }

        public String manualX() {
            return manualX;
        }

        public String manualZ() {
            return manualZ;
        }

        public VehicleSchedule.RouteSettings manualRoute() {
            return manualRoute;
        }

        public VehicleSchedule.Place landingSite() {
            return landingSite;
        }

        public long landingQuery() {
            return landingQuery;
        }

        public boolean searchingLanding() {
            return searchingLanding;
        }

        public void openManual() {
            if (view == null || pending || warningRequired() || view.delivery() != null) return;
            manual = true;
            overview = false;
            picker = Picker.NONE;
            if (manualBody == null) {
                manualBody = view.place()
                    .body();
                manualRoute = new VehicleSchedule.RouteSettings(
                    view.propulsion() == null ? OptionalDouble.empty()
                        : OptionalDouble.of(
                            Math.floor(
                                view.propulsion()
                                    .usableDeltaVMps())),
                    RoutePriority.PRIORITIZE_DV);
            }
            changed();
        }

        public void manualDestination(CelestialObjectKey body, String x, String z) {
            if (pending || warningRequired()) return;
            if (!x.isBlank()) Integer.parseInt(x);
            if (!z.isBlank()) Integer.parseInt(z);
            if (java.util.Objects.equals(manualBody, body) && manualX.equals(x) && manualZ.equals(z)) return;
            manualDirty = true;
            manualBody = body;
            manualX = x;
            manualZ = z;
            invalidateLanding();
        }

        public void manualRoute(VehicleSchedule.RouteSettings route) {
            if (pending || warningRequired()) return;
            manualDirty = true;
            manualRoute = route;
            changed();
        }

        private void invalidateLanding() {
            landingQuery++;
            landingSite = null;
            searchingLanding = false;
            landingProblem = null;
            changed();
        }

        public void requestLandingSearch() {
            if (view == null || manualBody == null || pending || warningRequired() || searchLanding == null) return;
            invalidateLanding();
            landingRevision = view.revision();
            manualDirty = true;
            searchingLanding = true;
            searchLanding.accept(this);
        }

        public void receiveLandingSearch(UUID vehicle, long revision, long query, @Nullable VehicleSchedule.Place site,
            @Nullable String problem) {
            if (view == null || !view.vehicleId()
                .equals(vehicle) || view.revision() != revision || landingRevision != revision || landingQuery != query)
                return;
            searchingLanding = false;
            landingSite = site;
            landingProblem = problem;
            changed();
        }

        public boolean canAcceptLanding() {
            return view != null && view.status() == Rocket.Status.PHYSICAL
                && landingSite != null
                && landingSite.surfaceSite() != null
                && landingRevision == view.revision()
                && !pending
                && !warningRequired()
                && (manualDirty || view.manualMission() == null)
                && manualRoute.allowance()
                    .isPresent()
                && acceptLanding != null;
        }

        public void requestLandingAcceptance() {
            if (!canAcceptLanding()) return;
            pending = true;
            pendingSave = false;
            problemKey = null;
            changed();
            pendingLanding = true;
            acceptLanding.accept(this);
        }

        public void requestLandingCancellation() {
            if (view == null || pending || warningRequired()) return;
            if (view.manualMission() != null && cancelLanding != null) {
                pending = true;
                pendingSave = false;
                pendingLandingCancellation = true;
                changed();
                cancelLanding.accept(this);
                return;
            }
            manual = false;
            invalidateLanding();
        }

        private void resetManual() {
            manual = false;
            manualDirty = false;
            manualBody = null;
            manualX = "";
            manualZ = "";
            manualRoute = new VehicleSchedule.RouteSettings(OptionalDouble.empty(), RoutePriority.PRIORITIZE_DV);
        }

        private void restoreManual(VehicleSchedule mission) {
            var destination = mission.visits()
                .getLast()
                .place();
            var site = destination.surfaceSite();
            manualDirty = false;
            manualBody = destination.body();
            manualX = Integer.toString(site.x());
            manualZ = Integer.toString(site.z());
            manualRoute = mission.routes()
                .get(
                    new VehicleSchedule.Leg(
                        mission.visits()
                            .getFirst()
                            .id(),
                        mission.visits()
                            .getLast()
                            .id()));
            landingSite = destination;
            landingRevision = view.revision();
        }

        public void vehicles(List<UUID> vehicles, Consumer<UUID> selectVehicle) {
            boolean changed = !this.vehicles.equals(vehicles);
            this.vehicles = List.copyOf(vehicles);
            this.selectVehicle = selectVehicle;
            if (changed) changed();
        }

        public void receive(Rocket.ScheduleView view, Catalog catalog, String problemKey, boolean acknowledgement) {
            boolean restoring = navigationVehicle != null && navigationVehicle.equals(view.vehicleId());
            boolean sameVehicle = restoring || this.view != null && this.view.vehicleId()
                .equals(view.vehicleId());
            boolean retainDraft = sameVehicle && this.draft != null && (restoring || dirty());
            boolean sameManual = this.view != null && sameVehicle
                && java.util.Objects.equals(this.view.manualMission(), view.manualMission());
            boolean sameSchedule = this.view != null && sameVehicle
                && this.view.schedule()
                    .equals(view.schedule());
            boolean contentChanged = this.view == null || !this.view.lockedVisitIds()
                .equals(view.lockedVisitIds())
                || !java.util.Objects.equals(this.view.launchWarning(), view.launchWarning())
                || !java.util.Objects.equals(this.view.manualMission(), view.manualMission())
                || !java.util.Objects.equals(this.view.delivery(), view.delivery())
                || !this.catalog.sameChoices(catalog)
                || acknowledgement && pending
                || overview && !java.util.Objects.equals(this.view, view);
            var previousDraft = this.draft;
            if (!sameVehicle
                || (restoring ? navigationRevision != view.revision() : this.view.revision() != view.revision()))
                invalidateLanding();
            if (!sameVehicle) {
                resetManual();
            }
            this.view = view;
            this.catalog = catalog;
            if (!sameVehicle || !restoring && !simpleDirty
                || acknowledgement && problemKey == null && (pendingDeliverySave || pendingDeliveryClear))
                restoreSimple();
            if (!sameVehicle) simple = view.delivery() != null;
            if (acknowledgement && pendingDeliveryClear && problemKey == null) simple = false;
            if (!restoring && sameVehicle && !sameManual && view.manualMission() == null && !manualDirty) {
                resetManual();
            }
            if (view.manualMission() != null
                && (!manualDirty || acknowledgement && pendingLanding && problemKey == null)) {
                if (!sameManual || acknowledgement && pendingLanding) manual = true;
                restoreManual(view.manualMission());
            }
            if (!retainDraft || (acknowledgement && pendingSave && problemKey == null)) {
                this.draft = view.schedule();
                baseRevision = view.revision();
            } else if (acknowledgement && !pendingSave && sameSchedule && problemKey == null) {
                baseRevision = view.revision();
            }
            if (acknowledgement) {
                if (pendingLandingCancellation && view.manualMission() == null && problemKey == null) {
                    resetManual();
                }
                pendingLandingCancellation = false;
                pending = false;
                pendingSave = false;
                pendingLanding = false;
                pendingDeliverySave = false;
                pendingDeliveryClear = false;
            }
            if (acknowledgement || problemKey != null || !sameVehicle) this.problemKey = problemKey;
            if (!sameVehicle) {
                selectedVisit = null;
                selectedAction = null;
                selectedLeg = null;
                overview = false;
            }
            if (selectedVisit == null && !draft.visits()
                .isEmpty())
                selectedVisit = draft.visits()
                    .getFirst()
                    .id();
            navigationVehicle = null;
            if (contentChanged || !java.util.Objects.equals(previousDraft, draft)) changed();
        }

        public VehicleSchedule draft() {
            return draft;
        }

        public Rocket.ScheduleView view() {
            return view;
        }

        public long baseRevision() {
            return baseRevision;
        }

        public boolean pending() {
            return pending;
        }

        public boolean dirty() {
            return draft != null && view != null && !draft.equals(view.schedule());
        }

        public void fail(String key) {
            navigating = false;
            pending = false;
            pendingSave = false;
            pendingLanding = false;
            pendingLandingCancellation = false;
            pendingDeliverySave = false;
            pendingDeliveryClear = false;
            searchingLanding = false;
            problemKey = key;
            changed();
        }

        public boolean editable(UUID visitId) {
            return view != null && !pending
                && view.delivery() == null
                && !warningRequired()
                && !view.lockedVisitIds()
                    .contains(visitId);
        }

        private boolean warningRequired() {
            return view != null && view.launchWarning() != null;
        }

        private boolean safeDurability() {
            if (view != null && (manual || warningRequired() && view.manualMission() != null)) {
                return (view.manualMission() == null ? view.schedule() : view.manualMission()).requireSafeDurability();
            }
            return draft == null || draft.requireSafeDurability();
        }

        private void changed() {
            renderVersion++;
        }

        public void edit(VehicleSchedule replacement) {
            if (pending || view == null || warningRequired() || view.delivery() != null) return;
            draft = replacement;
            problemKey = null;
            changed();
        }

        public void addVisit() {
            if (view == null || pending || warningRequired() || view.delivery() != null) return;
            var id = UUID.randomUUID();
            var place = draft.visits()
                .isEmpty() ? view.place()
                    : new VehicleSchedule.Place(
                        draft.visits()
                            .getLast()
                            .place()
                            .body(),
                        VehicleSchedule.Context.ORBIT,
                        null);
            edit(draft.withVisit(new VehicleSchedule.Visit(id, place, List.of())));
            selectVisit(id);
        }

        public void selectVisit(UUID id) {
            selectedVisit = id;
            selectedAction = null;
            selectedLeg = null;
            picker = Picker.NONE;
            changed();
        }

        private VehicleSchedule.Visit visit() {
            return draft == null ? null
                : draft.visits()
                    .stream()
                    .filter(
                        v -> v.id()
                            .equals(selectedVisit))
                    .findFirst()
                    .orElse(null);
        }

        public void replaceVisit(VehicleSchedule.Visit replacement) {
            if (!editable(replacement.id())) return;
            edit(draft.withVisit(replacement));
        }

        private void replacePlace(VehicleSchedule.Place place) {
            var visit = visit();
            if (visit != null) replacePlace(visit.id(), place);
        }

        private void replacePlace(UUID visitId, VehicleSchedule.Place place) {
            var visit = draft.visits()
                .stream()
                .filter(
                    candidate -> candidate.id()
                        .equals(visitId))
                .findFirst()
                .orElse(null);
            if (visit != null)
                replaceVisit(new VehicleSchedule.Visit(visit.id(), place, visit.actions(), visit.blockDockActions()));
        }

        private void replaceAction(VehicleSchedule.Action replacement) {
            var visit = draft.visits()
                .stream()
                .filter(
                    candidate -> candidate.actions()
                        .stream()
                        .anyMatch(
                            action -> action.id()
                                .equals(replacement.id())))
                .findFirst()
                .orElse(null);
            if (visit == null || !editable(visit.id())) return;
            var actions = new ArrayList<>(visit.actions());
            int index = -1;
            for (int i = 0; i < actions.size(); i++) if (actions.get(i)
                .id()
                .equals(replacement.id())) index = i;
            if (index < 0) return;
            actions.set(index, replacement);
            replaceVisit(new VehicleSchedule.Visit(visit.id(), visit.place(), actions, visit.blockDockActions()));
        }

        private void removeVisit(UUID id) {
            if (!editable(id)) return;
            edit(draft.withoutVisit(id));
            selectedVisit = draft.visits()
                .isEmpty() ? null
                    : draft.visits()
                        .getFirst()
                        .id();
            selectedAction = null;
            selectedLeg = null;
        }

        public void moveVisit(UUID id, int offset) {
            if (!editable(id)) return;
            var ids = new ArrayList<>(
                draft.visits()
                    .stream()
                    .map(VehicleSchedule.Visit::id)
                    .toList());
            int index = ids.indexOf(id);
            int target = index + offset;
            if (index < 0 || target < 0 || target >= ids.size() || !editable(ids.get(target))) return;
            Collections.swap(ids, index, target);
            edit(draft.reordered(ids));
        }

        public void cancel() {
            if (navigating || view == null) return;
            invalidateLanding();
            resetManual();
            pending = false;
            pendingSave = false;
            pendingLanding = false;
            pendingLandingCancellation = false;
            pendingDeliverySave = false;
            pendingDeliveryClear = false;
            restoreSimple();
            simpleChoosingItem = false;
            draft = view.schedule();
            baseRevision = view.revision();
            problemKey = null;
            picker = Picker.NONE;
            selectedAction = null;
            selectedLeg = null;
            selectedVisit = draft.visits()
                .isEmpty() ? null
                    : draft.visits()
                        .getFirst()
                        .id();
            changed();
        }

        public void moveAction(UUID id, int offset) {
            var visit = visit();
            if (visit == null || !editable(visit.id())) return;
            var actions = new ArrayList<>(visit.actions());
            int index = -1;
            for (int i = 0; i < actions.size(); i++) if (actions.get(i)
                .id()
                .equals(id)) index = i;
            int target = index + offset;
            if (index < 0 || target < 0 || target >= actions.size()) return;
            Collections.swap(actions, index, target);
            replaceVisit(new VehicleSchedule.Visit(visit.id(), visit.place(), actions, visit.blockDockActions()));
        }

        private void removeAction(UUID id) {
            var visit = visit();
            if (visit == null) return;
            replaceVisit(
                new VehicleSchedule.Visit(
                    visit.id(),
                    visit.place(),
                    visit.actions()
                        .stream()
                        .filter(
                            action -> !action.id()
                                .equals(id))
                        .toList(),
                    visit.blockDockActions()));
            selectedAction = null;
        }

        public void requestSave() {
            if (view == null || pending || manual || warningRequired() || view.delivery() != null || !dirty()) return;
            pending = true;
            pendingSave = true;
            problemKey = null;
            changed();
            save.accept(this);
        }

        public void requestStart() {
            if (view == null || pending
                || view.delivery() != null
                || warningRequired()
                || !manual && view.manualMission() != null
                || dirty() && view.manualMission() == null
                || start == null) return;
            pending = true;
            pendingSave = false;
            problemKey = null;
            changed();
            start.accept(this);
        }

        public void requestRiskReview(boolean proceed) {
            if (!warningRequired() || pending || review == null) return;
            pending = true;
            pendingSave = false;
            problemKey = null;
            if (!proceed) {
                if (view.manualMission() != null) manual = true;
                selectedLeg = view.manualMission() == null ? view.launchWarning()
                    .leg() : null;
                if (selectedLeg != null) selectedVisit = selectedLeg.toVisit();
                selectedAction = null;
                picker = Picker.NONE;
                overview = false;
            }
            changed();
            review.accept(this, proceed);
        }
    }

    private enum Picker {
        NONE,
        ACTION,
        ENDPOINT,
        RESOURCE,
    }

    public static ModularPanel build(Session session) {
        var panel = new ModularPanel("galaxia:vehicle_schedule") {

            @Override
            public float getScale() {
                var screen = getScreen().getScreenArea();
                float fit = Math
                    .min(1f, Math.min((screen.width - 2f * INSET) / WIDTH, (screen.height - 2f * INSET) / HEIGHT));
                return super.getScale() * fit;
            }
        }.size(WIDTH, HEIGHT)
            .background((context, x, y, width, height, theme) -> {
                EnumTextures.GUI_PANEL.getImage()
                    .draw(context, x, y, width, height, theme);
                EnumTextures.GUI_WELL.getImage()
                    .draw(context, x + 4, y + height - FOOTER_HEIGHT, width - 8, FOOTER_HEIGHT - 4, theme);
            });
        BooleanSupplier unsaved = () -> (session.simple ? session.simpleDirty : session.dirty()) || panel.getContext()
            .getFocusedWidget()
            .getElement() instanceof ScheduleField field
            && !field.getText()
                .equals(
                    field.getStringValue()
                        .getStringValue());
        var header = new ParentWidget<>().pos(4, 4)
            .widthRelOffset(1f, -8)
            .height(25)
            .background(EnumTextures.GUI_WELL.getImage());
        panel.child(header);
        panel.child(
            new ParentWidget<>().pos(INSET, 6)
                .size(16, 16)
                .background(EnumTextures.SILO_DESTINATION_ICON.getImage()));
        panel.child(
            IKey.lang(PREFIX + "title")
                .asWidget()
                .pos(34, 10)
                .color(TEXT));
        panel.child(
            buttonText(
                () -> session.view == null ? tr("vehicle")
                    : tr(
                        "vehicle_id",
                        session.view.vehicleId()
                            .toString()
                            .substring(0, 8)),
                () -> {
                    if (session.dirty() || session.simpleDirty || unsaved.getAsBoolean()) {
                        session.fail(PREFIX + "switch_unsaved");
                        return;
                    }
                    int index = session.view == null ? -1 : session.vehicles.indexOf(session.view.vehicleId());
                    if (!session.vehicles.isEmpty())
                        session.selectVehicle.accept(session.vehicles.get((index + 1) % session.vehicles.size()));
                }).name("rocket.schedule.vehicle")
                    .relative(header)
                    .right(40)
                    .top(0)
                    .widthRel(0.35f)
                    .heightRel(1f)
                    .setEnabledIf(
                        widget -> session.vehicles.size() > 1 && !session.pending && !session.warningRequired()));
        panel.child(
            ButtonWidget.panelCloseButton()
                .name("rocket.schedule.close"));
        panel.child(buttonText(() -> simpleText(session.simpleMode() ? "advanced_mode" : "simple_mode"), () -> {
            if (session.simpleMode()) session.showAdvanced();
            else session.showSimple();
        }).name("rocket.schedule.editor_mode")
            .relative(header)
            .left(170)
            .top(0)
            .width(120)
            .heightRel(1f)
            .setEnabledIf(widget -> session.view != null && !session.pending && !session.warningRequired()));
        var advancedControls = new ParentWidget<>().sizeRel(1f)
            .setEnabledIf(widget -> !session.simple);
        panel.child(advancedControls);
        panel.child(
            IKey.lang(SIMPLE_PREFIX + "intro")
                .asWidget()
                .pos(INSET, 35)
                .size(WIDTH - 2 * INSET, 18)
                .color(MUTED)
                .setEnabledIf(widget -> session.simple && !session.manual && !session.warningRequired()));
        panel.child(
            IKey.lang(SIMPLE_PREFIX + "automatic_service")
                .asWidget()
                .pos(INSET, 53)
                .size(WIDTH - 2 * INSET, 14)
                .color(MUTED)
                .setEnabledIf(widget -> session.simpleMode() && !session.manual && !session.warningRequired()));
        advancedControls.child(
            button("add_visit", session::addVisit).primaryWhen(
                () -> session.draft != null && session.draft.visits()
                    .isEmpty())
                .pos(INSET, 35)
                .size(100, 18)
                .setEnabledIf(
                    widget -> session.view != null && session.view.delivery() == null
                        && !session.manual
                        && !session.pending
                        && !session.warningRequired()));
        advancedControls.child(
            buttonText(
                () -> tr(
                    session.draft != null && session.draft.mode() == VehicleSchedule.Mode.LOOP ? "loop" : "run_once"),
                () -> {
                    if (session.draft != null) session.edit(
                        session.draft.withMode(
                            session.draft.mode() == VehicleSchedule.Mode.LOOP ? VehicleSchedule.Mode.RUN_ONCE
                                : VehicleSchedule.Mode.LOOP));
                }).name("rocket.schedule.mode")
                    .pos(117, 35)
                    .size(88, 18)
                    .setEnabledIf(
                        widget -> session.view != null && session.view.delivery() == null
                            && !session.manual
                            && !session.pending
                            && !session.warningRequired()));
        advancedControls.child(
            buttonText(
                () -> tr(session.safeDurability() ? "safe_on" : "safe_off"),
                () -> {
                    if (session.draft != null)
                        session.edit(session.draft.withSafeDurability(!session.draft.requireSafeDurability()));
                }).name("rocket.schedule.safe")
                    .pos(212, 35)
                    .size(180, 18)
                    .setEnabledIf(
                        widget -> session.view != null && session.view.delivery() == null
                            && !session.manual
                            && !session.pending
                            && !session.warningRequired()));
        advancedControls.child(
            IKey.dynamic(
                () -> session.view == null ? tr("loading")
                    : session.pending ? tr("pending") : unsaved.getAsBoolean() ? tr("unsaved") : tr("saved"))
                .asWidget()
                .name("rocket.schedule.save_state")
                .pos(398, 35)
                .size(84, 18)
                .color(() -> unsaved.getAsBoolean() ? GalaxiaGuiStyle.WARN : MUTED)
                .tooltip(
                    t -> {
                        if (session.view != null) t.addLine(IKey.lang(PREFIX + "revision", session.view.revision()));
                    }));

        var content = new ScheduleContent(session);
        panel.child(content);
        panel.child(
            IKey.dynamic(() -> status(session))
                .asWidget()
                .name("rocket.schedule.status")
                .pos(INSET, HEIGHT - 42)
                .size(WIDTH - 2 * INSET, 16)
                .color(
                    () -> !session.safeDurability() ? GalaxiaGuiStyle.BAD
                        : session.problemKey != null || session.view != null && session.view.problemKey() != null
                            || session.draft != null && !session.draft.hasValidRefueling() ? GalaxiaGuiStyle.WARN
                                : MUTED));
        advancedControls.child(button("cancel", () -> {
            session.cancel();
            panel.closeIfOpen();
        }).quiet()
            .pos(WIDTH - 240, HEIGHT - 25)
            .size(70, 18));
        var save = button("save", session::requestSave).primaryWhen(unsaved)
            .pos(WIDTH - 164, HEIGHT - 25)
            .size(70, 18)
            .tooltip(t -> t.addLine(IKey.lang(PREFIX + "save_hint")));
        save.setEnabledIf(
            widget -> unsaved.getAsBoolean() && !session.manual && !session.pending && !session.warningRequired());
        save.onMousePressed(mouse -> {
            if (mouse != 0) return false;
            // MUI removes focus after dispatching the press. Save needs the committed field first.
            save.getContext()
                .removeFocus();
            if (!save.canInteract.getAsBoolean()) return false;
            session.requestSave();
            return true;
        });
        advancedControls.child(save);
        advancedControls.child(
            button("start", session::requestStart)
                .primaryWhen(
                    () -> !unsaved.getAsBoolean() && session.draft != null
                        && !session.draft.visits()
                            .isEmpty())
                .tooltip(
                    t -> t.addLine(IKey.dynamic(() -> tr(unsaved.getAsBoolean() ? "start_unsaved" : "start_hint"))))
                .pos(WIDTH - 88, HEIGHT - 25)
                .size(78, 18)
                .setEnabledIf(
                    widget -> session.view != null && session.view.delivery() == null
                        && session.view.manualMission() == null
                        && !session.manual
                        && !unsaved.getAsBoolean()
                        && !session.pending
                        && !session.warningRequired()
                        && session.start != null
                        && !session.draft.visits()
                            .isEmpty()));
        advancedControls.child(buttonText(() -> tr(session.overview ? "schedule_view" : "overview"), () -> {
            session.overview = !session.overview;
            session.changed();
        }).quiet()
            .name("rocket.schedule.overview")
            .pos(WIDTH - 110, 35)
            .size(100, 18)
            .setEnabledIf(
                widget -> session.view != null && session.view.delivery() == null
                    && !session.manual
                    && !session.warningRequired()));
        advancedControls.child(buttonText(() -> tr(session.manual ? "schedule_view" : "manual"), () -> {
            if (session.manual) {
                session.manual = false;
                session.picker = Picker.NONE;
                session.changed();
            } else session.openManual();
        }).quiet()
            .name("rocket.schedule.manual")
            .pos(INSET, HEIGHT - 25)
            .size(110, 18)
            .setEnabledIf(
                w -> session.view != null && session.view.delivery() == null
                    && !session.pending
                    && !session.warningRequired()));
        var sections = new ParentWidget<>().pos(INSET, HEADER - SECTION_LABEL_HEIGHT)
            .size(WIDTH - 2 * INSET, SECTION_LABEL_HEIGHT)
            .setEnabledIf(
                widget -> session.view != null && session.draft != null
                    && !session.manual
                    && !session.overview
                    && !session.warningRequired());
        sections.child(
            IKey.dynamic(
                () -> tr(
                    "route_count",
                    session.draft == null ? 0
                        : session.draft.visits()
                            .size()))
                .asWidget()
                .name("rocket.schedule.route.heading")
                .size(LIST_WIDTH, 14)
                .color(MUTED));
        sections.child(
            IKey.dynamic(
                () -> tr(
                    session.picker == Picker.ACTION ? "choose_action"
                        : session.picker == Picker.RESOURCE ? "choose_resource"
                            : session.picker == Picker.ENDPOINT ? "choose_endpoint"
                                : session.selectedAction == null ? "destination_settings" : "action_settings"))
                .asWidget()
                .name("rocket.schedule.details.heading")
                .pos(DETAIL_X - INSET, 0)
                .size(DETAIL_WIDTH, 14)
                .color(MUTED));
        advancedControls.child(sections);
        simpleFooter(panel, session);
        panel.onCloseAction(session::cancel);
        return panel;
    }

    /** Retains the viewport and its scroll data while replacing only local draft controls. */
    private static final class ScrollContent extends ScrollWidget<ScrollContent> {

        private final VerticalScrollData scrollData;
        private final Flow rows = new Flow(com.cleanroommc.modularui.api.GuiAxis.Y) {

            @Override
            public boolean postLayoutWidgets() {
                boolean laidOut = super.postLayoutWidgets();
                if (laidOut) updateScroll();
                return laidOut;
            }
        };
        private final int viewportHeight;
        private final IntSupplier initialScroll;
        private boolean restoringScroll = true;
        @Nullable
        private String revealName;

        private ScrollContent(String name, int width, int height, IntSupplier initialScroll, IntConsumer saveScroll) {
            this(new VerticalScrollData(), name, width, height, initialScroll, saveScroll);
        }

        private ScrollContent(VerticalScrollData data, String name, int width, int height, IntSupplier initialScroll,
            IntConsumer saveScroll) {
            super(data);
            scrollData = data;
            viewportHeight = height;
            this.initialScroll = initialScroll;
            onUpdateListener(widget -> { if (!restoringScroll) saveScroll.accept(scrollData.getScroll()); });
            name(name);
            size(width, height);
            rows.pos(0, 0)
                .widthRel(1f);
            rows.resizer()
                .coverChildrenHeight(0);
            child(rows);
        }

        private void updateScroll() {
            scrollData.setScrollSize(Math.max(viewportHeight, rows.getArea().height));
            if (restoringScroll && !rows.getChildren()
                .isEmpty()) {
                scrollData.scrollTo(getScrollArea(), initialScroll.getAsInt());
                restoringScroll = false;
            }
            scrollData.clamp(getScrollArea());
            if (revealName == null) return;
            for (var row : rows.getChildren()) {
                if (!revealName.equals(row.getName())) continue;
                int top = row.getArea().ry;
                int current = scrollData.getScroll();
                if (top < current || top + row.getArea().height > current + viewportHeight)
                    scrollData.scrollTo(getScrollArea(), top);
                revealName = null;
                break;
            }
        }

        private void clearRows() {
            rows.removeAll();
        }

        private void refreshRows() {
            rows.scheduleResize();
            scheduleResize();
        }
    }

    private static final class ScheduleContent extends ParentWidget<ScheduleContent> {

        private final Session session;
        private final ParentWidget<?> main = new ParentWidget<>().widthRel(1f)
            .heightRel(1f);
        private final ParentWidget<?> overlay = new ParentWidget<>().widthRel(1f)
            .heightRel(1f);
        private final ScrollContent visits;
        private final ParentWidget<?> heading = new ParentWidget<>().pos(DETAIL_X - INSET, 0)
            .size(DETAIL_WIDTH, INSPECTOR_HEADER_HEIGHT);
        private final ScrollContent inspector;
        private final ParentWidget<?> pickerHeader = new ParentWidget<>().size(DETAIL_WIDTH, 25);
        private final ScrollContent choices;
        private int renderedVersion = -1;
        @Nullable
        private UUID selectedVisit;
        private Picker shownPicker = Picker.NONE;

        private ScheduleContent(Session session) {
            this.session = session;
            selectedVisit = session.selectedVisit;
            visits = new ScrollContent(
                "rocket.schedule.visits",
                LIST_WIDTH,
                CONTENT_HEIGHT,
                () -> session.listScroll,
                value -> session.listScroll = value);
            inspector = new ScrollContent(
                "rocket.schedule.inspector",
                DETAIL_WIDTH,
                CONTENT_HEIGHT - INSPECTOR_HEADER_HEIGHT,
                () -> session.inspectorScroll,
                value -> session.inspectorScroll = value);
            choices = new ScrollContent(
                "rocket.schedule.choices",
                DETAIL_WIDTH,
                CONTENT_HEIGHT - INSPECTOR_HEADER_HEIGHT - 25,
                () -> session.choicesScroll,
                value -> session.choicesScroll = value);
            name("rocket.schedule.content");
            pos(INSET, HEADER);
            widthRelOffset(1f, -2 * INSET).height(CONTENT_HEIGHT);
            visits.background(EnumTextures.GUI_WELL.getImage());
            visits.rows.padding(LIST_PADDING);
            inspector.pos(DETAIL_X - INSET, INSPECTOR_HEADER_HEIGHT);
            pickerHeader.pos(DETAIL_X - INSET, INSPECTOR_HEADER_HEIGHT);
            choices.pos(DETAIL_X - INSET, INSPECTOR_HEADER_HEIGHT + 25);
            main.child(visits)
                .child(heading)
                .child(inspector)
                .child(pickerHeader)
                .child(choices);
            child(main).child(overlay);
            main.setEnabledIf(w -> regular());
            overlay.setEnabledIf(w -> !regular());
            inspector.setEnabledIf(w -> session.picker == Picker.NONE);
            pickerHeader.setEnabledIf(w -> session.picker != Picker.NONE);
            choices.setEnabledIf(w -> session.picker != Picker.NONE);
            onUpdateListener(widget -> refresh(), true);
        }

        private boolean regular() {
            return session.view != null && session.draft != null
                && !session.warningRequired()
                && !session.manual
                && !session.overview
                && !session.simple;
        }

        private void refresh() {
            if (renderedVersion == session.renderVersion) return;
            renderedVersion = session.renderVersion;
            if (!regular()) {
                overlay.removeAll();
                if (session.view == null || session.draft == null) overlay.child(
                    IKey.lang(PREFIX + "loading")
                        .asWidget()
                        .pos(10, 40)
                        .color(MUTED));
                else if (session.warningRequired()) launchWarning(overlay, session);
                else if (session.manual) manualForm(overlay, session);
                else if (session.simple) simpleForm(overlay, session);
                else vehicleOverview(overlay, session);
                overlay.scheduleResize();
                return;
            }
            visits.clearRows();
            if (session.draft.visits()
                .isEmpty())
                visits.rows.child(
                    IKey.lang(PREFIX + "empty")
                        .asWidget()
                        .widthRelOffset(1f, -12)
                        .height(48)
                        .color(MUTED)
                        .margin(6));
            for (int i = 0; i < session.draft.visits()
                .size(); i++)
                visits.rows.child(
                    visitRow(
                        session,
                        session.draft.visits()
                            .get(i),
                        i + 1));
            if (!java.util.Objects.equals(selectedVisit, session.selectedVisit)) {
                selectedVisit = session.selectedVisit;
                visits.revealName = "rocket.schedule.visit." + selectedVisit;
                inspector.scrollData.scrollTo(inspector.getScrollArea(), 0);
                choices.scrollData.scrollTo(choices.getScrollArea(), 0);
            }
            visits.refreshRows();
            heading.removeAll();
            inspector.clearRows();
            pickerHeader.removeAll();
            choices.clearRows();
            var inspected = session.visit();
            if (inspected == null) {
                inspector.rows.child(
                    IKey.lang(PREFIX + "choose_visit")
                        .asWidget()
                        .size(DETAIL_WIDTH, 60)
                        .color(MUTED));
            } else {
                heading.child(
                    IKey.dynamic(() -> destinationName(inspected.place(), session.catalog))
                        .asWidget()
                        .name("rocket.schedule.inspector.target")
                        .size(DETAIL_WIDTH, 18)
                        .color(TEXT));
                heading.child(
                    IKey.dynamic(() -> placeContext(inspected.place(), session.catalog))
                        .asWidget()
                        .pos(0, 19)
                        .size(DETAIL_WIDTH, 16)
                        .color(MUTED));
                if (!session.editable(inspected.id())) session.picker = Picker.NONE;
                if (session.picker != Picker.NONE) {
                    if (shownPicker != session.picker) choices.scrollData.scrollTo(choices.getScrollArea(), 0);
                    picker(pickerHeader, choices.rows, session);
                } else {
                    var editor = new ParentWidget<>().widthRel(1f);
                    editor.resizer()
                        .coverChildrenHeight(0);
                    if (session.selectedAction != null) {
                        actionDetails(editor, session);
                    } else {
                        visitDetails(editor, session);
                    }
                    inspector.rows.child(editor);
                }
            }
            shownPicker = session.picker;
            heading.scheduleResize();
            pickerHeader.scheduleResize();
            inspector.refreshRows();
            choices.refreshRows();
        }
    }

    private static void simpleFooter(ModularPanel panel, Session session) {
        var footer = new ParentWidget<>().pos(0, HEIGHT - 25)
            .widthRel(1f)
            .height(18)
            .setEnabledIf(widget -> session.simpleMode() && !session.manual && !session.warningRequired());
        footer.child(buttonText(() -> tr("cancel"), () -> {
            session.cancel();
            panel.closeIfOpen();
        }).quiet()
            .pos(INSET, 0)
            .size(90, 18));
        footer.child(
            buttonText(() -> simpleText("detach"), session::requestDeliveryClear).name("rocket.simple.detach")
                .pos(110, 0)
                .size(160, 18)
                .setEnabledIf(
                    widget -> session.simpleEditable() && session.view.delivery() != null
                        && !session.view.delivery()
                            .enabled()
                        && session.clearDelivery != null));
        footer.child(
            buttonText(() -> simpleText("stop"), session::requestDeliveryStop).name("rocket.simple.stop")
                .pos(280, 0)
                .size(130, 18)
                .tooltip(t -> t.addLine(IKey.lang(SIMPLE_PREFIX + "stop_hint")))
                .setEnabledIf(
                    widget -> session.view != null && session.view.delivery() != null
                        && session.view.delivery()
                            .enabled()
                        && !session.pending
                        && session.stopDelivery != null));
        var start = buttonText(
            () -> simpleText(session.newDeliveryOrder() ? "start_new" : "start"),
            session::requestDeliverySave).name("rocket.simple.start")
                .right(INSET)
                .top(0)
                .size(170, 18)
                .tooltip(
                    t -> t.addLine(
                        IKey.dynamic(() -> simpleText(session.newDeliveryOrder() ? "new_order_hint" : "start_hint"))))
                .primaryWhen(session::hasSimplePlan)
                .setEnabledIf(
                    widget -> session.simpleEditable() && session.hasSimplePlan()
                        && !session.dirty()
                        && session.saveDelivery != null
                        && (session.simpleDirty || session.view.delivery() == null
                            || !session.view.delivery()
                                .enabled()));
        start.onMousePressed(mouse -> {
            if (mouse != 0) return false;
            start.getContext()
                .removeFocus();
            if (!start.canInteract.getAsBoolean()) return false;
            session.requestDeliverySave();
            return true;
        });
        footer.child(start);
        panel.child(footer);
    }

    private static void simpleForm(ParentWidget<?> content, Session session) {
        content.child(
            IKey.lang(SIMPLE_PREFIX + "title")
                .asWidget()
                .pos(0, 0)
                .color(ACCENT));
        content.child(
            IKey.lang(SIMPLE_PREFIX + "home")
                .asWidget()
                .pos(0, 24)
                .color(MUTED));
        content.child(
            GalaxiaUi
                .clipped(
                    session.simpleHome == null ? simpleText("home_required")
                        : targetName(session.simpleHome, session.catalog),
                    LIST_WIDTH,
                    TEXT)
                .pos(0, 40)
                .height(18));
        content.child(
            IKey.lang(SIMPLE_PREFIX + "destination")
                .asWidget()
                .pos(0, 68)
                .color(MUTED));
        content.child(
            bodyMapButton(session, () -> bodyName(session.simpleBody, session.catalog)).name("rocket.simple.body")
                .pos(0, 86)
                .size(LIST_WIDTH, 22)
                .setEnabledIf(widget -> session.simpleEditable() && session.bodyMap != null));
        var destinations = new ScrollContent(
            "rocket.simple.destinations",
            LIST_WIDTH,
            156,
            () -> session.simpleDestinationsScroll,
            value -> session.simpleDestinationsScroll = value);
        destinations.pos(0, 116)
            .background(EnumTextures.GUI_WELL.getImage());
        destinations.rows.padding(4);
        var available = session.simpleDestinations(session.simpleBody);
        if (available.isEmpty()) destinations.rows.child(
            IKey.lang(SIMPLE_PREFIX + "no_silos")
                .asWidget()
                .widthRel(1f)
                .height(40)
                .color(MUTED)
                .margin(6));
        for (var endpoint : available) {
            boolean selected = session.simpleDestination != null
                && session.simpleDestination.target() instanceof VehicleSchedule.Exact exact
                && exact.endpointId()
                    .equals(endpoint.endpointId());
            destinations.rows.child(
                buttonText(endpoint::routingName, () -> session.simpleDestination(endpoint))
                    .name("rocket.simple.destination." + endpoint.endpointId())
                    .widthRel(1f)
                    .height(26)
                    .primaryWhen(() -> selected)
                    .setEnabledIf(widget -> session.simpleEditable()));
        }
        content.child(destinations);
        var requests = new ParentWidget<>().pos(DETAIL_X - INSET, 0)
            .size(DETAIL_WIDTH, CONTENT_HEIGHT);
        content.child(requests);
        if (session.simpleChoosingItem) {
            simpleItemChoices(requests, session);
            return;
        }
        requests.child(
            IKey.lang(SIMPLE_PREFIX + "requests")
                .asWidget()
                .pos(0, 0)
                .color(ACCENT));
        requests.child(buttonText(() -> simpleText("add_request"), () -> {
            session.simpleChoosingItem = true;
            session.search = "";
            session.simpleScroll = 0;
            session.changed();
        }).name("rocket.simple.add_request")
            .right(0)
            .top(0)
            .size(110, 22)
            .setEnabledIf(widget -> session.simpleEditable()));
        var list = new ScrollContent(
            "rocket.simple.requests",
            DETAIL_WIDTH,
            CONTENT_HEIGHT - 30,
            () -> session.simpleScroll,
            value -> session.simpleScroll = value);
        list.pos(0, 30)
            .background(EnumTextures.GUI_WELL.getImage());
        list.rows.padding(4);
        if (session.simpleRequests.isEmpty()) list.rows.child(
            IKey.lang(SIMPLE_PREFIX + "empty_requests")
                .asWidget()
                .widthRel(1f)
                .height(50)
                .color(MUTED)
                .margin(6));
        var entries = session.simpleRequests.entrySet()
            .stream()
            .sorted(java.util.Comparator.comparing(entry -> resourceName(entry.getKey())))
            .toList();
        for (int index = 0; index < entries.size(); index++) list.rows.child(
            simpleRequestRow(
                session,
                (ItemStackWrapper) entries.get(index)
                    .getKey(),
                index));
        requests.child(list);
    }

    private static ParentWidget<?> simpleRequestRow(Session session, ItemStackWrapper resource, int index) {
        long requested = session.simpleRequests.get(resource);
        var row = new ParentWidget<>().widthRel(1f)
            .height(58)
            .background(EnumTextures.GUI_PANEL.getImage());
        row.child(
            GalaxiaUi.clipped(resourceName(resource), DETAIL_WIDTH - 48, TEXT)
                .pos(4, 4)
                .height(16));
        var remove = buttonText(() -> "x", () -> session.simpleRequest(resource, 0)).quiet()
            .danger()
            .name("rocket.simple.remove_request." + index)
            .right(4)
            .top(3)
            .size(20, 18)
            .setEnabledIf(widget -> session.simpleEditable());
        remove.onMousePressed(mouse -> {
            if (mouse != 0) return false;
            remove.getContext()
                .removeFocus();
            if (!remove.canInteract.getAsBoolean()) return false;
            session.simpleRequest(resource, 0);
            return true;
        });
        row.child(remove);
        row.child(
            IKey.lang(SIMPLE_PREFIX + "requested")
                .asWidget()
                .pos(4, 30)
                .color(MUTED));
        row.child(
            field(
                session,
                () -> Long.toString(requested),
                value -> session.simpleRequest(resource, Long.parseLong(value))).name("rocket.simple.amount." + index)
                    .pos(82, 26)
                    .size(82, 22)
                    .setMaxLength(19)
                    .setEnabledIf(widget -> session.simpleEditable()));
        long delivered = session.view.delivery() == null ? 0
            : session.view.delivery()
                .delivered()
                .getOrDefault(resource, 0L);
        row.child(
            GalaxiaUi.clipped(simpleText("delivered", delivered), DETAIL_WIDTH - 178, MUTED)
                .pos(172, 30)
                .height(16));
        return row;
    }

    private static void simpleItemChoices(ParentWidget<?> content, Session session) {
        content.child(
            IKey.lang(SIMPLE_PREFIX + "choose_item")
                .asWidget()
                .pos(0, 0)
                .color(ACCENT));
        content.child(buttonText(() -> tr("back"), () -> {
            session.simpleChoosingItem = false;
            session.simpleScroll = 0;
            session.changed();
        }).quiet()
            .right(0)
            .top(0)
            .size(60, 20));
        content.child(field(session, () -> session.search, value -> {
            session.search = value;
            session.changed();
        }).name("rocket.simple.search")
            .pos(0, 28)
            .widthRel(1f)
            .height(22)
            .hintText(tr("search")));
        var list = new ScrollContent(
            "rocket.simple.items",
            DETAIL_WIDTH,
            CONTENT_HEIGHT - 58,
            () -> session.simpleScroll,
            value -> session.simpleScroll = value);
        list.pos(0, 58)
            .background(EnumTextures.GUI_WELL.getImage());
        list.rows.padding(4);
        boolean found = false;
        for (var resource : session.catalog.resources()) {
            if (!(resource instanceof ItemStackWrapper item) || !Rocket.acceptsItemCargo(item.toStack(1))
                || session.simpleRequests.containsKey(item)
                || !matches(resourceName(item), session.search)) continue;
            found = true;
            list.rows.child(buttonText(() -> resourceName(item), () -> {
                session.simpleRequest(item, 1);
                session.simpleChoosingItem = false;
                session.simpleScroll = 0;
                session.changed();
            }).widthRel(1f)
                .height(26)
                .setEnabledIf(widget -> session.simpleEditable()));
        }
        if (!found) list.rows.child(
            IKey.lang(SIMPLE_PREFIX + "no_items")
                .asWidget()
                .widthRel(1f)
                .height(50)
                .color(MUTED)
                .margin(4));
        content.child(list);
    }

    private static void manualForm(ParentWidget<?> content, Session session) {
        boolean editable = session.view.status() == Rocket.Status.PHYSICAL && !session.pending;
        content.child(
            IKey.lang(PREFIX + "manual_title")
                .asWidget()
                .pos(0, 2)
                .color(ACCENT));
        content.child(
            bodyMapButton(session, () -> bodyName(session.manualBody, session.catalog))
                .name("rocket.schedule.manual.body")
                .pos(0, 25)
                .size(LIST_WIDTH, 20)
                .setEnabledIf(w -> editable && session.bodyMap != null));
        content.child(
            IKey.lang(PREFIX + "manual_origin")
                .asWidget()
                .pos(0, 53)
                .color(TEXT));
        content.child(
            field(
                session,
                () -> session.manualX,
                value -> session.manualDestination(session.manualBody, value, session.manualZ))
                    .name("rocket.schedule.manual.x")
                    .pos(0, 67)
                    .size(127, 20)
                    .hintText(tr("manual_x"))
                    .setEnabledIf(w -> editable));
        content.child(
            field(
                session,
                () -> session.manualZ,
                value -> session.manualDestination(session.manualBody, session.manualX, value))
                    .name("rocket.schedule.manual.z")
                    .pos(141, 67)
                    .size(127, 20)
                    .hintText(tr("manual_z"))
                    .setEnabledIf(w -> editable));
        content.child(
            IKey.lang(PREFIX + "manual_origin_hint")
                .asWidget()
                .pos(0, 92)
                .size(LIST_WIDTH, 28)
                .color(MUTED));
        content.child(
            button("manual_search", session::requestLandingSearch).name("rocket.schedule.manual.search")
                .pos(0, 125)
                .size(LIST_WIDTH, 23)
                .setEnabledIf(w -> editable && !session.searchingLanding && session.searchLanding != null));
        content.child(
            IKey.dynamic(
                () -> session.searchingLanding ? tr("manual_searching")
                    : session.landingProblem != null ? StatCollector.translateToLocal(session.landingProblem)
                        : session.view.manualMission() != null && !session.manualDirty ? tr("manual_accepted")
                            : session.landingSite != null ? tr("manual_site_ready") : tr("manual_search_required"))
                .asWidget()
                .name("rocket.schedule.manual.status")
                .pos(0, 156)
                .size(LIST_WIDTH, 52)
                .color(MUTED));
        int right = DETAIL_X - INSET;
        content.child(
            IKey.lang(PREFIX + "allowance")
                .asWidget()
                .pos(right, 2)
                .color(TEXT));
        content.child(
            field(
                session,
                () -> session.manualRoute.allowance()
                    .isPresent()
                        ? deltaVInput(
                            session.manualRoute.allowance()
                                .getAsDouble())
                        : "",
                value -> session.manualRoute(
                    new VehicleSchedule.RouteSettings(parseDeltaVInput(value), session.manualRoute.priority())))
                        .name("rocket.schedule.manual.allowance")
                        .pos(right, 20)
                        .size(160, 20)
                        .setEnabledIf(w -> editable));
        content.child(
            IKey.lang(PREFIX + "kilometres_per_second")
                .asWidget()
                .pos(right + 170, 26)
                .color(MUTED));
        content.child(
            buttonText(
                () -> tr(session.manualRoute.priority() == RoutePriority.PRIORITIZE_DV ? "economy" : "speed"),
                () -> session.manualRoute(
                    new VehicleSchedule.RouteSettings(
                        session.manualRoute.allowance(),
                        session.manualRoute.priority()
                            .toggled()))).name("rocket.schedule.manual.priority")
                                .pos(right, 48)
                                .size(DETAIL_WIDTH, 22)
                                .setEnabledIf(w -> editable));
        var site = session.landingSite == null ? null : session.landingSite.surfaceSite();
        if (site != null) {
            content.child(
                IKey.lang(PREFIX + "manual_site", site.x(), site.y(), site.z())
                    .asWidget()
                    .name("rocket.schedule.manual.site")
                    .pos(right, 81)
                    .size(DETAIL_WIDTH, 20)
                    .color(ACCENT));
            content.child(
                IKey.lang(
                    PREFIX + "manual_footprint",
                    site.footprint()
                        .width(),
                    site.footprint()
                        .depth(),
                    site.footprint()
                        .height())
                    .asWidget()
                    .name("rocket.schedule.manual.footprint")
                    .pos(right, 105)
                    .size(DETAIL_WIDTH, 26)
                    .color(TEXT));
            content.child(
                IKey.lang(
                    PREFIX + (session.view.manualMission() != null && !session.manualDirty ? "manual_reserved_hint"
                        : "manual_candidate_hint"))
                    .asWidget()
                    .pos(right, 134)
                    .size(DETAIL_WIDTH, 28)
                    .color(MUTED));
        }
        content.child(
            button("manual_accept", session::requestLandingAcceptance).name("rocket.schedule.manual.accept")
                .pos(right, 166)
                .size(144, 23)
                .setEnabledIf(w -> session.canAcceptLanding()));
        content.child(
            button(
                session.view.manualMission() == null ? "manual_back" : "manual_cancel",
                session::requestLandingCancellation).name("rocket.schedule.manual.cancel")
                    .pos(right + 150, 166)
                    .size(150, 23)
                    .setEnabledIf(w -> !session.pending && (session.view.manualMission() == null || editable)));
        if (session.view.manualMission() != null) {
            content.child(
                button("manual_resume", session::requestStart).name("rocket.schedule.manual.resume")
                    .pos(right, 195)
                    .size(DETAIL_WIDTH, 23)
                    .setEnabledIf(
                        w -> editable && !session.manualDirty
                            && session.start != null
                            && (PREFIX + "wait.review_paused").equals(session.view.problemKey())));
        }
    }

    private static void launchWarning(ParentWidget<?> content, Session session) {
        var warning = session.view.launchWarning();
        var dialog = new ParentWidget<>().name("rocket.schedule.risk")
            .pos((WIDTH - 2 * INSET - WARNING_WIDTH) / 2, (CONTENT_HEIGHT - WARNING_HEIGHT) / 2)
            .size(WARNING_WIDTH, WARNING_HEIGHT)
            .background(EnumTextures.GUI_PANEL.getImage());
        dialog.child(
            IKey.lang(PREFIX + "risk_title")
                .asWidget()
                .pos(INSET, INSET)
                .size(WARNING_WIDTH - 2 * INSET, 18)
                .color(GalaxiaGuiStyle.WARN));
        dialog.child(
            IKey.lang(PREFIX + (warning.returnDataAvailable() ? "risk_without_replenishment" : "risk_return_unknown"))
                .asWidget()
                .pos(INSET, 36)
                .size(WARNING_WIDTH - 2 * INSET, 42)
                .color(TEXT));
        dialog.child(
            IKey.lang(
                PREFIX + "risk_route",
                bodyName(
                    warning.source()
                        .body(),
                    session.catalog),
                tr(
                    "context." + warning.source()
                        .context()
                        .name()
                        .toLowerCase(java.util.Locale.ROOT)),
                bodyName(
                    warning.destination()
                        .body(),
                    session.catalog),
                tr(
                    "context." + warning.destination()
                        .context()
                        .name()
                        .toLowerCase(java.util.Locale.ROOT)))
                .asWidget()
                .pos(INSET, 84)
                .size(WARNING_WIDTH - 2 * INSET, 24)
                .color(TEXT));
        dialog.child(
            IKey.lang(PREFIX + "risk_outbound", deltaVText(warning.outboundDeltaVMps()))
                .asWidget()
                .pos(INSET, 113)
                .size(WARNING_WIDTH - 2 * INSET, 14)
                .color(ACCENT));
        dialog.child(
            IKey.lang(PREFIX + "risk_remaining", deltaVText(warning.remainingDeltaVMps()))
                .asWidget()
                .pos(INSET, 129)
                .size(WARNING_WIDTH - 2 * INSET, 14)
                .color(ACCENT));
        dialog.child(
            button("risk_edit", () -> session.requestRiskReview(false)).name("rocket.schedule.risk.edit")
                .pos(INSET, WARNING_HEIGHT - 34)
                .size(160, 24)
                .setEnabledIf(widget -> !session.pending && session.review != null));
        dialog.child(
            button("risk_acknowledge", () -> session.requestRiskReview(true)).name("rocket.schedule.risk.confirm")
                .pos(WARNING_WIDTH - 224, WARNING_HEIGHT - 34)
                .size(214, 24)
                .setEnabledIf(widget -> !session.pending && session.review != null));
        content.child(dialog);
    }

    private static ParentWidget<?> visitRow(Session session, VehicleSchedule.Visit visit, int ordinal) {
        boolean expanded = visit.id()
            .equals(session.selectedVisit);
        boolean editable = session.editable(visit.id());
        int height = CARD_HEADER_HEIGHT + (expanded ? ACTION_ROW_HEIGHT + visit.actions()
            .size() * ACTION_ROW_HEIGHT + (editable ? CARD_ACTIONS_HEIGHT : 38) : 0);
        var row = new ParentWidget<>().name("rocket.schedule.visit." + visit.id())
            .widthRel(1f)
            .height(height)
            .marginBottom(CARD_GAP)
            .background(EnumTextures.GUI_PANEL.getImage());
        var headerBackground = expanded ? EnumTextures.GUI_BUTTON_SELECTED.getImage()
            : EnumTextures.GUI_BUTTON.getImage();
        Supplier<String> destinationLabel = () -> ordinal + ". " + destinationName(visit.place(), session.catalog);
        row.child(
            buttonText(destinationLabel, () -> session.selectVisit(visit.id())).background(headerBackground)
                .overlay(
                    IKey.dynamic(destinationLabel)
                        .color(TEXT)
                        .alignment(Alignment.CenterLeft)
                        .asIcon()
                        .expandWidth()
                        .marginLeft(CARD_PADDING - CARD_HEADER_INSET)
                        .marginRight(CARD_PADDING))
                .name("rocket.schedule.select." + visit.id())
                .pos(CARD_HEADER_INSET, 2)
                .widthRelOffset(1f, -77)
                .height(22));
        row.child(
            button("settings", () -> session.selectVisit(visit.id())).name("rocket.schedule.settings." + visit.id())
                .right(3)
                .top(2)
                .size(69, 22));
        row.child(
            IKey.dynamic(() -> placeContext(visit.place(), session.catalog))
                .asWidget()
                .pos(CARD_PADDING, 29)
                .widthRelOffset(1f, -65)
                .height(18)
                .color(MUTED));
        row.child(
            symbol("^", () -> session.moveVisit(visit.id(), -1)).name("rocket.schedule.visit.up." + visit.id())
                .right(39)
                .top(27)
                .size(18, 18)
                .setEnabledIf(w -> editable));
        row.child(
            symbol("v", () -> session.moveVisit(visit.id(), 1)).name("rocket.schedule.visit.down." + visit.id())
                .right(20)
                .top(27)
                .size(18, 18)
                .setEnabledIf(w -> editable));
        row.child(
            symbol("x", () -> session.removeVisit(visit.id())).name("rocket.schedule.visit.remove." + visit.id())
                .right(1)
                .top(27)
                .size(18, 18)
                .setEnabledIf(w -> editable));
        if (!expanded) return row;
        row.child(
            IKey.lang(
                PREFIX + (visit.place()
                    .context() == VehicleSchedule.Context.SURFACE ? "after_landing" : "after_arrival"))
                .asWidget()
                .pos(ACTION_INDENT, CARD_HEADER_HEIGHT + 3)
                .widthRelOffset(1f, -ACTION_INDENT - CARD_PADDING)
                .height(18)
                .color(MUTED));
        int y = CARD_HEADER_HEIGHT + ACTION_ROW_HEIGHT;
        for (var action : visit.actions()) {
            row.child(arrivalActionRow(session, visit, action, editable).pos(ACTION_INDENT, y));
            y += ACTION_ROW_HEIGHT;
        }
        if (editable) {
            row.child(
                button("add_action", () -> openPicker(session, Picker.ACTION)).pos(ACTION_INDENT, y + CARD_BUTTON_GAP)
                    .widthRelOffset(1f, -ACTION_INDENT - CARD_PADDING)
                    .height(ACTION_BUTTON_HEIGHT));
        } else {
            row.child(
                IKey.lang(PREFIX + "active_locked")
                    .asWidget()
                    .pos(ACTION_INDENT, y + CARD_BUTTON_GAP)
                    .widthRelOffset(1f, -ACTION_INDENT - CARD_PADDING)
                    .height(34)
                    .color(MUTED));
        }
        return row;
    }

    private static ParentWidget<?> arrivalActionRow(Session session, VehicleSchedule.Visit visit,
        VehicleSchedule.Action action, boolean editable) {
        var row = new ParentWidget<>().name("rocket.schedule.action." + action.id())
            .widthRelOffset(1f, -ACTION_INDENT - CARD_PADDING).height(ACTION_ROW_HEIGHT)
            .background(EnumTextures.GUI_WELL.getImage());
        String type = switch (action) {
            case VehicleSchedule.Transfer transfer -> transfer.direction() == VehicleSchedule.Direction.LOAD ? "load" : "unload";
            case VehicleSchedule.Refuel ignored -> "refuel";
            case VehicleSchedule.Deploy ignored -> "deploy";
            case VehicleSchedule.Repair ignored -> "repair";
        };
        String parameter = switch (action) {
            case VehicleSchedule.Refuel refuel -> refuel.toFull() ? tr("refuel_full")
                : refuel.targets().isEmpty() ? tr("refuel_choose_targets")
                : refuel.targets().entrySet().stream()
                    .map(target -> tr("summary_amount", resourceName(target.getKey()), target.getValue()))
                    .collect(java.util.stream.Collectors.joining(", "));
            case VehicleSchedule.Repair repair -> tr("repair_below", Math.round(repair.thresholdPercent()));
            default -> tr("summary_amount", resourceName(actionResource(action)), actionAmount(action));
        };
        String summary = tr("action_summary", tr(type), parameter);
        row.child(IKey.str(summary).asWidget()
            .name("rocket.schedule.action.summary." + action.id()).pos(3, 3)
            .widthRelOffset(1f, -ACTION_EDIT_WIDTH - ACTION_REMOVE_WIDTH - 2 * CARD_BUTTON_GAP - 3)
            .height(ACTION_BUTTON_HEIGHT).color(TEXT)
            .tooltip(t -> t.addLine(IKey.str(summary))));
        row.child(button("edit", () -> openAction(session, visit.id(), action.id()))
            .name("rocket.schedule.action.edit." + action.id())
            .right(ACTION_REMOVE_WIDTH + CARD_BUTTON_GAP).top(3).size(ACTION_EDIT_WIDTH, ACTION_BUTTON_HEIGHT)
            .setEnabledIf(w -> editable));
        row.child(button("remove", () -> session.removeAction(action.id())).quiet().danger()
            .name("rocket.schedule.action.remove." + action.id())
            .right(0).top(3)
            .size(ACTION_REMOVE_WIDTH, ACTION_BUTTON_HEIGHT).setEnabledIf(w -> editable));
        return row;
    }

    private static void openAction(Session session, UUID visit, UUID action) {
        if (!session.editable(visit)) return;
        session.selectVisit(visit);
        session.selectedAction = action;
        session.changed();
    }

    @Nullable
    private static VehicleSchedule.Leg incomingLeg(Session session) {
        var visits = session.draft.visits();
        for (int i = 0; i < visits.size(); i++) {
            if (!visits.get(i)
                .id()
                .equals(session.selectedVisit)) continue;
            if (i > 0) return new VehicleSchedule.Leg(
                visits.get(i - 1)
                    .id(),
                visits.get(i)
                    .id());
            if (session.draft.mode() == VehicleSchedule.Mode.LOOP && visits.size() > 1) return new VehicleSchedule.Leg(
                visits.getLast()
                    .id(),
                visits.getFirst()
                    .id());
        }
        return null;
    }

    private static void visitDetails(ParentWidget<?> details, Session session) {
        var visit = session.visit();
        boolean editable = session.editable(visit.id());
        details.child(
            bodyMapButton(
                session,
                () -> bodyName(
                    visit.place()
                        .body(),
                    session.catalog)).name("rocket.schedule.body")
                        .pos(0, DESTINATION_FIELD_Y)
                        .widthRel(1f)
                        .height(20)
                        .setEnabledIf(w -> editable && session.bodyMap != null));
        details.child(
            buttonText(
                () -> tr(
                    "context." + visit.place()
                        .context()
                        .name()
                        .toLowerCase(java.util.Locale.ROOT)),
                () -> session.replacePlace(
                    new VehicleSchedule.Place(
                        visit.place()
                            .body(),
                        visit.place()
                            .context() == VehicleSchedule.Context.SURFACE ? VehicleSchedule.Context.ORBIT
                                : VehicleSchedule.Context.SURFACE,
                        null))).name("rocket.schedule.context")
                            .pos(0, ARRIVAL_FIELD_Y)
                            .size(ARRIVAL_CONTEXT_WIDTH, 20)
                            .setEnabledIf(w -> editable));
        details.child(
            buttonText(() -> targetName(visit.place(), session.catalog), () -> openPicker(session, Picker.ENDPOINT))
                .name("rocket.schedule.target")
                .pos(ARRIVAL_SITE_X, ARRIVAL_FIELD_Y)
                .widthRelOffset(1f, -ARRIVAL_SITE_X)
                .height(20)
                .setEnabledIf(w -> editable));
        details.child(
            buttonText(
                () -> tr(visit.blockDockActions() ? "dock_blocked" : "dock_allowed"),
                () -> session.replaceVisit(
                    new VehicleSchedule.Visit(visit.id(), visit.place(), visit.actions(), !visit.blockDockActions())))
                        .name("rocket.schedule.dock_policy")
                        .pos(0, DOCK_POLICY_Y)
                        .widthRel(1f)
                        .height(20)
                        .setEnabledIf(w -> editable));
        var incoming = incomingLeg(session);
        boolean expanded = incoming != null && incoming.equals(session.selectedLeg);
        if (incoming != null) {
            details.child(buttonText(() -> (expanded ? "v " : "> ") + tr("travel"), () -> {
                session.selectedLeg = expanded ? null : incoming;
                session.changed();
            }).name("rocket.schedule.travel." + incoming.fromVisit() + "." + incoming.toVisit())
                .pos(0, TRAVEL_HEADER_Y)
                .widthRel(1f)
                .height(22));
            if (expanded) {
                var travel = new ParentWidget<>().pos(0, TRAVEL_DETAILS_Y)
                    .widthRel(1f)
                    .height(TRAVEL_DETAILS_HEIGHT);
                routeDetails(travel, session);
                details.child(travel);
            }
        }
        details.child(
            IKey.lang(PREFIX + (editable ? "arrival_edit_hint" : "active_locked"))
                .asWidget()
                .pos(
                    0,
                    expanded ? TRAVEL_DETAILS_Y + TRAVEL_DETAILS_HEIGHT + 8
                        : incoming == null ? DOCK_POLICY_Y + 32 : TRAVEL_HEADER_Y + 32)
                .widthRel(1f)
                .height(50)
                .color(MUTED));
        details.child(
            IKey.lang(PREFIX + "destination_body")
                .asWidget()
                .pos(0, 0)
                .widthRel(1f)
                .height(12)
                .color(MUTED));
        details.child(
            IKey.lang(PREFIX + "arrival_mode")
                .asWidget()
                .pos(0, ARRIVAL_LABEL_Y)
                .size(ARRIVAL_CONTEXT_WIDTH, 12)
                .color(MUTED));
        details.child(
            IKey.lang(PREFIX + "arrival_site")
                .asWidget()
                .pos(ARRIVAL_SITE_X, ARRIVAL_LABEL_Y)
                .widthRelOffset(1f, -ARRIVAL_SITE_X)
                .height(12)
                .color(MUTED));
    }

    private static void routeDetails(ParentWidget<?> details, Session session) {
        var settings = session.draft.routes()
            .get(session.selectedLeg);
        if (settings == null) {
            session.selectedLeg = null;
            return;
        }
        var leg = session.selectedLeg;
        boolean editable = session.editable(session.selectedLeg.toVisit());
        details.child(
            IKey.lang(PREFIX + "travel")
                .asWidget()
                .pos(0, 4)
                .color(ACCENT));
        details.child(
            IKey.lang(PREFIX + "generated_travel")
                .asWidget()
                .pos(0, 21)
                .widthRel(1f)
                .height(25)
                .color(MUTED));
        details.child(
            IKey.lang(PREFIX + "allowance")
                .asWidget()
                .pos(0, 50)
                .color(TEXT));
        details.child(
            field(
                session,
                () -> settings.allowance()
                    .isPresent()
                        ? deltaVInput(
                            settings.allowance()
                                .getAsDouble())
                        : "",
                value -> {
                    var allowance = parseDeltaVInput(value);
                    session.edit(
                        session.draft
                            .withRouteSettings(leg, new VehicleSchedule.RouteSettings(allowance, settings.priority())));
                }).name("rocket.schedule.allowance")
                    .pos(0, 65)
                    .size(160, 19)
                    .setEnabledIf(w -> editable));
        details.child(
            IKey.lang(PREFIX + "kilometres_per_second")
                .asWidget()
                .pos(170, 71)
                .color(MUTED));
        details.child(
            buttonText(
                () -> tr(settings.priority() == RoutePriority.PRIORITIZE_DV ? "economy" : "speed"),
                () -> session.edit(
                    session.draft.withRouteSettings(
                        leg,
                        new VehicleSchedule.RouteSettings(
                            settings.allowance(),
                            settings.priority()
                                .toggled())))).name("rocket.schedule.priority")
                                    .pos(0, 92)
                                    .widthRel(1f)
                                    .height(22)
                                    .setEnabledIf(w -> editable));
        details.child(
            IKey.lang(PREFIX + "server_validation")
                .asWidget()
                .pos(0, 197)
                .widthRel(1f)
                .height(26)
                .color(MUTED));
        final Catalog[] evaluatedCatalog = { null };
        final LocalVehicle[] evaluatedVehicle = { null };
        final String[] guidance = { "" };
        details.child(forecastLabel(() -> {
            if (evaluatedCatalog[0] != session.catalog
                || !java.util.Objects.equals(evaluatedVehicle[0], session.view.propulsion())) {
                evaluatedCatalog[0] = session.catalog;
                evaluatedVehicle[0] = session.view.propulsion();
                guidance[0] = forecastText(session, leg);
            }
            return guidance[0];
        }).pos(0, 126)
            .widthRel(1f));
    }

    private static String forecastText(Session session, VehicleSchedule.Leg leg) {
        var catalog = session.catalog;
        var settings = session.draft.routes()
            .get(leg);
        if (settings == null || settings.allowance()
            .isEmpty()) return tr("allowance_missing");
        if (catalog.root() == null || catalog.costModel() == null) return tr("forecast_loading");
        var from = session.draft.visits()
            .stream()
            .filter(
                visit -> visit.id()
                    .equals(leg.fromVisit()))
            .findFirst()
            .orElseThrow()
            .place();
        var to = session.draft.visits()
            .stream()
            .filter(
                visit -> visit.id()
                    .equals(leg.toVisit()))
            .findFirst()
            .orElseThrow()
            .place();
        var source = catalog.bodies()
            .stream()
            .filter(
                body -> body.key()
                    .equals(from.body()))
            .findFirst()
            .orElse(null);
        var destination = catalog.bodies()
            .stream()
            .filter(
                body -> body.key()
                    .equals(to.body()))
            .findFirst()
            .orElse(null);
        if (source == null || destination == null) return tr("forecast_unknown_body");
        var estimate = OrbitalTransferPlanner.forecastAvailability(
            catalog.root(),
            source,
            destination,
            catalog.epochOsu(),
            from.endpointKind(),
            to.endpointKind(),
            session.view.propulsion(),
            catalog.costModel(),
            settings.allowance()
                .getAsDouble());
        return forecastMessage(estimate);
    }

    /** Presents the numerical owner's view without accepting or scheduling a departure. */
    public static TextWidget<?> createForecastGuidanceWidget(Supplier<ForecastEstimate> estimate) {
        return forecastLabel(() -> forecastMessage(estimate.get()));
    }

    private static TextWidget<?> forecastLabel(Supplier<String> message) {
        var label = IKey.dynamic(message)
            .asWidget();
        label.name("rocket.schedule.forecast")
            .size(DETAIL_WIDTH, 65)
            .color(ACCENT);
        return label;
    }

    private static String forecastMessage(ForecastEstimate estimate) {
        if (estimate.availability() == null) return tr(
            "forecast." + estimate.status()
                .name()
                .toLowerCase(java.util.Locale.ROOT));
        var availability = estimate.availability();
        String text = tr("forecast_available", Math.round(availability.availableFraction() * 100));
        if (!availability.periodic()) return text + "\n" + tr("forecast_fixed");
        text += "\n" + (availability.maximumWaitSeconds()
            .isPresent()
                ? tr(
                    "forecast_max_wait",
                    Math.round(
                        availability.maximumWaitSeconds()
                            .getAsDouble()))
                : tr("forecast_no_window"));
        return text + "\n" + tr("forecast_coverage", Math.round(availability.coverageSeconds()));
    }

    private static void vehicleOverview(ParentWidget<?> content, Session session) {
        var view = session.view;
        var rows = new ListWidget<>().name("rocket.schedule.overview.rows")
            .size(WIDTH - 2 * INSET, CONTENT_HEIGHT);
        rows.child(summaryLine(status(session), ACCENT));
        rows.child(
            summaryLine(
                tr(
                    "summary_location",
                    bodyName(
                        view.place()
                            .body(),
                        session.catalog),
                    tr(
                        "context." + view.place()
                            .context()
                            .name()
                            .toLowerCase(java.util.Locale.ROOT)),
                    targetName(view.place(), session.catalog)),
                TEXT));
        rows.child(
            summaryLine(
                tr(
                    "summary_modules",
                    view.modules()
                        .size(),
                    view.requiredModules()),
                TEXT));
        if (view.propulsion() == null) rows.child(summaryLine(tr("summary_propulsion_unavailable"), MUTED));
        else {
            rows.child(
                IKey.lang(
                    PREFIX + "summary_delta_v",
                    deltaVText(
                        view.propulsion()
                            .usableDeltaVMps()))
                    .asWidget()
                    .name("rocket.schedule.summary.delta_v")
                    .size(WIDTH - 2 * INSET - 8, SUMMARY_ROW_HEIGHT)
                    .color(ACCENT));
            rows.child(
                summaryLine(
                    tr(
                        "summary_mass",
                        Math.round(
                            view.propulsion()
                                .wetMassKg()),
                        Math.round(
                            view.propulsion()
                                .usablePropellantKg())),
                    TEXT));
        }
        if (view.returnSiloId() != null) rows.child(
            summaryLine(
                tr(
                    "summary_return",
                    session.catalog.endpoints()
                        .stream()
                        .filter(
                            endpoint -> endpoint.endpointId()
                                .equals(view.returnSiloId()))
                        .map(EndpointChoice::routingName)
                        .findFirst()
                        .orElse(
                            view.returnSiloId()
                                .toString()
                                .substring(0, 8))),
                ACCENT));
        rows.child(summaryLine(tr("summary_propellant"), ACCENT));
        if (view.propellant()
            .isEmpty()) rows.child(summaryLine(tr("summary_empty"), MUTED));
        view.propellant()
            .entrySet()
            .stream()
            .sorted(java.util.Comparator.comparing(entry -> resourceName(entry.getKey())))
            .forEach(
                entry -> rows
                    .child(summaryLine(tr("summary_amount", resourceName(entry.getKey()), entry.getValue()), TEXT)));
        rows.child(summaryLine(tr("summary_cargo"), ACCENT));
        if (view.cargo()
            .isEmpty()) rows.child(summaryLine(tr("summary_empty"), MUTED));
        view.cargo()
            .entrySet()
            .stream()
            .sorted(java.util.Comparator.comparing(entry -> resourceName(entry.getKey())))
            .forEach(
                entry -> rows
                    .child(summaryLine(tr("summary_amount", resourceName(entry.getKey()), entry.getValue()), TEXT)));
        rows.child(summaryLine(tr("summary_condition"), ACCENT));
        for (var module : view.modules()) {
            rows.child(
                summaryLine(
                    tr(
                        "summary_module",
                        StatCollector.translateToLocal(module.nameKey()),
                        Math.round(module.durabilityPercent()),
                        tr(
                            "holder." + module.holder()
                                .name()
                                .toLowerCase(java.util.Locale.ROOT))),
                    TEXT));
            if (module.workKey() != null)
                rows.child(summaryLine(StatCollector.translateToLocal(module.workKey()), MUTED));
            if (module.repairSelected()) rows.child(
                summaryLine(
                    tr(module.repairCompleted() ? "summary_repair_completed" : "summary_repair_selected"),
                    ACCENT));
        }
        content.child(rows);
    }

    private static IWidget summaryLine(String text, int color) {
        return IKey.str(text)
            .asWidget()
            .size(WIDTH - 2 * INSET - 8, SUMMARY_ROW_HEIGHT)
            .color(color);
    }

    private static void addAction(Session session, String type) {
        var visit = session.visit();
        if (visit == null || !session.editable(visit.id())) return;
        if (type.equals("refuel") && !visit.place()
            .canRefuel()) {
            session.fail(PREFIX + "refuel_silo_required");
            return;
        }
        var id = UUID.randomUUID();
        InventoryKey resource = session.catalog.resources()
            .stream()
            .filter(key -> !type.equals("deploy") || key instanceof ItemStackWrapper)
            .filter(key -> !type.equals("refuel") || key instanceof FluidKey)
            .findFirst()
            .orElse(null);
        if (resource == null && !type.equals("repair") && !type.equals("refuel")) {
            session.fail(PREFIX + "no_resources");
            return;
        }
        VehicleSchedule.Action action = switch (type) {
            case "load", "unload" -> new VehicleSchedule.Transfer(
                id,
                resource,
                1,
                type.equals("load") ? VehicleSchedule.Direction.LOAD : VehicleSchedule.Direction.UNLOAD);
            case "refuel" -> VehicleSchedule.Refuel.full(id);
            case "deploy" -> new VehicleSchedule.Deploy(id, (ItemStackWrapper) resource, 1);
            default -> new VehicleSchedule.Repair(id, 50);
        };
        var actions = new ArrayList<>(visit.actions());
        actions.add(action);
        session.replaceVisit(new VehicleSchedule.Visit(visit.id(), visit.place(), actions, visit.blockDockActions()));
        session.selectedAction = id;
        session.picker = Picker.NONE;
    }

    private static VehicleSchedule.Action selectedAction(Session session) {
        var visit = session.visit();
        return visit == null ? null
            : visit.actions()
                .stream()
                .filter(
                    a -> a.id()
                        .equals(session.selectedAction))
                .findFirst()
                .orElse(null);
    }

    private static void actionDetails(ParentWidget<?> details, Session session) {
        var action = selectedAction(session);
        if (action == null) {
            session.selectedAction = null;
            return;
        }
        boolean editable = session.editable(
            session.visit()
                .id());
        details.child(button("back", () -> {
            session.selectedAction = null;
            session.changed();
        }).pos(0, 0)
            .size(80, 18));
        details.child(
            symbol("^", () -> session.moveAction(action.id(), -1)).name("rocket.schedule.action.up." + action.id())
                .right(56)
                .top(0)
                .size(24, 18)
                .setEnabledIf(w -> editable));
        details.child(
            symbol("v", () -> session.moveAction(action.id(), 1)).name("rocket.schedule.action.down." + action.id())
                .right(28)
                .top(0)
                .size(24, 18)
                .setEnabledIf(w -> editable));
        details.child(
            symbol("x", () -> session.removeAction(action.id()))
                .name("rocket.schedule.action.details.remove." + action.id())
                .right(0)
                .top(0)
                .size(24, 18)
                .setEnabledIf(w -> editable));
        details.child(
            IKey.dynamic(() -> actionName(action))
                .asWidget()
                .pos(0, 30)
                .widthRel(1f)
                .height(20)
                .color(ACCENT));
        if (action instanceof VehicleSchedule.Repair repair) {
            details.child(
                IKey.lang(PREFIX + "repair_threshold")
                    .asWidget()
                    .pos(0, 66)
                    .widthRel(1f)
                    .height(34)
                    .color(TEXT));
            details.child(
                field(
                    session,
                    () -> Double.toString(repair.thresholdPercent()),
                    value -> session.replaceAction(
                        new VehicleSchedule.Repair(action.id(), Double.parseDouble(value), repair.returnSiloId())))
                            .name("rocket.schedule.threshold")
                            .pos(0, 108)
                            .size(120, 20)
                            .setEnabledIf(w -> editable));
            details.child(
                IKey.lang(PREFIX + "repair_hint")
                    .asWidget()
                    .pos(0, 139)
                    .widthRel(1f)
                    .height(25)
                    .color(MUTED));
            if (editable) {
                final double[] threshold = { repair.thresholdPercent() };
                var slider = new SliderWidget().bounds(0, 100)
                    .stopper(1)
                    .value(new DoubleValue.Dynamic(() -> threshold[0], value -> threshold[0] = value))
                    .name("rocket.schedule.threshold.slider")
                    .pos(132, 108)
                    .widthRelOffset(1f, -132)
                    .height(20)
                    .background(EnumTextures.GUI_WELL.getImage());
                slider.onUpdateListener(widget -> {
                    if (!widget.isDragging() && threshold[0] != repair.thresholdPercent()) session
                        .replaceAction(new VehicleSchedule.Repair(action.id(), threshold[0], repair.returnSiloId()));
                });
                details.child(slider);
            }
            details.child(
                IKey.dynamic(
                    () -> tr(
                        "repair_preview",
                        session.view.modules()
                            .stream()
                            .filter(module -> repair.qualifies(module.durabilityPercent()))
                            .count()))
                    .asWidget()
                    .pos(0, 171)
                    .color(ACCENT));
            var modules = new ListWidget<>().name("rocket.schedule.repair.candidates")
                .pos(0, 185)
                .widthRel(1f)
                .height(38);
            var displayed = new ArrayList<Rocket.ModuleView>();
            modules.onUpdateListener(widget -> {
                if (displayed.equals(session.view.modules())) return;
                displayed.clear();
                displayed.addAll(session.view.modules());
                widget.removeAll();
                for (var module : displayed) if (repair.qualifies(module.durabilityPercent())) {
                    widget.child(
                        IKey.lang(
                            PREFIX + "repair_preview_module",
                            StatCollector.translateToLocal(module.nameKey()),
                            Math.round(module.durabilityPercent()))
                            .asWidget()
                            .widthRel(1f)
                            .height(SUMMARY_ROW_HEIGHT)
                            .color(TEXT));
                }
                widget.scheduleResize();
            }, true);
            details.child(modules);
            return;
        }
        if (action instanceof VehicleSchedule.Refuel refuel) {
            refuelDetails(details, session, refuel, editable);
            return;
        }
        details.child(
            buttonText(() -> resourceName(actionResource(action)), () -> openPicker(session, Picker.RESOURCE))
                .name("rocket.schedule.resource")
                .pos(0, 65)
                .widthRel(1f)
                .height(24)
                .setEnabledIf(w -> editable));
        details.child(
            IKey.lang(PREFIX + (action instanceof VehicleSchedule.Refuel ? "target_amount" : "amount"))
                .asWidget()
                .pos(0, 104)
                .color(TEXT));
        details.child(
            field(
                session,
                () -> Long.toString(actionAmount(action)),
                value -> session.replaceAction(withAmount(action, Long.parseLong(value))))
                    .name("rocket.schedule.amount")
                    .pos(0, 122)
                    .size(160, 20)
                    .setEnabledIf(w -> editable));
        if (action instanceof VehicleSchedule.Transfer transfer) details.child(
            buttonText(
                () -> tr(transfer.direction() == VehicleSchedule.Direction.LOAD ? "load" : "unload"),
                () -> session.replaceAction(
                    new VehicleSchedule.Transfer(
                        action.id(),
                        transfer.resource(),
                        transfer.amount(),
                        transfer.direction() == VehicleSchedule.Direction.LOAD ? VehicleSchedule.Direction.UNLOAD
                            : VehicleSchedule.Direction.LOAD))).name("rocket.schedule.direction")
                                .pos(0, 156)
                                .widthRel(1f)
                                .height(20)
                                .setEnabledIf(w -> editable));
        details.child(
            IKey.lang(PREFIX + (action instanceof VehicleSchedule.Deploy ? "deploy_hint" : "exact_hint"))
                .asWidget()
                .pos(0, 188)
                .widthRel(1f)
                .height(34)
                .color(MUTED));
    }

    private static void refuelDetails(ParentWidget<?> details, Session session, VehicleSchedule.Refuel refuel,
        boolean editable) {
        details.child(
            buttonText(
                () -> tr(refuel.toFull() ? "refuel_full" : "refuel_exact"),
                () -> session.replaceAction(
                    refuel.toFull() ? new VehicleSchedule.Refuel(refuel.id(), Map.of())
                        : VehicleSchedule.Refuel.full(refuel.id()))).name("rocket.schedule.refuel.mode")
                            .pos(0, 56)
                            .widthRel(1f)
                            .height(22)
                            .setEnabledIf(w -> editable));
        if (refuel.toFull()) {
            details.child(
                IKey.lang(PREFIX + "refuel_full_hint")
                    .asWidget()
                    .pos(0, 90)
                    .widthRel(1f)
                    .height(60)
                    .color(MUTED));
            return;
        }
        details.child(
            IKey.lang(PREFIX + "refuel_targets")
                .asWidget()
                .pos(0, 88)
                .color(TEXT));
        var targets = new ListWidget<>().pos(0, 106)
            .widthRel(1f)
            .height(86);
        for (var entry : refuel.targets()
            .entrySet()) {
            var row = new ParentWidget<>().widthRel(1f)
                .height(35);
            row.child(
                IKey.dynamic(() -> resourceName(entry.getKey()))
                    .asWidget()
                    .pos(0, 3)
                    .widthRelOffset(1f, -126)
                    .height(28)
                    .color(TEXT));
            row.child(field(session, () -> Long.toString(entry.getValue()), value -> {
                var edited = new java.util.LinkedHashMap<>(refuel.targets());
                edited.put(entry.getKey(), Long.parseLong(value));
                session.replaceAction(new VehicleSchedule.Refuel(refuel.id(), edited));
            }).right(26)
                .top(4)
                .size(96, 20)
                .setEnabledIf(w -> editable));
            row.child(symbol("x", () -> {
                var edited = new java.util.LinkedHashMap<>(refuel.targets());
                edited.remove(entry.getKey());
                session.replaceAction(new VehicleSchedule.Refuel(refuel.id(), edited));
            }).right(0)
                .top(4)
                .size(20, 20)
                .setEnabledIf(w -> editable));
            targets.child(row);
        }
        details.child(targets);
        details.child(
            button("add_fluid", () -> openPicker(session, Picker.RESOURCE)).pos(0, 198)
                .size(140, 20)
                .setEnabledIf(w -> editable));
        details.child(
            IKey.lang(PREFIX + "refuel_hint")
                .asWidget()
                .pos(0, 230)
                .widthRel(1f)
                .height(14)
                .color(MUTED));
    }

    public static ParentWidget<?> bodySelectionControls(Session session, Supplier<CelestialObjectKey> candidate,
        Runnable select, Runnable cancel) {
        var controls = new ParentWidget<>().name("rocket.schedule.body.selection")
            .size(BODY_SELECTION_WIDTH, BODY_SELECTION_HEIGHT);
        controls.child(
            IKey.dynamic(
                () -> tr(session.canSelectDestinationBody(candidate.get()) ? "body_select_hint" : "known_bodies_hint"))
                .asWidget()
                .pos(0, 0)
                .size(BODY_SELECTION_WIDTH, 43)
                .color(MUTED));
        var confirm = button("select_body", select).name("rocket.schedule.body.confirm")
            .pos(0, 48)
            .size(BODY_SELECTION_WIDTH, 20)
            .setEnabledIf(widget -> session.canSelectDestinationBody(candidate.get()));
        confirm.onMousePressed(mouse -> {
            if (mouse != 0) return false;
            confirm.getContext()
                .removeFocus();
            if (!confirm.canInteract.getAsBoolean()) return false;
            select.run();
            return true;
        });
        controls.child(confirm);
        controls.child(
            button("back", cancel).name("rocket.schedule.body.cancel")
                .pos(0, 74)
                .size(BODY_SELECTION_WIDTH, 20));
        return controls;
    }

    private static ScheduleButton bodyMapButton(Session session, Supplier<String> label) {
        var button = buttonText(label, () -> {});
        button.onMousePressed(mouse -> {
            if (mouse != 0) return false;
            button.getContext()
                .removeFocus();
            if (!button.canInteract.getAsBoolean()) return false;
            session.requestBodyMap();
            return true;
        });
        return button;
    }

    private static void openPicker(Session session, Picker picker) {
        session.picker = picker;
        session.search = "";
        if (picker == Picker.ENDPOINT) {
            var place = session.visit()
                .place();
            session.groupKind = place.target() == null
                ? (place.context() == VehicleSchedule.Context.ORBIT ? Kind.DOCK : Kind.LANDING_PAD)
                : place.target()
                    .kind();
            session.groupStation = place.target() instanceof VehicleSchedule.Group group ? group.stationId() : null;
        }
        session.changed();
    }

    private static void picker(ParentWidget<?> details, Flow choices, Session session) {
        details.child(button("back", () -> {
            session.picker = Picker.NONE;
            session.changed();
        }).pos(0, 0)
            .size(60, 18));
        details.child(field(session, () -> session.search, text -> {
            session.search = text;
            session.changed();
        }).name("rocket.schedule.search")
            .pos(67, 0)
            .widthRelOffset(1f, -67)
            .height(18)
            .hintText(tr("search_choices")));
        switch (session.picker) {
            case ACTION -> {
                var place = session.visit()
                    .place();
                boolean found = false;
                for (String type : List.of("load", "unload", "refuel", "repair", "deploy")) {
                    if (!matches(tr(type), session.search)) continue;
                    found = true;
                    boolean available = switch (type) {
                        case "refuel" -> place.canRefuel();
                        case "repair" -> place.target() != null && place.target()
                            .kind() == Kind.LANDING_PAD;
                        default -> true;
                    };
                    var row = new ParentWidget<>().widthRel(1f)
                        .height(ACTION_CHOICE_HEIGHT);
                    row.child(
                        button(type, () -> addAction(session, type)).widthRel(1f)
                            .height(ACTION_BUTTON_HEIGHT)
                            .overlay(
                                IKey.lang(PREFIX + type)
                                    .color(available ? TEXT : MUTED)
                                    .alignment(Alignment.CenterLeft)
                                    .asIcon()
                                    .expandWidth()
                                    .marginLeft(CARD_PADDING)
                                    .marginRight(CARD_PADDING))
                            .setEnabledIf(w -> available));
                    String description = !available && type.equals("refuel") ? "refuel_silo_hint"
                        : !available && type.equals("repair") ? "repair_pad_hint" : "action_description." + type;
                    row.child(
                        IKey.lang(PREFIX + description)
                            .asWidget()
                            .pos(CARD_PADDING, ACTION_CHOICE_DESCRIPTION_Y)
                            .widthRelOffset(1f, -2 * CARD_PADDING)
                            .height(ACTION_CHOICE_HEIGHT - ACTION_CHOICE_DESCRIPTION_Y)
                            .color(available ? MUTED : GalaxiaGuiStyle.WARN));
                    choices.child(row);
                }
                if (!found) choices.child(
                    IKey.lang(PREFIX + "no_matching_actions")
                        .asWidget()
                        .widthRel(1f)
                        .height(ACTION_CHOICE_HEIGHT)
                        .color(MUTED));
            }
            case ENDPOINT -> endpointChoices(choices, session);
            case RESOURCE -> {
                var action = selectedAction(session);
                for (var resource : session.catalog.resources()) {
                    if (action instanceof VehicleSchedule.Refuel && !(resource instanceof FluidKey)) continue;
                    if (action instanceof VehicleSchedule.Deploy && !(resource instanceof ItemStackWrapper)) continue;
                    if (!matches(resourceName(resource), session.search)) continue;
                    choices.child(buttonText(() -> resourceName(resource), () -> {
                        session.replaceAction(withResource(action, resource));
                        session.picker = Picker.NONE;
                    }).widthRel(1f)
                        .height(22));
                }
            }
            default -> {}
        }
    }

    private static void endpointChoices(Flow choices, Session session) {
        var visitId = session.visit()
            .id();
        var place = session.visit()
            .place();
        boolean currentSource = session.draft.visits()
            .getFirst()
            .id()
            .equals(visitId)
            && place.body()
                .equals(
                    session.view.place()
                        .body())
            && place.context() == session.view.place()
                .context();
        if (place.context() == VehicleSchedule.Context.ORBIT || currentSource) {
            choices.child(button("no_endpoint", () -> {
                session.replacePlace(new VehicleSchedule.Place(place.body(), place.context(), null));
                session.picker = Picker.NONE;
            }).widthRel(1f)
                .height(22));
        } else {
            choices.child(
                IKey.lang(PREFIX + "manual_automation_hint")
                    .asWidget()
                    .widthRel(1f)
                    .height(38)
                    .color(MUTED));
        }
        choices.child(
            buttonText(
                () -> tr(
                    "group_kind_" + session.groupKind.name()
                        .toLowerCase(java.util.Locale.ROOT)),
                () -> {
                    session.groupKind = Kind.values()[(session.groupKind.ordinal() + 1) % Kind.values().length];
                    session.changed();
                }).name("rocket.schedule.group_kind")
                    .widthRel(1f)
                    .height(22));
        choices.child(buttonText(() -> groupScope(session), () -> {
            var scopes = new ArrayList<UUID>();
            scopes.add(null);
            if (place.context() == VehicleSchedule.Context.ORBIT) session.catalog.stations()
                .stream()
                .filter(
                    station -> station.body()
                        .equals(place.body()))
                .map(StationChoice::stationId)
                .forEach(scopes::add);
            session.groupStation = scopes.get((scopes.indexOf(session.groupStation) + 1) % scopes.size());
            session.changed();
        }).name("rocket.schedule.group_scope")
            .widthRel(1f)
            .height(22));
        choices.child(
            field(
                session,
                () -> place.target() instanceof VehicleSchedule.Group group ? group.routingName() : "",
                value -> {
                    if (!value.isBlank()) {
                        session.replacePlace(
                            visitId,
                            new VehicleSchedule.Place(
                                place.body(),
                                place.context(),
                                new VehicleSchedule.Group(value, session.groupKind, session.groupStation)));
                        session.picker = Picker.NONE;
                    }
                }).name("rocket.schedule.group_name")
                    .hintText(tr("group_name_hint"))
                    .widthRel(1f)
                    .height(20));
        choices.child(
            IKey.lang(PREFIX + "group_hint")
                .asWidget()
                .widthRel(1f)
                .height(32)
                .color(MUTED));
        for (var endpoint : session.catalog.endpoints()) {
            if (!endpoint.body()
                .equals(place.body()) || endpoint.context() != place.context()
                || !matches(endpoint.routingName(), session.search)) continue;
            var row = new ParentWidget<>().widthRel(1f)
                .height(43);
            row.child(
                buttonText(
                    () -> endpoint.routingName() + "  ["
                        + endpoint.endpointId()
                            .toString()
                            .substring(0, 8)
                        + "]",
                    () -> {
                        session.replacePlace(
                            new VehicleSchedule.Place(
                                place.body(),
                                place.context(),
                                new VehicleSchedule.Exact(endpoint.endpointId(), endpoint.kind())));
                        session.picker = Picker.NONE;
                    }).widthRel(1f)
                        .height(22));
            row.child(buttonText(() -> tr("group_named", endpoint.routingName()), () -> {
                session.replacePlace(
                    new VehicleSchedule.Place(
                        place.body(),
                        place.context(),
                        new VehicleSchedule.Group(endpoint.routingName(), endpoint.kind(), endpoint.stationId())));
                session.picker = Picker.NONE;
            }).pos(10, 23)
                .widthRelOffset(1f, -10)
                .height(18));
            choices.child(row);
        }
    }

    private static String groupScope(Session session) {
        if (session.groupStation == null) return tr("group_scope_body");
        String station = session.catalog.stations()
            .stream()
            .filter(
                choice -> choice.stationId()
                    .equals(session.groupStation))
            .findFirst()
            .map(StationChoice::name)
            .orElseGet(
                () -> tr(
                    "station_id",
                    session.groupStation.toString()
                        .substring(0, 8)));
        return tr("group_scope_station", station);
    }

    private static InventoryKey actionResource(VehicleSchedule.Action action) {
        return switch (action) {
            case VehicleSchedule.Transfer transfer -> transfer.resource();
            case VehicleSchedule.Deploy deploy -> deploy.cargo();
            case VehicleSchedule.Refuel refuel -> refuel.targets().keySet().stream().findFirst().orElse(null);
            default -> null;
        };
    }

    private static long actionAmount(VehicleSchedule.Action action) {
        return switch (action) {
            case VehicleSchedule.Transfer transfer -> transfer.amount();
            case VehicleSchedule.Deploy deploy -> deploy.amount();
            case VehicleSchedule.Refuel refuel -> refuel.targets().values().stream().findFirst().orElse(0L);
            default -> 0;
        };
    }

    private static VehicleSchedule.Action withAmount(VehicleSchedule.Action action, long amount) {
        return switch (action) {
            case VehicleSchedule.Transfer transfer -> new VehicleSchedule.Transfer(action.id(), transfer.resource(), amount, transfer.direction());
            case VehicleSchedule.Deploy deploy -> new VehicleSchedule.Deploy(action.id(), deploy.cargo(), amount);
            case VehicleSchedule.Refuel refuel -> new VehicleSchedule.Refuel(action.id(), Map.of((FluidKey) actionResource(refuel), amount));
            default -> action;
        };
    }

    private static VehicleSchedule.Action withResource(VehicleSchedule.Action action, InventoryKey resource) {
        return switch (action) {
            case VehicleSchedule.Transfer transfer -> new VehicleSchedule.Transfer(action.id(), resource, transfer.amount(), transfer.direction());
            case VehicleSchedule.Deploy deploy -> new VehicleSchedule.Deploy(action.id(), (ItemStackWrapper) resource, deploy.amount());
            case VehicleSchedule.Refuel refuel -> {
                var targets = new java.util.LinkedHashMap<>(refuel.targets());
                targets.putIfAbsent((FluidKey) resource, 1L);
                yield new VehicleSchedule.Refuel(action.id(), targets);
            }
            default -> action;
        };
    }

    private static String actionName(VehicleSchedule.Action action) {
        return switch (action) {
            case VehicleSchedule.Transfer transfer -> tr(transfer.direction() == VehicleSchedule.Direction.LOAD ? "load" : "unload")
                + " " + resourceName(transfer.resource());
            case VehicleSchedule.Refuel refuel -> tr("refuel");
            case VehicleSchedule.Deploy deploy -> tr("deploy") + " " + resourceName(deploy.cargo());
            case VehicleSchedule.Repair repair -> tr("repair");
        };
    }

    private static String resourceName(@Nullable InventoryKey resource) {
        if (resource instanceof ItemStackWrapper item) return item.toStack(1)
            .getDisplayName();
        if (resource instanceof FluidKey fluid) return fluid.fluid()
            .getLocalizedName(fluid.toStack(1));
        return tr("choose_resource");
    }

    private static String destinationName(VehicleSchedule.Place place, Catalog catalog) {
        if (place.target() instanceof VehicleSchedule.Group group) return group.routingName();
        if (place.target() != null) return targetName(place, catalog);
        return tr(place.context() == VehicleSchedule.Context.ORBIT ? "parking_orbit" : "current_location");
    }

    private static String placeContext(VehicleSchedule.Place place, Catalog catalog) {
        return tr(
            "destination_context",
            bodyName(place.body(), catalog),
            tr(
                "context." + place.context()
                    .name()
                    .toLowerCase(java.util.Locale.ROOT)));
    }

    private static String targetName(VehicleSchedule.Place place, Catalog catalog) {
        if (place.target() instanceof VehicleSchedule.Group group) return tr("group_named", group.routingName());
        if (place.target() instanceof VehicleSchedule.Exact exact) return catalog.endpoints()
            .stream()
            .filter(
                endpoint -> endpoint.endpointId()
                    .equals(exact.endpointId()))
            .findFirst()
            .map(EndpointChoice::routingName)
            .orElseGet(
                () -> tr(
                    "missing_endpoint",
                    exact.endpointId()
                        .toString()
                        .substring(0, 8)));
        return tr("no_endpoint");
    }

    private static String bodyName(CelestialObjectKey body, Catalog catalog) {
        return catalog.bodies()
            .stream()
            .filter(
                choice -> choice.key()
                    .equals(body))
            .findFirst()
            .map(CelestialObject::displayName)
            .orElseGet(() -> tr("unknown_body"));
    }

    private static String status(Session session) {
        if (session.pending) return tr("pending");
        if (session.problemKey != null) return StatCollector.translateToLocal(session.problemKey);
        if (session.view == null) return tr("loading");
        if (session.simple) {
            if (session.dirty()) return simpleText("advanced_unsaved");
            if (session.view.problemKey() != null) return StatCollector.translateToLocal(session.view.problemKey());
            if (session.simpleHome == null || !(session.simpleHome.target() instanceof VehicleSchedule.Exact home)
                || home.kind() != Kind.SILO) return simpleText("home_required");
            if (session.simpleDirty) return tr("unsaved");
            var delivery = session.view.delivery();
            if (delivery == null) return simpleText("hint");
            boolean complete = delivery.plan()
                .requested()
                .entrySet()
                .stream()
                .allMatch(
                    entry -> delivery.delivered()
                        .getOrDefault(entry.getKey(), 0L) >= entry.getValue());
            return simpleText(complete ? "complete" : delivery.enabled() ? "active" : "stopped");
        }
        if (session.view.delivery() != null) return simpleText("managed_advanced");
        if (!session.draft.hasValidRefueling()) return tr("refuel_silo_required");
        if (session.view.problemKey() != null) return StatCollector.translateToLocal(session.view.problemKey());
        if (!session.safeDurability()) return tr("safe_warning");
        return session.dirty() ? tr("unsaved")
            : tr(
                "status." + session.view.status()
                    .name()
                    .toLowerCase(java.util.Locale.ROOT));
    }

    private static ScheduleButton button(String key, Runnable action) {
        return buttonText(() -> tr(key), action).name("rocket.schedule." + key);
    }

    private static ScheduleButton symbol(String text, Runnable action) {
        String key = switch (text) {
            case "^" -> "move_up";
            case "v" -> "move_down";
            default -> "remove";
        };
        return buttonText(() -> text, action).quiet()
            .dangerIf("x".equals(text))
            .tooltip(tooltip -> tooltip.addLine(IKey.lang(PREFIX + key)));
    }

    private static ScheduleButton buttonText(Supplier<String> text, Runnable action) {
        var button = new ScheduleButton();
        return button.background(new DynamicDrawable(() -> button.surface(false)))
            .hoverBackground(new DynamicDrawable(() -> button.surface(true)))
            .overlay(
                IKey.dynamic(text)
                    .color(button::textColor)
                    .shadow(false))
            .onMousePressed(mouse -> {
                if (mouse != 0 || !button.canInteract.getAsBoolean()) return false;
                action.run();
                return true;
            });
    }

    /** MUI's enabled flag hides widgets. Read-only editor controls stay visible instead. */
    private static final class ScheduleButton extends ButtonWidget<ScheduleButton> {

        private BooleanSupplier canInteract = () -> true;
        private BooleanSupplier primary = () -> false;
        private boolean quiet;
        private boolean danger;

        private ScheduleButton primaryWhen(BooleanSupplier condition) {
            primary = condition;
            return this;
        }

        private ScheduleButton quiet() {
            quiet = true;
            return this;
        }

        private ScheduleButton danger() {
            return dangerIf(true);
        }

        private ScheduleButton dangerIf(boolean value) {
            danger = value;
            return this;
        }

        private int textColor() {
            if (!canInteract.getAsBoolean()) return MUTED;
            if (primary.getAsBoolean()) return GalaxiaGuiStyle.TEXT_TITLE;
            return danger ? GalaxiaGuiStyle.BAD : TEXT;
        }

        private IDrawable surface(boolean hovered) {
            boolean enabled = canInteract.getAsBoolean();
            if (quiet && (!hovered || !enabled)) return IDrawable.EMPTY;
            if (!enabled) return EnumTextures.GUI_WELL.getImage();
            if (primary.getAsBoolean()) return hovered ? IDrawable.of(
                EnumTextures.GUI_BUTTON_SELECTED.getImage(),
                new Rectangle().hollow(1)
                    .color(GalaxiaGuiStyle.TEXT_TITLE))
                : EnumTextures.GUI_BUTTON_SELECTED.getImage();
            return hovered ? EnumTextures.GUI_BUTTON_HOVERED.getImage() : EnumTextures.GUI_BUTTON.getImage();
        }

        @Override
        public ScheduleButton setEnabledIf(Predicate<ScheduleButton> condition) {
            canInteract = () -> condition.test(this);
            return this;
        }
    }

    private static TextFieldWidget field(Session session, Supplier<String> getter, Consumer<String> setter) {
        return new ScheduleField().value(new StringValue.Dynamic(getter, value -> {
            try {
                setter.accept(value);
            } catch (IllegalArgumentException ignored) {
                session.fail(PREFIX + "invalid_value");
            }
        }))
            .setMaxLength(80)
            .autoUpdateOnChange(false)
            .setTextColor(TEXT)
            .hintColor(MUTED)
            .background(EnumTextures.GUI_WELL.getImage());
    }

    private static final class ScheduleField extends TextFieldWidget {

        private BooleanSupplier canInteract = () -> true;

        @Override
        public TextFieldWidget setEnabledIf(Predicate<TextFieldWidget> condition) {
            canInteract = () -> condition.test(this);
            return this;
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            return canInteract.getAsBoolean() ? super.onMousePressed(mouseButton) : Result.IGNORE;
        }

        @Override
        public Result onKeyPressed(char character, int keyCode) {
            return canInteract.getAsBoolean() ? super.onKeyPressed(character, keyCode) : Result.IGNORE;
        }
    }

    private static String deltaVInput(double metresPerSecond) {
        return BigDecimal.valueOf(metresPerSecond)
            .movePointLeft(3)
            .stripTrailingZeros()
            .toPlainString();
    }

    private static OptionalDouble parseDeltaVInput(String kilometresPerSecond) {
        return kilometresPerSecond.isBlank() ? OptionalDouble.empty()
            : OptionalDouble.of(Double.parseDouble(kilometresPerSecond) * METRES_PER_KILOMETRE);
    }

    private static String deltaVText(double metresPerSecond) {
        return BigDecimal.valueOf(metresPerSecond / METRES_PER_KILOMETRE)
            .setScale(3, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(PREFIX + key, args);
    }

    private static String simpleText(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(SIMPLE_PREFIX + key, args);
    }

    private static boolean matches(String value, String query) {
        return value.toLowerCase(java.util.Locale.ROOT)
            .contains(query.toLowerCase(java.util.Locale.ROOT));
    }
}
