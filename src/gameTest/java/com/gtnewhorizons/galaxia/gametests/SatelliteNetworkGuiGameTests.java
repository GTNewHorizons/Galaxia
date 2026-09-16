package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/** A real GUI network showcase in the disposable Horizon world. Scan progress may persist until world shutdown. */
@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class SatelliteNetworkGuiGameTests {

    private static final List<CelestialObjectId> PLANETS = List
        .of(CelestialObjectId.OVERWORLD, CelestialObjectId.MARS, CelestialObjectId.EGORA);
    private static final int ASTEROID_COUNT = 30;
    private static final int ASTEROID_CANDIDATES = 200;

    private SatelliteNetworkGuiGameTests() {}

    /**
     * Integration contract: GUI-created planetary hubs and asteroid satellites form a real capacity-weighted network.
     */
    @GameTest(timeoutTicks = 6000)
    public static void watchPlanetBackboneAndAsteroidNetworkGrow(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare discovered planet and asteroid prerequisites", () -> fixture.prepare(helper));
        GuiTestSupport.openMap(scenario, helper)
            .awaitClient("Debug control ready", c -> {
                if (GuiTestSupport.sidebarDebugTarget() == null)
                    throw new AssertionError("Debug button is unavailable");
                if (GuiTestSupport.map()
                    .isCreativeBuildModeEnabled()) throw new AssertionError("Expected Debug initially off");
                c.afterTest(fixture::cleanupClient);
            })
            .step("enable Debug through sidebar")
            .click(ClientTarget.of("sidebar.debug", c -> GuiTestSupport.sidebarDebugTarget()))
            .awaitClient("Debug enabled", c -> fixture.assertDebug(true));
        for (int index = 0; index < PLANETS.size() + ASTEROID_COUNT; index++) {
            int bodyIndex = index;
            String step = index < PLANETS.size() ? "planet hub " + PLANETS.get(index) + ": "
                : "asteroid " + (index - PLANETS.size() + 1) + "/" + ASTEROID_COUNT + ": ";
            scenario.withinTicks(400)
                .async(
                    step + "create both satellite kinds in one client GUI batch",
                    c -> fixture.createBodyThroughGui(c, bodyIndex))
                .awaitClient(step + "context menu dismissed", c -> {
                    if (GuiTestSupport.findNamedWidget("starmap.context.menu") != null)
                        throw new AssertionError("Satellite context menu is still open after dismissal");
                })
                .awaitServer(step + "completed batch and previous bodies verified on server", () -> {
                    for (int previous = 0; previous <= bodyIndex; previous++) {
                        for (SatelliteKind kind : SatelliteKind.values())
                            fixture.assertCreated(false, previous, kind, expectedCount(previous, kind));
                    }
                })
                .awaitClient(step + "both satellite kinds and exact identities synchronized", c -> {
                    for (SatelliteKind kind : SatelliteKind.values())
                        fixture.assertCreated(true, bodyIndex, kind, expectedCount(bodyIndex, kind));
                });
        }
        scenario.step("disable Debug through sidebar")
            .click(ClientTarget.of("sidebar.debug", c -> GuiTestSupport.sidebarDebugTarget()))
            .awaitClient("Debug disabled", c -> fixture.assertDebug(false));
        scenario.serverSequence()
            .thenIdle(40);
        scenario.awaitServer("server fleet and network remain active", () -> fixture.assertNetwork(false))
            .withinTicks(150)
            .awaitClient("real network synchronized and overview displayed", c -> {
                fixture.assertDebug(false);
                fixture.assertNetwork(true);
                GuiTestSupport.requireScreen("galactic_orbital_map");
            })
            .step("capture completed visible network")
            .capture("network-final");
        scenario.serverSequence()
            .thenIdle(40);
        scenario.succeed();
    }

    private static int expectedCount(int bodyIndex, SatelliteKind kind) {
        return bodyIndex < PLANETS.size() ? (kind == SatelliteKind.COMMUNICATION ? 8 : 2) : 1;
    }

    private static final class Fixture {

        private final List<CelestialObjectKey> bodies = new ArrayList<>();
        private final List<CelestialObjectKey> asteroidCandidates = new ArrayList<>();
        private final Set<CelestialAsset.ID> baseline = new HashSet<>();
        private final Map<CelestialObjectKey, CelestialKnowledgeFacts> priorFacts = new LinkedHashMap<>();
        private final Map<CelestialObjectKey, Map<SatelliteKind, Set<CelestialAsset.ID>>> created = new LinkedHashMap<>();
        private UUID team;

        CompletableFuture<Void> createBodyThroughGui(ClientTest client, int bodyIndex) {
            CompletableFuture<Void> batch = client.click(1, "body context", c -> bodyContextTarget(bodyIndex));
            for (SatelliteKind kind : SatelliteKind.values()) {
                String control = "starmap.context.ADD_" + kind.name() + "_SATELLITE";
                for (int count = 0; count < expectedCount(bodyIndex, kind); count++) {
                    batch = batch
                        .thenCompose(ignored -> client.click(0, control, c -> GuiTestSupport.namedTarget(control)));
                }
            }
            return batch.thenCompose(
                ignored -> client.click(0, "map outside context menu", c -> GuiTestSupport.contextMenuDismissTarget()));
        }

        void prepare(GameTestHelper helper) {
            EntityPlayerMP player = null;
            for (Object candidate : helper.getWorld().playerEntities) {
                if (candidate instanceof EntityPlayerMP actual) {
                    player = actual;
                    break;
                }
            }
            if (player == null) throw new AssertionError("Integrated client player has not joined");
            EntityPlayerMP owner = player;
            team = GTTeamsCompat.getTeamData(owner)
                .orElseThrow()
                .getTeamId();
            CelestialAssetStore.SERVER.assetsViewInternal()
                .forEach(asset -> baseline.add(asset.assetId));
            boolean originalCreative = owner.capabilities.isCreativeMode;
            helper.afterTest(() -> {
                cleanupServer();
                owner.capabilities.isCreativeMode = originalCreative;
                owner.sendPlayerAbilities();
            });
            Map<CelestialObjectKey, CelestialKnowledgeFacts> snapshot = CelestialKnowledgeService.snapshot(team);
            for (CelestialObjectId planet : PLANETS) bodies.add(CelestialObjectKey.registered(planet));
            for (int ordinal = 20; ordinal < 20 + ASTEROID_CANDIDATES; ordinal++) {
                CelestialObjectKey key = CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(ordinal)));
                var body = CelestialRegistry.get(key)
                    .orElseThrow(() -> new AssertionError("Missing generated asteroid " + key));
                if (!body.properties()
                    .canCreateOutpost()) throw new AssertionError("Unsupported asteroid fixture " + key);
                asteroidCandidates.add(key);
                priorFacts.put(key, snapshot.get(key));
                CelestialKnowledgeService.putFacts(team, key, CelestialKnowledgeFacts.discoveredUnknown());
            }
            for (CelestialObjectKey key : bodies) {
                priorFacts.put(key, snapshot.get(key));
                CelestialKnowledgeService.putFacts(team, key, CelestialKnowledgeFacts.discoveredUnknown());
            }
            owner.capabilities.isCreativeMode = true;
            owner.sendPlayerAbilities();
        }

        ClickTarget bodyContextTarget(int bodyIndex) {
            if (bodyIndex < PLANETS.size()) return GuiTestSupport.bodyTarget(bodies.get(bodyIndex), 1);
            var map = GuiTestSupport.map();
            if (map == null) return null;
            for (CelestialObjectKey key : asteroidCandidates) {
                if (bodies.subList(0, bodyIndex)
                    .contains(key)) continue;
                var bounds = map.visibleBodyBounds(key);
                if (bounds != null && key
                    .equals(map.interactableBodyAt(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 1))) {
                    if (bodies.size() == bodyIndex) bodies.add(key);
                    else bodies.set(bodyIndex, key);
                    return GuiTestSupport.bodyTarget(key, 1);
                }
            }
            return null;
        }

        void assertDebug(boolean enabled) {
            var map = GuiTestSupport.map();
            if (map == null || map.isCreativeBuildModeEnabled() != enabled)
                throw new AssertionError("Expected GUI Debug mode=" + enabled);
        }

        void assertCreated(boolean client, int index, SatelliteKind kind, int expectedCount) {
            CelestialObjectKey body = bodies.get(index);
            CelestialAssetStore store = client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
            List<Satellite> matches = store.getStateInternal(team, body)
                .stream()
                .filter(
                    asset -> !baseline.contains(asset.assetId) && asset instanceof Satellite satellite
                        && satellite.satelliteKind() == kind)
                .map(Satellite.class::cast)
                .toList();
            if (matches.size() != expectedCount) throw new AssertionError(
                "Expected " + expectedCount
                    + " GUI-created "
                    + kind
                    + " at "
                    + body
                    + ", got "
                    + matches.size()
                    + ", client="
                    + client
                    + ", actual IDs="
                    + matches.stream()
                        .map(asset -> asset.assetId)
                        .toList()
                    + ", server-created IDs="
                    + created.get(body));
            Set<CelestialAsset.ID> actual = new HashSet<>();
            for (Satellite satellite : matches) {
                if (!satellite.isOperational() || !body.equals(satellite.celestialObjectKey)
                    || !store.isOwnedByInternal(team, satellite.assetId))
                    throw new AssertionError("GUI satellite lost operational status, body or team ownership");
                actual.add(satellite.assetId);
            }
            if (!client) {
                Map<SatelliteKind, Set<CelestialAsset.ID>> known = created
                    .computeIfAbsent(body, ignored -> new LinkedHashMap<>());
                if (!actual.containsAll(known.getOrDefault(kind, Set.of())))
                    throw new AssertionError("Previously created server satellite identities changed");
                known.put(kind, Set.copyOf(actual));
            } else if (!actual.equals(
                created.getOrDefault(body, Map.of())
                    .get(kind)))
                throw new AssertionError("Client satellite identity differs from server");
        }

        void assertNetwork(boolean client) {
            if (created.size() != PLANETS.size() + ASTEROID_COUNT)
                throw new AssertionError("Not all thirty-three network bodies were populated");
            for (int index = 0; index < bodies.size(); index++) {
                for (SatelliteKind kind : SatelliteKind.values())
                    assertCreated(client, index, kind, expectedCount(index, kind));
            }
            SatelliteNetworkState network = client ? SatelliteNetworkClientState.current()
                : SatelliteNetworkService.current(team);
            if (!team.equals(network.teamId())) throw new AssertionError("Network belongs to the wrong team");
            for (CelestialObjectKey body : bodies) {
                if (network.body(body) == null || network.capacityKbps(body) <= 0)
                    throw new AssertionError("Missing active network node at " + body);
            }
            for (int planet = 0; planet < PLANETS.size(); planet++) {
                for (int asteroid = PLANETS.size(); asteroid < bodies.size(); asteroid++) {
                    if (network.capacityKbps(bodies.get(planet)) <= network.capacityKbps(bodies.get(asteroid)))
                        throw new AssertionError("Each planetary hub must have more capacity than every asteroid");
                }
            }
            boolean hasFixtureLink = network.links()
                .stream()
                .anyMatch(
                    link -> bodies.contains(link.from()) && bodies.contains(link.to()) && link.capacityKbps() > 0);
            if (!hasFixtureLink) throw new AssertionError("No real network links connect the fixture bodies");
        }

        void cleanupServer() {
            for (CelestialObjectKey body : bodies) {
                for (CelestialAsset asset : List.copyOf(CelestialAssetStore.SERVER.getStateInternal(team, body))) {
                    if (asset instanceof Satellite && !baseline.contains(asset.assetId))
                        AssetStateSync.SERVER.destroyAsset(asset.assetId);
                }
            }
            Map<CelestialObjectKey, CelestialKnowledgeFacts> current = new LinkedHashMap<>(
                CelestialKnowledgeService.snapshot(team));
            priorFacts.forEach((key, previous) -> {
                if (previous == null) current.remove(key);
                else current.put(key, previous);
            });
            CelestialKnowledgeService.restore(team, current);
        }

        void cleanupClient() {
            created.values()
                .forEach(
                    kinds -> kinds.values()
                        .forEach(ids -> ids.forEach(CelestialAssetStore.CLIENT::destroyAssetInternal)));
            var map = GuiTestSupport.map();
            if (map != null && map.isCreativeBuildModeEnabled()) map.toggleCreativeBuildMode();
        }
    }
}
