package com.gtnewhorizons.galaxia.gametests;

import java.awt.Rectangle;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.compat.VisualProspectingCompat;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterSize;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.layers.InteractableLayer;
import com.sinthoras.visualprospecting.database.ClientCache;
import com.sinthoras.visualprospecting.database.CraterPosition;
import com.sinthoras.visualprospecting.integration.model.layers.CraterLayerManager;
import com.sinthoras.visualprospecting.integration.model.locations.CraterLocation;
import com.sinthoras.visualprospecting.integration.model.render.CraterRenderStep;

import detrav.items.DetravMetaGeneratedTool01;
import detrav.items.behaviours.BehaviourDetravToolProspector;
import journeymap.client.ui.fullscreen.Fullscreen;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "visualprospecting", "journeymap" })
public final class CraterMapGameTests {

    private CraterMapGameTests() {}

    @GameTest(timeoutTicks = 1800)
    public static void discoveryAndAutomaticExtractionUpdates(GameTestHelper helper) {
        var fixture = new CraterExtractorGameTests.Fixture();
        var random = new Random(0);
        int diameter = CraterSize.SMALL.sampleDiameter(random);
        long reserve = CraterProfile.IRON_SILICA.initialReserve(CraterSize.SMALL, diameter, random);
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("build an extractor and publish an undiscovered crater", () -> {
                boolean teamSharing = com.sinthoras.visualprospecting.Config.enableTeamSharing;
                com.sinthoras.visualprospecting.Config.enableTeamSharing = false;
                helper.afterTest(() -> com.sinthoras.visualprospecting.Config.enableTeamSharing = teamSharing);
                fixture.build(helper, diameter, reserve);
                fixture.deposits()
                    .register(
                        "qa:undiscovered",
                        fixture.x + 128,
                        fixture.z,
                        48,
                        CraterSize.MEDIUM,
                        CraterProfile.PLATINUM,
                        100);
                VisualProspectingCompat.update(
                    fixture.world,
                    fixture.deposits()
                        .inspect("qa:undiscovered"));
            })
            .client("an update alone does not reveal a crater", client -> {
                if (ClientCache.instance.getCrater(fixture.world.provider.dimensionId, "qa:undiscovered") != null) {
                    throw new AssertionError("An undiscovered crater leaked onto the map");
                }
            })
            .server("use the GT hand prospector inside the deposit", () -> {
                fixture.player.capabilities.isCreativeMode = true;
                fixture.world.setBlock(fixture.x, fixture.y - 4, fixture.z, Blocks.stone);
                helper.afterTest(() -> fixture.world.setBlockToAir(fixture.x, fixture.y - 4, fixture.z));
                var tool = DetravMetaGeneratedTool01.INSTANCE;
                boolean used = new BehaviourDetravToolProspector(0).onItemUse(
                    tool,
                    new ItemStack(tool, 1, 0),
                    fixture.player,
                    fixture.world,
                    fixture.x,
                    fixture.y - 4,
                    fixture.z,
                    1,
                    0.5F,
                    0.5F,
                    0.5F);
                if (!used) throw new AssertionError("GT prospector did not accept the stone block");
            })
            .awaitClient("the native prospector discovers the crater on the map", client -> {
                assertSnapshot(fixture, reserve, 0, 16000);
                CraterPosition crater = ClientCache.instance
                    .getCrater(fixture.world.provider.dimensionId, fixture.crater);
                if (crater.blockX != fixture.x || crater.blockZ != fixture.z
                    || crater.diameter != diameter
                    || crater.initialReserve != reserve) {
                    throw new AssertionError("Map marker lost the crater footprint");
                }
                var tooltip = new java.util.ArrayList<String>();
                new CraterRenderStep(new CraterLocation(crater)).getTooltip(tooltip);
                if (tooltip.size() != 3 || !"Iron Silica Crater".equals(tooltip.get(0))
                    || tooltip.stream()
                        .anyMatch(line -> line.startsWith("visualprospecting."))) {
                    throw new AssertionError("Incomplete or untranslated crater tooltip: " + tooltip);
                }
            })
            .server(
                "stand clear of the crater marker",
                () -> fixture.player.setPositionAndUpdate(fixture.x - 40, fixture.y + 3, fixture.z - 40))
            .awaitClient("client is clear of the marker", client -> {
                if (Math.abs(Minecraft.getMinecraft().thePlayer.posX - (fixture.x - 40)) > 1) {
                    throw new AssertionError("Client position has not updated");
                }
            })
            .client("open the crater layer in JourneyMap", client -> {
                var minecraft = Minecraft.getMinecraft();
                minecraft.gameSettings.guiScale = 2;
                CraterLayerManager.instance.activateLayer();
                minecraft.displayGuiScreen(new Fullscreen());
            })
            .awaitClient(
                "JourneyMap renders the discovered crater",
                client -> assertMapSnapshot(fixture, reserve, 0, 16000))
            .click(craterMarker(fixture))
            .capture("crater-map-discovered")
            .server("start finite wet extraction", fixture::startWet)
            .awaitServer("native completion extracts one finite portion", () -> fixture.assertOutput(16000))
            .awaitClient(
                "reserve updates without another scan",
                client -> assertMapSnapshot(fixture, reserve - 1, 0, 16000))
            .click(craterMarker(fixture))
            .capture("crater-map-after-extraction")
            .server("settle the remaining finite portions before testing fracking", () -> {
                // Exercise native extraction above, then fast-forward finite accounting for the fracking check.
                for (long portion = 1; portion < reserve; portion++) {
                    var operation = fixture.deposits()
                        .begin(fixture.crater, true, false);
                    if (operation == null || operation.fracking()
                        || !fixture.deposits()
                            .finish(operation.id(), true)) {
                        throw new AssertionError("Could not settle a remaining finite portion");
                    }
                }
                fixture.assertReserve(0, 0);
                VisualProspectingCompat.update(
                    fixture.world,
                    fixture.deposits()
                        .inspect(fixture.crater));
            })
            .awaitClient("the map receives the exhausted reserve", client -> assertMapSnapshot(fixture, 0, 0, 16000))
            .server("start fracking without another scan", fixture::startWet)
            .awaitServer("native fracking commits the shared history", () -> fixture.assertOutput(32000))
            .awaitClient(
                "map shows the new fracking price automatically",
                client -> assertMapSnapshot(fixture, 0, 1, 16002))
            .click(craterMarker(fixture))
            .awaitClient("JourneyMap tooltip shows the live price", client -> {
                var layer = (InteractableLayer) CraterLayerManager.instance.getLayerRenderer(SupportedMods.JourneyMap);
                var tooltip = layer.getTooltip();
                if (tooltip == null || tooltip.stream()
                    .noneMatch(line -> line.contains("16002"))) {
                    throw new AssertionError("The live crater tooltip is not visible");
                }
            })
            .capture("crater-map-live-price")
            .client("save and reload the discovered map data", client -> {
                ClientCache.instance.saveVeinCache();
                ClientCache.instance.reset();
            })
            .server("repeat the existing world-id login handshake", () -> {
                com.sinthoras.visualprospecting.teams.TeamProspectionDispatcher
                    .forgetCraterSubscriptions(fixture.player.getUniqueID());
                var offlineOperation = fixture.deposits()
                    .begin(fixture.crater, true, false);
                fixture.deposits()
                    .finish(offlineOperation.id(), true);
                VisualProspectingCompat.update(
                    fixture.world,
                    fixture.deposits()
                        .inspect(fixture.crater));
                com.sinthoras.visualprospecting.VP.network.sendTo(
                    new com.sinthoras.visualprospecting.network.WorldIdNotification(
                        com.sinthoras.visualprospecting.database.WorldIdHandler.getWorldId()),
                    fixture.player);
            })
            .awaitClient("login refreshes the older saved snapshot", client -> assertMapSnapshot(fixture, 0, 2, 16004))
            .succeed();
    }

