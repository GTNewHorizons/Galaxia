package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;

public final class OrbitalPinnedInfoContentBuilder {

    List<PinnedInfoRow> buildRows(CelestialObject body) {
        if (CelestialClient.isAsteroidScanInProgress(body)) {
            return CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
                .map(scan -> List.of(row("name", "???"), row("scan", formatScanProgress(scan))))
                .orElseGet(() -> List.of(row("name", "???")));
        }
        if (CelestialClient.isSensorRevealedAsteroid(body)) {
            return List.of(row("name", "???"));
        }
        List<PinnedInfoRow> rows = new ArrayList<>();
        rows.add(row("name", body.displayName()));
        rows.add(row("type", formatObjectClass(body.objectClass())));
        rows.add(row("landable", isLandable(body) ? tr("value.yes") : tr("value.no")));
        rows.add(row("dangers", buildDangerSummary(body)));
        if (body.objectClass() != CelestialObject.Class.STAR && body.objectClass() != CelestialObject.Class.GALAXY) {
            rows.add(row("surface", formatSurfaceType(body)));
            rows.add(buildOreRow(body));
            buildScanRow(body).ifPresent(rows::add);
        }
        return rows;
    }

    void buildSignatureInto(StringBuilder signature, CelestialObject body, int width, int height) {
        signature.setLength(0);
        signature.append(body.key())
            .append('|')
            .append(width)
            .append('|')
            .append(height)
            .append('|')
            .append(body.displayName())
            .append('|')
            .append(body.objectClass())
            .append('|')
            .append(
                body.properties()
                    .visitable())
            .append('|')
            .append(
                body.properties()
                    .canCreateOutpost())
            .append('|')
            .append(
                body.properties()
                    .radiation())
            .append('|')
            .append(
                body.properties()
                    .temperature());
        String surfaceType = body.properties()
            .metadata()
            .get("surface");
        signature.append('|')
            .append(surfaceType == null ? "" : surfaceType);
        if (!canShowOreDetails(body)) {
            signature.append("|asteroidOre:")
                .append(asteroidOreKnowledge(body));
        } else {
            List<String> gtOreDepositIds = body.properties()
                .gtOreDepositIds();
            signature.append('|')
                .append(gtOreDepositIds.size());
            for (String veinId : gtOreDepositIds) {
                signature.append('|')
                    .append(veinId)
                    .append(',');
            }
        }
        CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
            .ifPresent(
                scan -> signature.append("|asteroidScan:")
                    .append(scan.step())
                    .append(':')
                    .append(scan.elapsedTicks())
                    .append(':')
                    .append(scan.capability()));
    }

    private boolean canShowOreDetails(CelestialObject body) {
        if (!body.key()
            .isMinorBody()) return true;
        return CelestialClient.asteroidProjection(body)
            .map(AsteroidStarmapProjection::canShowOreDetails)
            .orElse(false);
    }

    private PinnedInfoRow buildOreRow(CelestialObject body) {
        if (body.key()
            .isMinorBody()) {
            AsteroidStarmapProjection projection = CelestialClient.asteroidProjection(body)
                .orElse(null);
            CelestialResourceKnowledgeState oreKnowledge = projection == null ? CelestialResourceKnowledgeState.UNKNOWN
                : projection.oreKnowledgeState();
            // Asteroids can have real ore data before the player knows it. The
            // sidebar gates presentation on knowledge state, not on the profile.
            if (oreKnowledge == CelestialResourceKnowledgeState.UNKNOWN) return row("ores", tr("ore.unknown"));
        }

        if (!canShowOreDetails(body)) return row("ores", tr("ore.unknown"));

        List<ItemStack> gtOres = distinctOres(
            body.properties()
                .getResolvedGtOreStacks());
        if (gtOres.isEmpty()) return row("ores", tr("ore.undefined"));
        return new PinnedInfoRow(label("ores"), "", gtOres);
    }