    private static ClientTarget craterMarker(CraterExtractorGameTests.Fixture fixture) {
        return ClientTarget.of("crater marker", client -> {
            var minecraft = Minecraft.getMinecraft();
            var screen = minecraft.currentScreen;
            var renderer = CraterLayerManager.instance.getLayerRenderer(SupportedMods.JourneyMap);
            var steps = renderer.getRenderSteps();
            if (steps.isEmpty()) return null;
            var step = (CraterRenderStep) steps.get(0);
            double scale = (double) screen.width / minecraft.displayWidth;
            var bounds = new Rectangle(
                (int) (step.getX() * scale),
                (int) (step.getY() * scale),
                (int) Math.ceil(step.getWidth() * scale),
                (int) Math.ceil(step.getHeight() * scale));
            return new ClickTarget(fixture.crater, bounds, () -> ((InteractableLayer) renderer).getTooltip() != null);
        });
    }

    private static void assertSnapshot(CraterExtractorGameTests.Fixture fixture, long reserve, long fracking,
        long price) {
        CraterPosition crater = ClientCache.instance.getCrater(fixture.world.provider.dimensionId, fixture.crater);
        if (crater == null || crater.remainingReserve != reserve
            || crater.completedFracking != fracking
            || crater.nextFrackingFluid != price) {
            throw new AssertionError("Incorrect map snapshot for " + fixture.crater + ": " + crater);
        }
    }

    private static void assertMapSnapshot(CraterExtractorGameTests.Fixture fixture, long reserve, long fracking,
        long price) {
        assertSnapshot(fixture, reserve, fracking, price);
        if (!(Minecraft.getMinecraft().currentScreen instanceof Fullscreen)) {
            throw new AssertionError("JourneyMap is not open");
        }
        for (var location : CraterLayerManager.instance.getVisibleLocations()) {
            if (location instanceof CraterLocation marker && marker.crater.id.equals(fixture.crater)
                && marker.crater.remainingReserve == reserve
                && marker.crater.completedFracking == fracking
                && marker.crater.nextFrackingFluid == price) return;
        }
        throw new AssertionError("Visible crater marker has not refreshed");
    }
}