    /** Each ore once, though several veins of the body can carry it */
    private static List<ItemStack> distinctOres(List<ItemStack> ores) {
        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack ore : ores) {
            boolean seen = false;
            for (ItemStack kept : distinct) {
                if (kept.getItem() == ore.getItem() && kept.getItemDamage() == ore.getItemDamage()) {
                    seen = true;
                    break;
                }
            }
            if (!seen) distinct.add(ore);
        }
        return distinct;
    }

    private java.util.Optional<PinnedInfoRow> buildScanRow(CelestialObject body) {
        if (!body.key()
            .isMinorBody()) return java.util.Optional.empty();
        // Active scan progress is keyed by the minor-body id so switching focus
        // between generated asteroids shows the satellite's current target.
        return CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING)
            .map(scan -> row("scan", formatScanProgress(scan)));
    }

    private String formatScanProgress(CelestialDiscoveryScanSnapshot scan) {
        int percent = scan.step()
            .durationTicks() == 0
                ? 100
                : Math.min(
                    100,
                    Math.max(
                        0,
                        (int) Math.round(
                            scan.elapsedTicks() * 100.0
                                / scan.step()
                                    .durationTicks())));
        return StatCollector.translateToLocalFormatted(
            key("scan.progress"),
            tr(
                "scan.pass." + scan.step()
                    .name()
                    .toLowerCase()),
            percent);
    }

    private CelestialResourceKnowledgeState asteroidOreKnowledge(CelestialObject body) {
        return CelestialKnowledgeClientState.resourceKnowledge(body.key())
            .orElse(CelestialResourceKnowledgeState.UNKNOWN);
    }

    private String buildDangerSummary(CelestialObject body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties()
            .radiation() >= 0.25) dangers.add(tr("danger.radiation"));
        if (body.properties()
            .temperature() > 360) dangers.add(tr("danger.heat"));
        if (body.properties()
            .temperature() > 0
            && body.properties()
                .temperature() < 120)
            dangers.add(tr("danger.cold"));
        if (!body.properties()
            .visitable() && body.properties()
                .canCreateOutpost())
            dangers.add(tr("danger.remote"));
        return dangers.isEmpty() ? tr("danger.none") : String.join(", ", dangers);
    }

    private String formatObjectClass(CelestialObject.Class objectClass) {
        return tr(
            "type." + objectClass.name()
                .toLowerCase());
    }

    private boolean isLandable(CelestialObject body) {
        return body.isLandable();
    }

    private String formatSurfaceType(CelestialObject body) {
        String surfaceType = body.properties()
            .metadata()
            .get("surface");
        if (surfaceType == null || surfaceType.isEmpty()) return tr("surface.undefined");
        return formatInfoToken(surfaceType);
    }

    private static PinnedInfoRow row(String labelKeySuffix, String value) {
        return new PinnedInfoRow(label(labelKeySuffix), value);
    }

    private static String label(String suffix) {
        return tr("label." + suffix);
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }

    private static String key(String suffix) {
        return "galaxia.gui.orbital.pinned_info." + suffix;
    }

    private String formatInfoToken(String value) {
        String[] parts = value.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)))
                .append(part.substring(1));
        }
        return out.toString();
    }

    /**
     * Keeps the details window of the hovered or focused body open while there is one. The window can be dragged
     * away but not closed, it follows the selection
     */
    public static final class OrbitalPinnedInfoWidget extends Widget<OrbitalPinnedInfoWidget> {

        interface Callbacks {

            CelestialObject getPinnedInfoBody();

            void buildSignatureInto(StringBuilder buf, CelestialObject body, int width, int height);

            List<PinnedInfoRow> buildRows(CelestialObject body);
        }

        public static final String WINDOW = "starmap.panel.details";
        private static final int MAX_TEXT_WIDTH = 180;
        private static final int ORE_COLUMNS = 5;
        private static final int ORES_SHOWN = 15;
        /** Distance from the right edge of the layer the window opens at */
        private static final int RIGHT_INSET = 18;

        private final Callbacks callbacks;
        private final StarmapViewContext view;
        private final StringBuilder sigBuf = new StringBuilder(256);
        private String lastSignature = "";

        OrbitalPinnedInfoWidget(Callbacks callbacks, StarmapViewContext view) {
            this.callbacks = callbacks;
            this.view = view;
            size(0, 0);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            WindowLayer layer = WindowLayer.of(this);
            if (layer == null) return;
            CelestialObject body = callbacks.getPinnedInfoBody();
            if (body == null) {
                layer.close(WINDOW);
                lastSignature = "";
                return;
            }
            callbacks.buildSignatureInto(sigBuf, body, view.viewportWidth(), view.viewportHeight());
            if (lastSignature.contentEquals(sigBuf) && layer.isOpen(WINDOW)) return;
            lastSignature = sigBuf.toString();
            List<PinnedInfoRow> rows = callbacks.buildRows(body);
            // A new body gets a new title, so the window is opened again where it was left
            layer.close(WINDOW);
            layer.open(
                WindowSpec.of(WINDOW, IKey.str(title(rows, body)))
                    .closable(false)
                    .anchor((lw, lh, w, h) -> new int[] { lw - w - RIGHT_INSET, Math.max(30, (lh - h) / 2) })
                    .body(column -> fill(column, rows, body)));
        }

        private static String title(List<PinnedInfoRow> rows, CelestialObject body) {
            for (PinnedInfoRow row : rows) {
                if (label("name").equals(row.label())) return row.value();
            }
            return body.displayName();
        }

        private static void fill(GalaxiaUi.Column column, List<PinnedInfoRow> rows, CelestialObject body) {
            for (PinnedInfoRow row : rows) {
                if (label("name").equals(row.label())) continue;
                if (row.inlineItems()) {
                    Flow line = GalaxiaUi.row()
                        .child(GalaxiaUi.clipped(row.value(), MAX_TEXT_WIDTH / 2, GalaxiaGuiStyle.TEXT));
                    for (ItemStack stack : row.items()) line.child(GalaxiaUi.item(stack));
                    column.add(line);
                } else if (!row.items()
                    .isEmpty()) {
                        column.section(row.label())
                            .add(
                                GalaxiaUi.itemGrid(
                                    "starmap.details.ores",
                                    row.items(),
                                    ORE_COLUMNS,
                                    ORES_SHOWN,
                                    StatCollector.translateToLocalFormatted(key("ores_of"), body.displayName())));
                    } else {
                        column.keyValue(row.label(), row.value(), MAX_TEXT_WIDTH);
                    }
            }
        }
    }
}
