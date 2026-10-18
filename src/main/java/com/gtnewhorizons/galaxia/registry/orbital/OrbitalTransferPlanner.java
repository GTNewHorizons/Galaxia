package com.gtnewhorizons.galaxia.registry.orbital;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.state.CelestialObjectKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.CostModel;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.LocalStatus;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.LocalVehicle;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.ManeuverKind;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.ManeuverObligation;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.OrbitalState;

/**
 * Shared (non-client) utilities for interplanetary trajectory planning.
 *
 * <p>
 * Uses the shared Lambert solver and associated helpers so
 * that the server-side logistics engine can compute transfer routes without touching any
 * client-only classes.
 *
 * <p>
 * Time unit convention:
 * <ul>
 * <li>All orbital times are in "orbital simulation units" (OSU). The client viewer
 * advances OSU at {@code OSU_PER_SECOND}× real time: {@code osu = world_ticks * OSU_PER_TICK}.</li>
 * <li>To convert a TOF in OSU to game-clock seconds: {@code tofSeconds = tof / OSU_PER_SECOND}.</li>
 * <li>Physical orbital seconds for thrust calculations use {@link OrbitalMechanics#osuToPhysicalSeconds(double)}.</li>
 * <li>To convert to server ticks: {@code ticks = (int)(tof / OSU_PER_TICK)}.</li>
 * </ul>
 */
public final class OrbitalTransferPlanner {

    /** OSU advance per server tick. */
    public static final double OSU_PER_TICK = 20.0 / 20.0;
    /** OSU advance per real second at 20 TPS. */
    public static final double OSU_PER_SECOND = OSU_PER_TICK * 20.0;
    private static final double MIN_TRANSFER_ANGLE_SIN = 1e-3;
    private static final double BRANCH_TIE_DV_EPS = 1e-7;
    private static final int TRAJECTORY_INTEGRATION_STEPS_PER_SAMPLE = 16;
    private static final double TRAJECTORY_INTEGRATION_TIME_SCALE_FRACTION = 0.03;

    private OrbitalTransferPlanner() {}

    /** Canonical numerical inputs for packaged forecasts, independent of textures, GT content and localization. */
    public static String canonicalForecastInputs(double parkingAltitudeM) {
        return canonicalForecastInputs(
            com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry.numericalDefinitions(parkingAltitudeM));
    }

    private static String canonicalForecastInputs(Map<CelestialObjectKey, CelestialObject> definitions) {
        StringBuilder out = new StringBuilder("rocket_forecast_geometry_v1\n");
        out.append("calculator=lambert64_planar_physical_clearance_v1\n");
        out.append("units=")
            .append(Double.toHexString(OrbitalMechanics.METRES_PER_DISTANCE_UNIT))
            .append(',')
            .append(Double.toHexString(OrbitalMechanics.PHYSICAL_SECONDS_PER_OSU))
            .append('\n');
        new TreeMap<>(definitions).forEach((key, body) -> {
            var orbit = body.orbitalParams();
            var properties = body.properties();
            out.append(
                key.registeredBodyId()
                    .name())
                .append('|')
                .append(
                    body.parentKey() == null ? "none"
                        : body.parentKey()
                            .registeredBodyId()
                            .name())
                .append('|')
                .append(
                    body.objectClass()
                        .name());
            for (double value : new double[] { orbit.semiMajorAxis(), orbit.eccentricity(), orbit.inclination(),
                orbit.longitudeOfAscendingNode(), orbit.argumentOfPeriapsis(), orbit.meanAnomalyAtEpoch(),
                orbit.orbitSpeed(), body.mu(), properties.radiusEarthRelative(), properties.parkingOrbitRadius() })
                out.append('|')
                    .append(Double.toHexString(value));
            if (body.absolutePosition() != null) out.append("|absolute:")
                .append(
                    Double.toHexString(
                        body.absolutePosition()
                            .x()))
                .append(',')
                .append(
                    Double.toHexString(
                        body.absolutePosition()
                            .y()));
            if (properties.asteroidFieldProfile() != null) out.append("|band:")
                .append(
                    Double.toHexString(
                        properties.asteroidFieldProfile()
                            .innerOrbitalRadius()))
                .append(',')
                .append(
                    Double.toHexString(
                        properties.asteroidFieldProfile()
                            .outerOrbitalRadius()));
            out.append('\n');
        });
        return out.toString();
    }

    /** Offline numerical build entrypoint. It never bootstraps Minecraft or the game content registry. */
    public static void main(String[] args) throws java.io.IOException {
        if (args.length == 3 && args[0].equals("inputs")) {
            double parking = args[2].equals("defaults")
                ? com.gtnewhorizons.galaxia.core.config.ConfigRocket.flight.localParkingAltitudeM
                : Double.parseDouble(args[2]);
            java.nio.file.Path output = java.nio.file.Path.of(args[1]);
            java.nio.file.Files.createDirectories(
                output.toAbsolutePath()
                    .getParent());
            java.nio.file.Files
                .writeString(output, canonicalForecastInputs(parking), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("Exported headless numerical inputs: " + output);
            return;
        }
        if (args.length == 6 && args[0].equals("prepare")) {
            prepareForecasts(
                java.nio.file.Path.of(args[1]),
                java.nio.file.Path.of(args[2]),
                args[3],
                args[4],
                Boolean.parseBoolean(args[5]));
            return;
        }
        throw new IllegalArgumentException(
            "Usage: inputs output-file parking-altitude-m | prepare baked-file package-directory fingerprint yes|no release");
    }

    private static void prepareForecasts(java.nio.file.Path baked, java.nio.file.Path packaged, String fingerprint,
        String answer, boolean release) throws java.io.IOException {
        ForecastData data = null;
        boolean readable = false, current = false;
        if (java.nio.file.Files.isRegularFile(baked)) try (var header = new java.io.DataInputStream(
            new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(baked)))) {
                if (!FORECAST_MAGIC.equals(header.readUTF()))
                    throw new java.io.IOException("Unsupported forecast format");
                current = fingerprint.equals(header.readUTF());
                readable = true;
            } catch (java.io.IOException invalid) {
                System.out.println("Rocket forecasts are invalid: " + invalid.getMessage());
            }
        if (!current) System.out.println(
            "Rocket forecasts are missing or stale. Regeneration choices: -ProcketForecastRegenerate=yes or -ProcketForecastRegenerate=no");
        if (release || !current && answer.equals("yes")) {
            long start = System.nanoTime();
            data = generateForecasts(
                com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry.numericalDefinitions(
                    com.gtnewhorizons.galaxia.core.config.ConfigRocket.flight.localParkingAltitudeM),
                128,
                fingerprint);
            java.nio.file.Files.createDirectories(
                baked.toAbsolutePath()
                    .getParent());
            java.nio.file.Path temporary = java.nio.file.Files.createTempFile(
                baked.toAbsolutePath()
                    .getParent(),
                "forecast-",
                ".tmp");
            try {
                writeForecastData(data, java.nio.file.Files.newOutputStream(temporary));
                ForecastData verified = readForecastData(java.nio.file.Files.newInputStream(temporary));
                if (!data.equals(verified)) throw new java.io.IOException("Generated forecast verification failed");
                java.nio.file.Files.move(temporary, baked, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                java.nio.file.Files.deleteIfExists(temporary);
            }
            current = true;
            readable = true;
            System.out.println(
                "Generated " + data.profiles()
                    .size()
                    + " directional circular profiles, "
                    + java.nio.file.Files.size(baked)
                    + " compressed bytes in "
                    + (System.nanoTime() - start) / 1e6
                    + " ms");
        } else if (!current && !answer.equals("no")) throw new IllegalStateException(
            "Noninteractive stale forecast build requires -ProcketForecastRegenerate=yes or -ProcketForecastRegenerate=no");
        java.nio.file.Path directory = packaged.resolve("assets/galaxia/orbital");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Path target = directory.resolve("forecasts.bin.gz");
        if (readable) java.nio.file.Files.copy(baked, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        else java.nio.file.Files.deleteIfExists(target);
        java.nio.file.Files.writeString(
            directory.resolve("forecast-state.properties"),
            "fresh=" + current + "\ninput=" + fingerprint + "\n",
            java.nio.charset.StandardCharsets.UTF_8);
        if (!current) System.out.println(
            "Stale or missing rocket forecasts are disabled in this development package. Exact departure validation remains available.");
    }

    public enum RoutePriority {

        PRIORITIZE_TOF,
        PRIORITIZE_DV;

        public RoutePriority toggled() {
            return this == PRIORITIZE_TOF ? PRIORITIZE_DV : PRIORITIZE_TOF;
        }
    }

    // -------------------------------------------------------------------------
    // Public result type
    // -------------------------------------------------------------------------

    /**
     * Result of a minimum-TOF Lambert route scan.
     *
     * @param tofOsu             time-of-flight in orbital simulation units
     * @param totalDv            total delta-V (departure + capture), orbital velocity units
     * @param departureDv        departure delta-V only
     * @param captureDv          capture delta-V only
     * @param attractorBodyKey   central body used by Lambert
     * @param anchorX            central body world X at departure
     * @param anchorY            central body world Y at departure
     * @param r1x                departure relative X from attractor
     * @param r1y                departure relative Y from attractor
     * @param departureVelocityX inertial transfer velocity X relative to attractor at departure
     * @param departureVelocityY inertial transfer velocity Y relative to attractor at departure
     * @param prograde           true when the selected Lambert branch is counter-clockwise
     */

    public record TransferRoute(double tofOsu, double totalDv, double departureDv, double captureDv,
        CelestialObjectKey attractorBodyKey, double anchorX, double anchorY, double r1x, double r1y,
        double departureVelocityX, double departureVelocityY, boolean prograde) {

        public TransferRoute(double tofOsu, double totalDv, double departureDv) {
            this(
                tofOsu,
                totalDv,
                departureDv,
                Math.max(0.0, totalDv - departureDv),
                null,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                true);
        }

        /** Converts TOF to game-clock seconds at 20 TPS, separate from physical orbital seconds. */
        public double tofSeconds() {
            return tofOsu / OSU_PER_SECOND;
        }

        /** Converts TOF to server ticks (minimum 1). */
        public int tofTicks() {
            return Math.max(1, (int) (tofOsu / OSU_PER_TICK));
        }

        public boolean hasTrajectoryGeometry() {
            return attractorBodyKey != null && Double.isFinite(anchorX)
                && Double.isFinite(anchorY)
                && Double.isFinite(r1x)
                && Double.isFinite(r1y)
                && Double.isFinite(departureVelocityX)
                && Double.isFinite(departureVelocityY)
                && Double.isFinite(tofOsu)
                && tofOsu > 0.0;
        }
    }

    /**
     * One valid scanned Lambert branch. Positions use orbital distance units and all velocities are relative to
     * the attractor in orbital distance units per OSU. The source/destination velocities describe the endpoint
     * reference bodies, while departure/arrival velocities describe the transfer trajectory.
     */
    public record TransferCandidate(TransferRoute route, double r2x, double r2y, double arrivalVelocityX,
        double arrivalVelocityY, double sourceVelocityX, double sourceVelocityY, double destinationVelocityX,
        double destinationVelocityY) {}

    /** One retained branch at a fixed index in the shared 64-duration scan. Velocities use orbital units. */
    public record ForecastCandidate(int timeIndex, boolean prograde, double tofOsu, double departureRelativeSpeed,
        double arrivalRelativeSpeed) {

        public ForecastCandidate {
            if (timeIndex < 0 || timeIndex >= 64
                || !finitePositive(tofOsu)
                || !finiteNonNegative(departureRelativeSpeed)
                || !finiteNonNegative(arrivalRelativeSpeed))
                throw new IllegalArgumentException("Invalid forecast candidate");
        }

        private int slot() {
            return timeIndex * 2 + (prograde ? 0 : 1);
        }
    }

    /** Directional circular geometry. Radii are representative coordinates, never registered phantom bodies. */
    public record CircularProfile(CelestialObjectKey source, CelestialObjectKey destination,
        CelestialObjectKey attractor, double sourceRadius, double destinationRadius, double sourceOmega,
        double destinationOmega, boolean fixedGeometry, List<List<ForecastCandidate>> phases) {

        public CircularProfile {
            if (source == null || destination == null
                || attractor == null
                || !finitePositive(sourceRadius)
                || !finitePositive(destinationRadius)
                || !Double.isFinite(sourceOmega)
                || !Double.isFinite(destinationOmega)
                || phases.isEmpty()
                || phases.size() > 256
                || fixedGeometry && phases.size() != 1) throw new IllegalArgumentException("Invalid circular profile");
            phases = phases.stream()
                .map(rows -> {
                    if (rows.size() > 128 || rows.stream()
                        .map(ForecastCandidate::slot)
                        .distinct()
                        .count() != rows.size())
                        throw new IllegalArgumentException("Duplicate or excessive forecast branches");
                    return List.copyOf(rows);
                })
                .toList();
        }
    }

    /** Packaged numerical output shared by the Star Map and vehicle guidance. Generation is an offline build action. */
    public record ForecastData(String inputFingerprint, String geometryFingerprint, List<CircularProfile> profiles) {

        public ForecastData {
            if (inputFingerprint == null || inputFingerprint.length() != 64
                || geometryFingerprint == null
                || geometryFingerprint.length() != 64)
                throw new IllegalArgumentException("Invalid forecast fingerprint");
            profiles = List.copyOf(profiles);
        }
    }

    public enum ForecastStatus {
        AVAILABLE,
        MISSING,
        STALE,
        INVALID,
        UNAVAILABLE_VEHICLE,
        UNAVAILABLE_LOCAL_DATA,
        UNSUPPORTED_FAMILY
    }

    public record WindowAvailability(double availableFraction, java.util.OptionalDouble nextWindowSeconds,
        java.util.OptionalDouble maximumWaitSeconds, double coverageSeconds, boolean periodic) {

        public WindowAvailability {
            if (!finiteNonNegative(availableFraction) || availableFraction > 1 || !finiteNonNegative(coverageSeconds))
                throw new IllegalArgumentException("Invalid window availability");
        }
    }

    public record ForecastEstimate(ForecastStatus status, @Nullable WindowAvailability availability) {}

    private static final String FORECAST_MAGIC = "galaxia_circular_forecasts_v1";
    private static final String FORECAST_RESOURCE = "/assets/galaxia/orbital/forecasts.bin.gz";

    /** Explicit offline generation. This method is called by the build tool and numerical verification only. */
    public static ForecastData generateForecasts(Map<CelestialObjectKey, CelestialObject> definitions, int phaseSamples,
        String fingerprint) {
        if (phaseSamples < 8 || phaseSamples > 256) throw new IllegalArgumentException("Unsupported phase density");
        List<CircularProfile> profiles = new ArrayList<>();
        List<CelestialObject> bodies = new ArrayList<>(definitions.values());
        bodies.sort(Comparator.comparing(CelestialObject::key));
        for (CelestialObject source : bodies) for (CelestialObject destination : bodies) {
            if (source == destination || source.parentKey() == null
                || !source.parentKey()
                    .equals(destination.parentKey())
                || !circularOrbit(source)
                || !circularOrbit(destination)) continue;
            CelestialObject attractor = definitions.get(source.parentKey());
            if (attractor == null || attractor.mu() <= 0
                || attractor.properties()
                    .radiusEarthRelative() <= 0)
                continue;
            if (source.isAsteroidBelt() && source.properties()
                .asteroidFieldProfile() == null || destination.isAsteroidBelt()
                    && destination.properties()
                        .asteroidFieldProfile() == null)
                continue;
            double sourceOmega = signedOmega(attractor, source), destinationOmega = signedOmega(attractor, destination);
            for (double sourceRadius : representativeRadii(source))
                for (double destinationRadius : representativeRadii(destination)) {
                    List<List<ForecastCandidate>> phases = new ArrayList<>();
                    for (int phase = 0; phase < phaseSamples; phase++) phases.add(
                        forecastRows(
                            computeCircularCandidates(
                                attractor,
                                sourceRadius,
                                sourceOmega,
                                destinationRadius,
                                destinationOmega,
                                2 * Math.PI * phase / phaseSamples),
                            attractor.mu(),
                            sourceRadius,
                            destinationRadius));
                    profiles.add(
                        new CircularProfile(
                            source.key(),
                            destination.key(),
                            attractor.key(),
                            sourceRadius,
                            destinationRadius,
                            sourceOmega,
                            destinationOmega,
                            false,
                            phases));
                }
        }
        // The one supported rigid field shares a source-radius, destination-radius and fixed-separation family.
        for (CelestialObject belt : bodies) {
            if (!belt.isAsteroidBelt() || belt.properties()
                .asteroidFieldProfile() == null) continue;
            CelestialObject attractor = definitions.get(belt.parentKey());
            if (attractor == null || attractor.mu() <= 0
                || attractor.properties()
                    .radiusEarthRelative() <= 0)
                continue;
            double omega = signedOmega(attractor, belt);
            for (double r1 : representativeRadii(belt)) for (double r2 : representativeRadii(belt)) {
                List<List<ForecastCandidate>> phases = new ArrayList<>();
                for (int phase = 0; phase < phaseSamples; phase++) phases.add(
                    forecastRows(
                        computeCircularCandidates(attractor, r1, omega, r2, omega, 2 * Math.PI * phase / phaseSamples),
                        attractor.mu(),
                        r1,
                        r2));
                profiles.add(
                    new CircularProfile(belt.key(), belt.key(), attractor.key(), r1, r2, omega, omega, false, phases));
            }
        }
        // A parent parking orbit can steer before injection. Its targeted arrival separation is rotation invariant.
        for (CelestialObject child : bodies) {
            CelestialObject parent = child.parentKey() == null ? null : definitions.get(child.parentKey());
            if (parent == null || !circularOrbit(child) || !OrbitalMechanics.hasManeuverData(parent.properties()))
                continue;
            double r = child.orbitalParams()
                .semiMajorAxis(),
                parking = parent.properties()
                    .parkingOrbitRadius();
            double omega = signedOmega(parent, child),
                parkingOmega = Math.sqrt(parent.mu() / (parking * parking * parking));
            for (boolean outgoing : new boolean[] { true, false }) {
                List<TransferCandidate> candidates = new ArrayList<>();
                double a = (r + parking) / 2, hohmann = Math.PI * Math.sqrt(a * a * a / parent.mu());
                for (int i = 0; i < 64; i++) {
                    double tof = hohmann * (.1 + 2.9 * i / 63), angle = outgoing ? Math.PI - .05 : Math.PI;
                    double r1 = outgoing ? parking : r, r2 = outgoing ? r : parking;
                    double omega1 = outgoing ? parkingOmega : omega, omega2 = outgoing ? omega : parkingOmega;
                    double x = r2 * Math.cos(angle), y = r2 * Math.sin(angle);
                    candidates.addAll(
                        solveFixedCandidates(
                            parent.key(),
                            parent.mu(),
                            parent.properties()
                                .radiusEarthRelative(),
                            0,
                            0,
                            r1,
                            0,
                            0,
                            omega1 * r1,
                            x,
                            y,
                            -omega2 * y,
                            omega2 * x,
                            tof));
                }
                profiles.add(
                    new CircularProfile(
                        outgoing ? parent.key() : child.key(),
                        outgoing ? child.key() : parent.key(),
                        parent.key(),
                        outgoing ? parking : r,
                        outgoing ? r : parking,
                        outgoing ? parkingOmega : omega,
                        outgoing ? omega : parkingOmega,
                        true,
                        List.of(forecastRows(candidates, parent.mu(), parking, r))));
            }
        }
        return new ForecastData(fingerprint, sha256(canonicalForecastInputs(definitions)), profiles);
    }

    private static double signedOmega(CelestialObject attractor, CelestialObject body) {
        return OrbitalMechanics.getOrbitDirectionSign(body) * 2
            * Math.PI
            / OrbitalMechanics.getOrbitalPeriod(attractor, body.orbitalParams());
    }

    private static double[] representativeRadii(CelestialObject body) {
        var field = body.properties()
            .asteroidFieldProfile();
        return field == null ? new double[] { body.orbitalParams()
            .semiMajorAxis() }
            : new double[] { field.innerOrbitalRadius(), (field.innerOrbitalRadius() + field.outerOrbitalRadius()) / 2,
                field.outerOrbitalRadius() };
    }

    private static List<ForecastCandidate> forecastRows(List<TransferCandidate> candidates, double mu, double r1,
        double r2) {
        double a = (r1 + r2) / 2, hohmann = Math.PI * Math.sqrt(a * a * a / mu);
        return candidates.stream()
            .map(
                c -> new ForecastCandidate(
                    (int) Math.round(
                        (c.route()
                            .tofOsu() / hohmann - .1) * 63 / 2.9),
                    c.route()
                        .prograde(),
                    c.route()
                        .tofOsu(),
                    c.route()
                        .departureDv(),
                    c.route()
                        .captureDv()))
            .toList();
    }

    public static void writeForecastData(ForecastData data, java.io.OutputStream output) throws java.io.IOException {
        try (var out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(output))) {
            out.writeUTF(FORECAST_MAGIC);
            out.writeUTF(data.inputFingerprint());
            out.writeUTF(data.geometryFingerprint());
            out.writeInt(
                data.profiles()
                    .size());
            for (CircularProfile p : data.profiles()) {
                out.writeUTF(
                    p.source()
                        .requireRegisteredBodyId()
                        .name());
                out.writeUTF(
                    p.destination()
                        .requireRegisteredBodyId()
                        .name());
                out.writeUTF(
                    p.attractor()
                        .requireRegisteredBodyId()
                        .name());
                out.writeDouble(p.sourceRadius());
                out.writeDouble(p.destinationRadius());
                out.writeDouble(p.sourceOmega());
                out.writeDouble(p.destinationOmega());
                out.writeBoolean(p.fixedGeometry());
                out.writeInt(
                    p.phases()
                        .size());
                for (var rows : p.phases()) {
                    out.writeInt(rows.size());
                    for (var row : rows) {
                        out.writeByte(row.timeIndex());
                        out.writeBoolean(row.prograde());
                        out.writeDouble(row.tofOsu());
                        out.writeDouble(row.departureRelativeSpeed());
                        out.writeDouble(row.arrivalRelativeSpeed());
                    }
                }
            }
        }
    }

    public static ForecastData readForecastData(java.io.InputStream input) throws java.io.IOException {
        try (var in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(input))) {
            if (!FORECAST_MAGIC.equals(in.readUTF())) throw new java.io.IOException("Unsupported forecast format");
            String fingerprint = in.readUTF(), geometry = in.readUTF();
            int count = bounded(in.readInt(), 0, 4096);
            List<CircularProfile> profiles = new ArrayList<>();
            for (int p = 0; p < count; p++) {
                var source = forecastKey(in.readUTF());
                var target = forecastKey(in.readUTF());
                var attractor = forecastKey(in.readUTF());
                double r1 = in.readDouble(), r2 = in.readDouble(), omega1 = in.readDouble(), omega2 = in.readDouble();
                boolean fixed = in.readBoolean();
                int phases = bounded(in.readInt(), 1, 256);
                List<List<ForecastCandidate>> samples = new ArrayList<>();
                for (int i = 0; i < phases; i++) {
                    int rows = bounded(in.readInt(), 0, 128);
                    List<ForecastCandidate> candidates = new ArrayList<>();
                    for (int j = 0; j < rows; j++) candidates.add(
                        new ForecastCandidate(
                            in.readUnsignedByte(),
                            in.readBoolean(),
                            in.readDouble(),
                            in.readDouble(),
                            in.readDouble()));
                    samples.add(candidates);
                }
                profiles.add(new CircularProfile(source, target, attractor, r1, r2, omega1, omega2, fixed, samples));
            }
            if (in.read() != -1) throw new java.io.IOException("Trailing forecast data");
            return new ForecastData(fingerprint, geometry, profiles);
        }
    }

    private static CelestialObjectKey forecastKey(String value) {
        return CelestialObjectKey
            .registered(com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId.valueOf(value));
    }

    private static int bounded(int value, int low, int high) throws java.io.IOException {
        if (value < low || value > high) throw new java.io.IOException("Forecast count out of bounds");
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return com.google.common.io.BaseEncoding.base16()
                .lowerCase()
                .encode(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record PackagedForecast(ForecastStatus status, @Nullable ForecastData data) {}

    private static final class PackagedForecastHolder {

        private static final PackagedForecast VALUE = loadPackagedForecast();
    }

    private static PackagedForecast loadPackagedForecast() {
        try (var state = OrbitalTransferPlanner.class
            .getResourceAsStream("/assets/galaxia/orbital/forecast-state.properties")) {
            if (state == null) return new PackagedForecast(ForecastStatus.MISSING, null);
            var properties = new java.util.Properties();
            properties.load(state);
            if (!"true".equals(properties.getProperty("fresh")))
                return new PackagedForecast(ForecastStatus.STALE, null);
            try (var input = OrbitalTransferPlanner.class.getResourceAsStream(FORECAST_RESOURCE)) {
                if (input == null) return new PackagedForecast(ForecastStatus.MISSING, null);
                ForecastData data = readForecastData(input);
                if (!data.inputFingerprint()
                    .equals(properties.getProperty("input"))) return new PackagedForecast(ForecastStatus.STALE, null);
                return new PackagedForecast(ForecastStatus.AVAILABLE, data);
            }
        } catch (java.io.IOException | IllegalArgumentException invalid) {
            return new PackagedForecast(ForecastStatus.INVALID, null);
        }
    }

    public static ForecastEstimate forecastAvailability(CelestialObject root, CelestialObject source,
        CelestialObject destination, double epoch, EndpointKind sourceKind, EndpointKind destinationKind,
        LocalVehicle vehicle, CostModel model, double allowanceMps) {
        if (vehicle == null) return new ForecastEstimate(ForecastStatus.UNAVAILABLE_VEHICLE, null);
        PackagedForecast packaged = PackagedForecastHolder.VALUE;
        if (packaged.data() == null) return new ForecastEstimate(packaged.status(), null);
        String geometry = sha256(
            canonicalForecastInputs(com.gtnewhorizons.galaxia.core.config.ConfigRocket.flight.localParkingAltitudeM));
        if (!geometry.equals(
            packaged.data()
                .geometryFingerprint()))
            return new ForecastEstimate(ForecastStatus.STALE, null);
        return estimateAvailability(
            packaged.data(),
            root,
            source,
            destination,
            epoch,
            sourceKind,
            destinationKind,
            vehicle,
            model,
            allowanceMps);
    }

    /** Qualifying time in uniform phase cells. This is an estimate, not a guarantee of continuous windows. */
    public static WindowAvailability summarizeCircularAvailability(boolean[] qualifies, double signedOmega,
        double phase) {
        if (qualifies.length == 0 || !Double.isFinite(signedOmega) || !Double.isFinite(phase))
            throw new IllegalArgumentException("Invalid phase path");
        double step = 2 * Math.PI / qualifies.length, angle = normalize(phase);
        int current = Math.min(qualifies.length - 1, (int) (angle / step)), available = 0;
        for (boolean value : qualifies) if (value) available++;
        if (signedOmega == 0) return new WindowAvailability(
            qualifies[current] ? 1 : 0,
            qualifies[current] ? java.util.OptionalDouble.of(0) : java.util.OptionalDouble.empty(),
            qualifies[current] ? java.util.OptionalDouble.of(0) : java.util.OptionalDouble.empty(),
            0,
            false);
        double speed = Math.abs(signedOmega) * OSU_PER_SECOND;
        java.util.OptionalDouble next = java.util.OptionalDouble.empty(), longest = java.util.OptionalDouble.empty();
        if (available > 0) {
            if (qualifies[current]) next = java.util.OptionalDouble.of(0);
            else for (int i = 1; i <= qualifies.length; i++) {
                int index = Math.floorMod(current + (signedOmega > 0 ? i : -i), qualifies.length);
                if (qualifies[index]) {
                    double delta = signedOmega > 0 ? (current + i) * step - angle : angle - (current - i + 1) * step;
                    next = java.util.OptionalDouble.of(Math.max(0, delta / speed));
                    break;
                }
            }
            int first = 0;
            while (!qualifies[first]) first++;
            int run = 0, max = 0;
            for (int i = 1; i <= qualifies.length; i++) {
                if (qualifies[(first + i) % qualifies.length]) run = 0;
                else {
                    run++;
                    max = Math.max(max, run);
                }
            }
            longest = java.util.OptionalDouble.of(max * step / speed);
        }
        return new WindowAvailability(available / (double) qualifies.length, next, longest, 2 * Math.PI / speed, true);
    }

    /** Vehicle costs are evaluated after candidate interpolation, before availability or priority is derived. */
    public static ForecastEstimate estimateAvailability(ForecastData data, CelestialObject root, CelestialObject source,
        CelestialObject destination, double epoch, EndpointKind sourceKind, EndpointKind destinationKind,
        @Nullable LocalVehicle vehicle, CostModel model, double allowanceMps) {
        if (vehicle == null) return new ForecastEstimate(ForecastStatus.UNAVAILABLE_VEHICLE, null);
        if (!finiteNonNegative(allowanceMps)) throw new IllegalArgumentException("Invalid allowance");
        if (!OrbitalMechanics.hasManeuverData(source.properties())
            || !OrbitalMechanics.hasManeuverData(destination.properties()))
            return new ForecastEstimate(ForecastStatus.UNAVAILABLE_LOCAL_DATA, null);
        if (source == destination) {
            boolean eligible = localForecastEligible(source, sourceKind, destinationKind, vehicle, model, allowanceMps);
            return new ForecastEstimate(
                ForecastStatus.AVAILABLE,
                summarizeCircularAvailability(new boolean[] { eligible }, 0, 0));
        }
        CelestialObject sourceFamily = forecastFamily(root, source),
            destinationFamily = forecastFamily(root, destination);
        var profiles = data.profiles()
            .stream()
            .filter(
                p -> p.source()
                    .equals(sourceFamily.key())
                    && p.destination()
                        .equals(destinationFamily.key()))
            .toList();
        if (profiles.isEmpty()) return new ForecastEstimate(ForecastStatus.UNSUPPORTED_FAMILY, null);
        CircularProfile reference = profiles.getFirst();
        CelestialObject attractor = GalaxiaCelestialAPI.findBodyByKey(root, reference.attractor());
        if (attractor == null) return new ForecastEstimate(ForecastStatus.UNSUPPORTED_FAMILY, null);
        var a = source == attractor ? parkingReference(source, 0, 1) : relative(root, attractor, source, epoch);
        var b = destination == attractor ? parkingReference(destination, 0, 1)
            : relative(root, attractor, destination, epoch);
        double phase = normalize(Math.atan2(b.y(), b.x()) - Math.atan2(a.y(), a.x()));
        int samples = reference.phases()
            .size();
        boolean[] eligible = new boolean[samples];
        for (int i = 0; i < samples; i++) {
            var rows = interpolateForecastCandidates(profiles, a.radius(), b.radius(), 2 * Math.PI * i / samples);
            if (rows == null) return new ForecastEstimate(ForecastStatus.UNSUPPORTED_FAMILY, null);
            eligible[i] = rows.stream()
                .anyMatch(
                    row -> forecastEligible(
                        row,
                        source,
                        destination,
                        attractor,
                        sourceKind,
                        destinationKind,
                        vehicle,
                        model,
                        allowanceMps));
        }
        var current = interpolateForecastCandidates(profiles, a.radius(), b.radius(), phase);
        if (current == null) return new ForecastEstimate(ForecastStatus.UNSUPPORTED_FAMILY, null);
        boolean currentEligible = current.stream()
            .anyMatch(
                row -> forecastEligible(
                    row,
                    source,
                    destination,
                    attractor,
                    sourceKind,
                    destinationKind,
                    vehicle,
                    model,
                    allowanceMps));
        double speed = reference.fixedGeometry() ? 0 : reference.destinationOmega() - reference.sourceOmega();
        WindowAvailability availability = summarizeCircularAvailability(eligible, speed, phase);
        if (speed == 0) availability = summarizeCircularAvailability(new boolean[] { currentEligible }, 0, 0);
        else if (currentEligible) availability = new WindowAvailability(
            availability.availableFraction(),
            java.util.OptionalDouble.of(0),
            availability.maximumWaitSeconds(),
            availability.coverageSeconds(),
            true);
        return new ForecastEstimate(ForecastStatus.AVAILABLE, availability);
    }

    private static CelestialObject forecastFamily(CelestialObject root, CelestialObject body) {
        CelestialObject parent = body.parentKey() == null ? null
            : GalaxiaCelestialAPI.findBodyByKey(root, body.parentKey());
        return parent != null && parent.isAsteroidBelt() ? parent : body;
    }

    @Nullable
    private static ManeuverKind sourceManeuver(EndpointKind endpoint) {
        return switch (endpoint) {
            case SURFACE -> ManeuverKind.ASCENT;
            case DOCK -> ManeuverKind.UNDOCKING;
            case PARKING -> null;
        };
    }

    @Nullable
    private static ManeuverKind destinationManeuver(EndpointKind endpoint) {
        return switch (endpoint) {
            case SURFACE -> ManeuverKind.LANDING;
            case DOCK -> ManeuverKind.DOCKING;
            case PARKING -> null;
        };
    }

    private static double localManeuverCost(CelestialObject body, @Nullable ManeuverKind kind, CostModel model) {
        if (kind == null) return 0;
        return kind == ManeuverKind.UNDOCKING || kind == ManeuverKind.DOCKING ? model.dockingDeltaVMps()
            : OrbitalMechanics.surfaceManeuverDeltaV(body.properties(), model, kind == ManeuverKind.LANDING);
    }

    private static boolean localForecastEligible(CelestialObject body, EndpointKind source, EndpointKind destination,
        LocalVehicle vehicle, CostModel model, double allowance) {
        double mass = vehicle.wetMassKg(), dv = 0;
        for (ManeuverKind kind : new ManeuverKind[] { sourceManeuver(source), destinationManeuver(destination) }) {
            double cost = localManeuverCost(body, kind, model);
            if (kind == null || cost == 0) continue;
            dv += cost;
            if (OrbitalMechanics.approximateBurn(kind, body, vehicle, mass, cost, 0, model)
                .status() != LocalStatus.FEASIBLE) return false;
            mass *= Math.exp(-cost / vehicle.exhaustVelocityMps());
        }
        return dv <= allowance;
    }

    private static boolean forecastEligible(ForecastCandidate row, CelestialObject source, CelestialObject destination,
        CelestialObject attractor, EndpointKind sourceKind, EndpointKind destinationKind, LocalVehicle vehicle,
        CostModel model, double allowance) {
        double mass = vehicle.wetMassKg(), dv = 0;
        ManeuverKind initialKind = sourceManeuver(sourceKind);
        double initialCost = localManeuverCost(source, initialKind, model);
        if (initialKind != null && initialCost > 0) {
            dv += initialCost;
            if (OrbitalMechanics.approximateBurn(initialKind, source, vehicle, mass, initialCost, 0, model)
                .status() != LocalStatus.FEASIBLE) return false;
            mass *= Math.exp(-initialCost / vehicle.exhaustVelocityMps());
        }
        double departure = OrbitalMechanics.velocityToMetresPerSecond(row.departureRelativeSpeed());
        double capture = OrbitalMechanics.velocityToMetresPerSecond(row.arrivalRelativeSpeed());
        if (source != attractor) departure = OrbitalMechanics.parkingWellDeltaV(source.properties(), departure);
        if (destination != attractor) capture = OrbitalMechanics.parkingWellDeltaV(destination.properties(), capture);
        for (int i = 0; i < 2; i++) {
            CelestialObject body = i == 0 ? source : destination;
            double cost = i == 0 ? departure : capture;
            dv += cost;
            if (OrbitalMechanics
                .approximateBurn(
                    i == 0 ? ManeuverKind.DEPARTURE : ManeuverKind.CAPTURE,
                    body,
                    vehicle,
                    mass,
                    cost,
                    0,
                    model)
                .status() != LocalStatus.FEASIBLE) return false;
            mass *= Math.exp(-cost / vehicle.exhaustVelocityMps());
        }
        ManeuverKind finalKind = destinationManeuver(destinationKind);
        double finalCost = localManeuverCost(destination, finalKind, model);
        if (finalKind != null && finalCost > 0) {
            dv += finalCost;
            if (OrbitalMechanics.approximateBurn(finalKind, destination, vehicle, mass, finalCost, 0, model)
                .status() != LocalStatus.FEASIBLE) return false;
        }
        return dv <= allowance;
    }

    /** Interpolates matching branch/time rows only. Invalid neighboring samples never become free candidates. */
    @Nullable
    public static List<ForecastCandidate> interpolateForecastCandidates(List<CircularProfile> profiles,
        double sourceRadius, double destinationRadius, double phase) {
        if (profiles.isEmpty()) return null;
        double[] a = bracket(
            profiles.stream()
                .mapToDouble(CircularProfile::sourceRadius)
                .distinct()
                .sorted()
                .toArray(),
            sourceRadius);
        double[] b = bracket(
            profiles.stream()
                .mapToDouble(CircularProfile::destinationRadius)
                .distinct()
                .sorted()
                .toArray(),
            destinationRadius);
        if (a == null || b == null) return null;
        Map<Integer, double[]> sums = new TreeMap<>();
        int corners = 0;
        for (CircularProfile profile : profiles) {
            double weight = coordinateWeight(profile.sourceRadius(), sourceRadius, a)
                * coordinateWeight(profile.destinationRadius(), destinationRadius, b);
            if (weight <= 0) continue;
            int n = profile.phases()
                .size();
            double index = normalize(phase) * n / (2 * Math.PI);
            if (Math.abs(index - Math.rint(index)) < 1e-10) index = Math.rint(index);
            int lower = (int) Math.floor(index) % n;
            double fraction = n == 1 ? 0 : index - Math.floor(index);
            for (int side = 0; side < (fraction == 0 ? 1 : 2); side++) {
                double contribution = weight * (side == 0 ? 1 - fraction : fraction);
                corners++;
                for (ForecastCandidate row : profile.phases()
                    .get((lower + side) % n)) {
                    double lowerAngle = 2 * Math.PI * lower / n + profile.destinationOmega() * row.tofOsu();
                    double upperAngle = lowerAngle + 2 * Math.PI / n;
                    long winding = (long) Math.floor((side == 0 ? lowerAngle : upperAngle) / (2 * Math.PI));
                    // A zero-angle arrival separates two zero-revolution Lambert paths even with the same sign.
                    // Do not interpolate a cell or radius corners across that discontinuity.
                    if (fraction > 0
                        && Math.floor(lowerAngle / (2 * Math.PI)) != Math.floor(upperAngle / (2 * Math.PI))) continue;
                    double[] values = sums.computeIfAbsent(row.slot(), key -> new double[6]);
                    if (values[4] == 0) values[5] = winding;
                    if (values[5] != winding) continue;
                    values[0] += contribution * row.tofOsu();
                    values[1] += contribution * row.departureRelativeSpeed();
                    values[2] += contribution * row.arrivalRelativeSpeed();
                    values[3] += contribution;
                    values[4]++;
                }
            }
        }
        final int required = corners;
        return sums.entrySet()
            .stream()
            .filter(e -> e.getValue()[4] == required && Math.abs(e.getValue()[3] - 1) < 1e-8)
            .map(e -> {
                double[] v = e.getValue();
                return new ForecastCandidate(e.getKey() / 2, e.getKey() % 2 == 0, v[0], v[1], v[2]);
            })
            .toList();
    }

    @Nullable
    private static double[] bracket(double[] values, double requested) {
        if (values.length == 0 || requested < values[0] - 1e-7 || requested > values[values.length - 1] + 1e-7)
            return null;
        double low = values[0], high = values[values.length - 1];
        for (double value : values) {
            if (Math.abs(value - requested) <= 1e-7) return new double[] { value, value };
            if (value < requested) low = value;
            if (value > requested) {
                high = value;
                break;
            }
        }
        return new double[] { low, high };
    }

    private static double coordinateWeight(double coordinate, double requested, double[] bracket) {
        if (bracket[0] == bracket[1]) return coordinate == bracket[0] ? 1 : 0;
        if (coordinate == bracket[0]) return (bracket[1] - requested) / (bracket[1] - bracket[0]);
        if (coordinate == bracket[1]) return (requested - bracket[0]) / (bracket[1] - bracket[0]);
        return 0;
    }

    public enum EndpointKind {
        SURFACE,
        PARKING,
        DOCK
    }

    public static final String POWERED_MODEL_ID = "patched_conic_cost_v1";

    /** Approved approximate obligations. Geometry remains impulsive, with explicit finite-burn applicability. */
    public record PoweredCandidate(String modelId, @Nullable TransferCandidate geometrySeed,
        List<ManeuverObligation> obligations, double initialMassKg, double finalMassKg, double finalParkingAngleRadians,
        double totalDvMps, double propellantKg, double physicalSeconds, double traveledDistanceMeters,
        Map<CelestialObjectKey, Double> beltDistanceMeters, LocalStatus status) {

        public PoweredCandidate {
            obligations = List.copyOf(obligations);
            beltDistanceMeters = checkedBeltDistances(beltDistanceMeters, traveledDistanceMeters);
            if (!POWERED_MODEL_ID.equals(modelId) || status == null
                || !finitePositive(initialMassKg)
                || !finitePositive(finalMassKg)
                || finalMassKg > initialMassKg
                || !Double.isFinite(finalParkingAngleRadians)
                || !finiteNonNegative(totalDvMps)
                || !finiteNonNegative(propellantKg)
                || !finiteNonNegative(physicalSeconds)
                || !finiteNonNegative(traveledDistanceMeters))
                throw new IllegalArgumentException("Invalid approximate powered candidate");
            double dv = 0, propellant = 0, seconds = 0;
            boolean interbody = false;
            for (int i = 0; i < obligations.size(); i++) {
                ManeuverObligation obligation = obligations.get(i);
                interbody |= obligation.kind() == ManeuverKind.COAST;
                dv += obligation.deltaVMps();
                propellant += obligation.propellantKg();
                seconds += obligation.physicalSeconds();
                if (i > 0 && !close(
                    obligations.get(i - 1)
                        .endEpochOsu(),
                    obligation.startEpochOsu())) throw new IllegalArgumentException("Discontinuous maneuver epochs");
                if (status == LocalStatus.FEASIBLE && obligation.status() != LocalStatus.FEASIBLE)
                    throw new IllegalArgumentException("Infeasible accepted maneuver");
            }
            if (interbody != (geometrySeed != null))
                throw new IllegalArgumentException("Missing or unexpected transfer geometry");
            if (!close(dv, totalDvMps) || !close(propellant, propellantKg)
                || !close(seconds, physicalSeconds)
                || !close(initialMassKg - finalMassKg, propellantKg))
                throw new IllegalArgumentException("Inconsistent maneuver totals");
        }

        public double gameSeconds() {
            return obligations.isEmpty() ? 0
                : (obligations.getLast()
                    .endEpochOsu()
                    - obligations.getFirst()
                        .startEpochOsu())
                    / OSU_PER_SECOND;
        }

        public NBTTagCompound serializeNBT() {
            NBTTagCompound out = new NBTTagCompound();
            out.setString("model", modelId);
            out.setBoolean("hasGeometry", geometrySeed != null);
            if (geometrySeed != null) out.setTag("geometry", encodeGeometry(geometrySeed));
            NBTTagList phases = new NBTTagList();
            for (ManeuverObligation obligation : obligations) phases.appendTag(obligation.serializeNBT());
            out.setTag("obligations", phases);
            out.setDouble("initialMass", initialMassKg);
            out.setDouble("finalMass", finalMassKg);
            out.setDouble("angle", finalParkingAngleRadians);
            out.setDouble("dv", totalDvMps);
            out.setDouble("propellant", propellantKg);
            out.setDouble("seconds", physicalSeconds);
            out.setDouble("distanceMeters", traveledDistanceMeters);
            NBTTagList distances = new NBTTagList();
            beltDistanceMeters.forEach((key, meters) -> {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag("belt", CelestialObjectKeyState.encode(key));
                entry.setDouble("meters", meters);
                distances.appendTag(entry);
            });
            out.setTag("beltDistances", distances);
            out.setString("status", status.name());
            return out;
        }

        public static PoweredCandidate deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "poweredCandidate");
            List<ManeuverObligation> obligations = new ArrayList<>();
            var list = in.compounds("obligations");
            if (list.tagCount() > 64) throw new IllegalArgumentException("Too many maneuver obligations");
            for (int i = 0; i < list.tagCount(); i++)
                obligations.add(ManeuverObligation.deserializeNBT(list.getCompoundTagAt(i)));
            Map<CelestialObjectKey, Double> distances = new TreeMap<>();
            var regions = in.compounds("beltDistances");
            if (regions.tagCount() > 64) throw new IllegalArgumentException("Too many crossed belts");
            for (int i = 0; i < regions.tagCount(); i++) {
                var entry = in.element("beltDistances", i, regions.getCompoundTagAt(i));
                var key = CelestialObjectKeyState.decode(entry.compound("belt"));
                if (distances.put(key, entry.doubleValue("meters")) != null)
                    throw new IllegalArgumentException("Duplicate crossed belt");
            }
            return new PoweredCandidate(
                in.string("model"),
                in.bool("hasGeometry") ? decodeGeometry(in.compound("geometry")) : null,
                obligations,
                in.doubleValue("initialMass"),
                in.doubleValue("finalMass"),
                in.doubleValue("angle"),
                in.doubleValue("dv"),
                in.doubleValue("propellant"),
                in.doubleValue("seconds"),
                in.doubleValue("distanceMeters"),
                distances,
                in.enumValue(LocalStatus.class, "status"));
        }
    }

    public record PoweredPlan(LocalStatus status, List<PoweredCandidate> candidates) {

        public PoweredPlan {
            candidates = List.copyOf(candidates);
        }
    }

    /** Conservative future travel in this bounded model. Exposure is a union, never a sum of overlapping belts. */
    public record TravelExposureBound(double distanceMeters, double beltDistanceMeters) {

        public TravelExposureBound {
            if (!finiteNonNegative(distanceMeters) || !finiteNonNegative(beltDistanceMeters)
                || beltDistanceMeters > distanceMeters)
                throw new IllegalArgumentException("Invalid travel exposure bound");
        }
    }

    /**
     * Analytic bound for local transitions and direct circular single-phase families. A missing result means the
     * family has no proved bound here. It never means zero future wear. Allowance bounds the approximate burn path.
     */
    @Nullable
    public static TravelExposureBound maximumTravelExposure(CelestialObject root, CelestialObject source,
        CelestialObject destination, EndpointKind sourceKind, EndpointKind destinationKind, double allowanceMps,
        CostModel model) {
        if (!finiteNonNegative(allowanceMps))
            throw new IllegalArgumentException("Finite nonnegative allowance required");
        if (!OrbitalMechanics.hasManeuverData(source.properties())
            || !OrbitalMechanics.hasManeuverData(destination.properties())) return null;
        double local = 0;
        if (sourceKind == EndpointKind.SURFACE) local += OrbitalMechanics.distanceToMetres(
            source.properties()
                .parkingOrbitRadius())
            - source.properties()
                .radiusMetres();
        if (destinationKind == EndpointKind.SURFACE) local += OrbitalMechanics.distanceToMetres(
            destination.properties()
                .parkingOrbitRadius())
            - destination.properties()
                .radiusMetres();
        if (sourceKind == EndpointKind.DOCK) local += orbitalBurnDistanceBound(source, model.dockingDeltaVMps(), model);
        if (destinationKind == EndpointKind.DOCK)
            local += orbitalBurnDistanceBound(destination, model.dockingDeltaVMps(), model);
        if (source == destination) return new TravelExposureBound(
            local,
            possiblyInsideBelt(
                root,
                source,
                source.properties()
                    .parkingOrbitRadius()) ? local : 0);
        CelestialObject attractor = commonAttractor(root, source, destination);
        if (attractor == null || attractor.mu() <= 0
            || attractor.properties()
                .radiusEarthRelative() <= 0
            || depth(root, attractor, source) > 1
            || depth(root, attractor, destination) > 1) return null;
        if ((source != attractor && !circularOrbit(source))
            || (destination != attractor && !circularOrbit(destination))) return null;
        double r1 = source == attractor ? source.properties()
            .parkingOrbitRadius()
            : source.orbitalParams()
                .semiMajorAxis();
        double r2 = destination == attractor ? destination.properties()
            .parkingOrbitRadius()
            : destination.orbitalParams()
                .semiMajorAxis();
        double mu = OrbitalMechanics.muToSI(attractor.mu());
        double radius = OrbitalMechanics.distanceToMetres(Math.max(r1, r2));
        double semiMajor = OrbitalMechanics.distanceToMetres((r1 + r2) / 2);
        double maximumSeconds = 3 * Math.PI * Math.sqrt(semiMajor * semiMajor * semiMajor / mu);
        // Any interior apocenter requires an outward and inward crossing of the endpoint radius envelope.
        // Bound elliptic radial speed by parabolic escape speed and integrate dr/sqrt(2mu/r) in both directions.
        double maximumRadius = Math.pow(Math.pow(radius, 1.5) + .75 * Math.sqrt(2 * mu) * maximumSeconds, 2.0 / 3.0);
        // The zero-revolution solver turns through less than 2pi and has at most three radial monotone pieces.
        double coast = (3 + 2 * Math.PI) * maximumRadius;
        double sourceParking = OrbitalMechanics.distanceToMetres(
            source.properties()
                .parkingOrbitRadius());
        local += 2 * Math.PI * sourceParking;
        for (CelestialObject body : List.of(source, destination)) {
            local += orbitalBurnDistanceBound(body, allowanceMps, model);
        }
        double distance = coast + local;
        boolean exposed = possiblyInsideBelt(root, attractor, OrbitalMechanics.metresToDistance(maximumRadius))
            || possiblyInsideBelt(
                root,
                source,
                source.properties()
                    .parkingOrbitRadius())
            || possiblyInsideBelt(
                root,
                destination,
                destination.properties()
                    .parkingOrbitRadius());
        return new TravelExposureBound(distance, exposed ? distance : 0);
    }

    private static double orbitalBurnDistanceBound(CelestialObject body, double deltaV, CostModel model) {
        if (deltaV == 0) return 0;
        double parking = OrbitalMechanics.distanceToMetres(
            body.properties()
                .parkingOrbitRadius());
        double mu = OrbitalMechanics.muToSI(body.mu());
        return (Math.sqrt(mu / parking) + deltaV / 2) * model.maxOrbitalBurnTimeScaleFraction()
            * Math.sqrt(parking * parking * parking / mu);
    }

    private static boolean circularOrbit(CelestialObject body) {
        var orbit = body.orbitalParams();
        return orbit.semiMajorAxis() > 0 && orbit.eccentricity() == 0 && orbit.inclination() == 0;
    }

    private static boolean possiblyInsideBelt(CelestialObject root, CelestialObject center, double reach) {
        for (BeltBand band : beltBands()) {
            double[] envelope = radialEnvelope(root, band.parent(), center);
            if (envelope == null) envelope = radialEnvelope(root, center, band.parent());
            if (envelope == null
                || Math.max(0, envelope[0] - reach) <= band.outer() && envelope[1] + reach >= band.inner()) return true;
        }
        return false;
    }

    /** Triangle and reverse-triangle bounds retain all relative phases along an authored circular parent chain. */
    @Nullable
    private static double[] radialEnvelope(CelestialObject root, CelestialObject ancestor, CelestialObject body) {
        double sum = 0, largest = 0;
        int remaining = 64;
        while (body != ancestor) {
            if (--remaining == 0 || body.parentKey() == null || !circularOrbit(body)) return null;
            double radius = body.orbitalParams()
                .semiMajorAxis();
            sum += radius;
            largest = Math.max(largest, radius);
            body = GalaxiaCelestialAPI.findBodyByKey(root, body.parentKey());
            if (body == null) return null;
        }
        return new double[] { Math.max(0, 2 * largest - sum), sum };
    }

    public static PoweredPlan computePoweredCandidates(CelestialObject root, CelestialObject source,
        CelestialObject destination, double epoch, EndpointKind sourceKind, double sourceParkingAngleRadians,
        EndpointKind destinationKind, int direction, LocalVehicle vehicle, CostModel model) {
        return approximateCandidates(
            root,
            source,
            destination,
            epoch,
            sourceKind,
            sourceParkingAngleRadians,
            destinationKind,
            direction,
            vehicle,
            model,
            Double.NaN);
    }

    public static PoweredPlan computePoweredFixedCandidates(CelestialObject root, CelestialObject source,
        CelestialObject destination, double epoch, EndpointKind sourceKind, double sourceParkingAngleRadians,
        EndpointKind destinationKind, int direction, LocalVehicle vehicle, CostModel model, double tofOsu) {
        if (!finitePositive(tofOsu)) throw new IllegalArgumentException("Positive flight time required");
        return approximateCandidates(
            root,
            source,
            destination,
            epoch,
            sourceKind,
            sourceParkingAngleRadians,
            destinationKind,
            direction,
            vehicle,
            model,
            tofOsu);
    }

    /** Applies all vehicle verdicts and the allowance before speed/fuel ranking. */
    public static PoweredCandidate selectPoweredCandidate(PoweredPlan plan, double deltaVAllowanceMps,
        RoutePriority priority) {
        if (Double.isNaN(deltaVAllowanceMps) || deltaVAllowanceMps < 0)
            throw new IllegalArgumentException("Invalid allowance");
        Comparator<PoweredCandidate> order = priority == RoutePriority.PRIORITIZE_DV
            ? Comparator.comparingDouble(PoweredCandidate::totalDvMps)
                .thenComparingDouble(PoweredCandidate::gameSeconds)
            : Comparator.comparingDouble(PoweredCandidate::gameSeconds)
                .thenComparingDouble(PoweredCandidate::totalDvMps);
        return plan.candidates()
            .stream()
            .filter(
                candidate -> candidate.status() == LocalStatus.FEASIBLE && candidate.totalDvMps() <= deltaVAllowanceMps)
            .min(order)
            .orElse(null);
    }

    private static PoweredPlan approximateCandidates(CelestialObject root, CelestialObject source,
        CelestialObject destination, double epoch, EndpointKind sourceKind, double angle, EndpointKind destinationKind,
        int direction, LocalVehicle vehicle, CostModel model, double fixedTof) {
        if (!Double.isFinite(epoch) || !Double.isFinite(angle) || (direction != 1 && direction != -1))
            throw new IllegalArgumentException("Invalid departure state");
        if (!OrbitalMechanics.hasManeuverData(source.properties())
            || !OrbitalMechanics.hasManeuverData(destination.properties()))
            return new PoweredPlan(LocalStatus.UNAVAILABLE_DATA, List.of());
        List<ManeuverObligation> prefix = new ArrayList<>();
        double mass = vehicle.wetMassKg();
        ManeuverKind initialKind = sourceManeuver(sourceKind);
        double initialCost = localManeuverCost(source, initialKind, model);
        if (initialKind != null && initialCost > 0) {
            ManeuverObligation initial = OrbitalMechanics
                .approximateBurn(initialKind, source, vehicle, mass, initialCost, epoch, model);
            prefix.add(initial);
            mass *= Math.exp(-initial.deltaVMps() / vehicle.exhaustVelocityMps());
            epoch = initial.endEpochOsu();
            if (initial.status() != LocalStatus.FEASIBLE) return new PoweredPlan(
                initial.status(),
                List.of(result(root, prefix, vehicle, angle, null, initial.status())));
        }
        if (source == destination) {
            List<ManeuverObligation> obligations = new ArrayList<>(prefix);
            ManeuverKind finalKind = destinationManeuver(destinationKind);
            double finalCost = localManeuverCost(destination, finalKind, model);
            if (finalKind != null && finalCost > 0) obligations
                .add(OrbitalMechanics.approximateBurn(finalKind, destination, vehicle, mass, finalCost, epoch, model));
            PoweredCandidate candidate = result(root, obligations, vehicle, angle, null, LocalStatus.FEASIBLE);
            return new PoweredPlan(candidate.status(), List.of(candidate));
        }
        CelestialObject attractor = commonAttractor(root, source, destination);
        if (attractor == null || attractor.mu() <= 0
            || attractor.properties()
                .radiusEarthRelative() <= 0
            || !wellDataAvailable(root, attractor, source)
            || !wellDataAvailable(root, attractor, destination))
            return new PoweredPlan(LocalStatus.UNAVAILABLE_DATA, List.of());
        OrbitalMechanics.OrbitalState src = relative(root, attractor, source, epoch),
            dst = relative(root, attractor, destination, epoch);
        double r1 = source == attractor ? source.properties()
            .parkingOrbitRadius() : src.radius();
        double r2 = destination == attractor ? destination.properties()
            .parkingOrbitRadius() : dst.radius();
        double hohmann = Math.PI * Math.sqrt(Math.pow((r1 + r2) / 2, 3) / attractor.mu());
        List<PoweredCandidate> candidates = new ArrayList<>();
        int samples = Double.isFinite(fixedTof) ? 1 : 64;
        for (int i = 0; i < samples; i++) {
            double tof = Double.isFinite(fixedTof) ? fixedTof : hohmann * (.1 + 2.9 * i / 63);
            for (boolean branch : new boolean[] { true, false }) {
                int sourceAlternatives = 1 << Math.max(0, depth(root, attractor, source) - 1);
                int destinationAlternatives = 1 << Math.max(0, depth(root, attractor, destination) - 1);
                for (int sourceMask = 0; sourceMask < sourceAlternatives; sourceMask++)
                    for (int destinationMask = 0; destinationMask < destinationAlternatives; destinationMask++) {
                        PoweredCandidate candidate = approximateBranch(
                            root,
                            attractor,
                            source,
                            destination,
                            epoch,
                            mass,
                            prefix,
                            angle,
                            destinationKind,
                            direction,
                            vehicle,
                            model,
                            tof,
                            branch,
                            sourceMask,
                            destinationMask);
                        if (candidate != null) candidates.add(candidate);
                    }
            }
        }
        LocalStatus status = candidates.stream()
            .anyMatch(c -> c.status() == LocalStatus.FEASIBLE)
                ? LocalStatus.FEASIBLE
                : !candidates.isEmpty() && candidates.stream()
                    .allMatch(
                        c -> c.status() == candidates.getFirst()
                            .status()) ? candidates.getFirst()
                                .status() : LocalStatus.NOT_FOUND;
        return new PoweredPlan(status, candidates);
    }

    private static PoweredCandidate approximateBranch(CelestialObject root, CelestialObject attractor,
        CelestialObject source, CelestialObject destination, double start, double initialMass,
        List<ManeuverObligation> prefix, double sourceAngle, EndpointKind destinationKind, int direction,
        LocalVehicle vehicle, CostModel model, double tof, boolean branch, int sourceMask, int destinationMask) {
        double effectiveEpoch = start;
        TransferCandidate seed = null;
        ManeuverObligation departure = null;
        double waitingSeconds = 0, arrivalAngle = sourceAngle;
        boolean converged = false, wellClearance = true;
        WellVelocity sourceWell = null;
        for (int iteration = 0; iteration < 12; iteration++) {
            OrbitalMechanics.OrbitalState origin = relative(root, attractor, source, effectiveEpoch);
            OrbitalMechanics.OrbitalState target = relative(root, attractor, destination, effectiveEpoch + tof);
            double requiredAngle = sourceAngle;
            if (source == attractor) {
                requiredAngle = Math.atan2(target.y(), target.x()) - direction * (Math.PI - .05);
                origin = parkingReference(source, requiredAngle, direction);
            } else if (seed != null) {
                sourceWell = wellVelocity(
                    root,
                    attractor,
                    source,
                    effectiveEpoch,
                    seed.route()
                        .departureVelocityX(),
                    seed.route()
                        .departureVelocityY(),
                    true,
                    sourceMask);
                double vx = sourceWell.vxMps(), vy = sourceWell.vyMps();
                double eccentricity = 1 + OrbitalMechanics.distanceToMetres(
                    source.properties()
                        .parkingOrbitRadius())
                    * (vx * vx + vy * vy)
                    / OrbitalMechanics.muToSI(source.mu());
                requiredAngle = Math.atan2(vy, vx) - direction * Math.acos(-1 / eccentricity);
            }
            double parking = OrbitalMechanics.distanceToMetres(
                source.properties()
                    .parkingOrbitRadius());
            double turn = normalize(direction * (requiredAngle - sourceAngle));
            waitingSeconds = turn * Math.sqrt(parking * parking * parking / OrbitalMechanics.muToSI(source.mu()));
            if (destination == attractor) {
                arrivalAngle = Math.atan2(origin.y(), origin.x()) + Math.PI;
                target = parkingReference(destination, arrivalAngle, direction);
            }
            var anchor = OrbitalMechanics.resolveWorldState(root, attractor, effectiveEpoch);
            seed = solveFixedCandidates(
                attractor.key(),
                attractor.mu(),
                attractor.properties()
                    .radiusEarthRelative(),
                anchor.x(),
                anchor.y(),
                origin.x(),
                origin.y(),
                origin.vx(),
                origin.vy(),
                target.x(),
                target.y(),
                target.vx(),
                target.vy(),
                tof).stream()
                    .filter(
                        c -> c.route()
                            .prograde() == branch)
                    .findFirst()
                    .orElse(null);
            if (seed == null) return null;
            if (source != attractor) sourceWell = wellVelocity(
                root,
                attractor,
                source,
                effectiveEpoch,
                seed.route()
                    .departureVelocityX(),
                seed.route()
                    .departureVelocityY(),
                true,
                sourceMask);
            double dv = source == attractor ? OrbitalMechanics.velocityToMetresPerSecond(
                seed.route()
                    .departureDv())
                : OrbitalMechanics
                    .parkingWellDeltaV(source.properties(), Math.hypot(sourceWell.vxMps(), sourceWell.vyMps()));
            double waitEnd = start + OrbitalMechanics.physicalSecondsToOsu(waitingSeconds);
            departure = OrbitalMechanics
                .approximateBurn(ManeuverKind.DEPARTURE, source, vehicle, initialMass, dv, waitEnd, model);
            double next = departure.endEpochOsu();
            if (Math.abs(next - effectiveEpoch) < 1e-8) {
                converged = true;
                break;
            }
            effectiveEpoch = next;
        }
        List<ManeuverObligation> obligations = new ArrayList<>(prefix);
        obligations.add(OrbitalMechanics.approximateCoast(ManeuverKind.PARKING_COAST, source, waitingSeconds, start));
        obligations.add(departure);
        if (sourceWell != null) {
            wellClearance &= sourceWell.clearance();
            for (int i = sourceWell.parents()
                .size() - 1; i >= 0; i--)
                obligations.add(
                    OrbitalMechanics.approximateCoast(
                        ManeuverKind.WELL_PASSAGE,
                        sourceWell.parents()
                            .get(i),
                        0,
                        departure.endEpochOsu()));
        }
        double mass = initialMass * Math.exp(-departure.deltaVMps() / vehicle.exhaustVelocityMps());
        ManeuverObligation coast = OrbitalMechanics.approximateCoast(
            ManeuverKind.COAST,
            attractor,
            OrbitalMechanics.osuToPhysicalSeconds(tof),
            departure.endEpochOsu());
        obligations.add(coast);
        WellVelocity destinationWell = destination == attractor ? null
            : wellVelocity(
                root,
                attractor,
                destination,
                coast.endEpochOsu(),
                seed.arrivalVelocityX(),
                seed.arrivalVelocityY(),
                false,
                destinationMask);
        double captureDv = destination == attractor ? OrbitalMechanics.velocityToMetresPerSecond(
            seed.route()
                .captureDv())
            : OrbitalMechanics.parkingWellDeltaV(
                destination.properties(),
                Math.hypot(destinationWell.vxMps(), destinationWell.vyMps()));
        if (destinationWell != null) {
            wellClearance &= destinationWell.clearance();
            for (CelestialObject parent : destinationWell.parents()) obligations
                .add(OrbitalMechanics.approximateCoast(ManeuverKind.WELL_PASSAGE, parent, 0, coast.endEpochOsu()));
            double vx = destinationWell.vxMps(), vy = destinationWell.vyMps();
            double r = OrbitalMechanics.distanceToMetres(
                destination.properties()
                    .parkingOrbitRadius());
            double eccentricity = 1 + r * (vx * vx + vy * vy) / OrbitalMechanics.muToSI(destination.mu());
            arrivalAngle = Math.atan2(vy, vx) + Math.PI + direction * Math.acos(-1 / eccentricity);
        }
        ManeuverObligation capture = OrbitalMechanics
            .approximateBurn(ManeuverKind.CAPTURE, destination, vehicle, mass, captureDv, coast.endEpochOsu(), model);
        obligations.add(capture);
        mass *= Math.exp(-capture.deltaVMps() / vehicle.exhaustVelocityMps());
        ManeuverKind finalKind = destinationManeuver(destinationKind);
        double finalCost = localManeuverCost(destination, finalKind, model);
        if (finalKind != null && finalCost > 0) obligations.add(
            OrbitalMechanics
                .approximateBurn(finalKind, destination, vehicle, mass, finalCost, capture.endEpochOsu(), model));
        return result(
            root,
            obligations,
            vehicle,
            normalize(arrivalAngle),
            seed,
            converged && wellClearance ? LocalStatus.FEASIBLE : LocalStatus.NOT_FOUND);
    }

    private static PoweredCandidate result(CelestialObject root, List<ManeuverObligation> obligations,
        LocalVehicle vehicle, double angle, TransferCandidate seed, LocalStatus status) {
        double dv = 0, fuel = 0, seconds = 0;
        for (ManeuverObligation obligation : obligations) {
            dv += obligation.deltaVMps();
            fuel += obligation.propellantKg();
            seconds += obligation.physicalSeconds();
            if (status == LocalStatus.FEASIBLE && obligation.status() != LocalStatus.FEASIBLE)
                status = obligation.status();
        }
        TrajectoryDistance distance = approximatePathDistance(root, obligations, seed);
        if (distance == null) {
            status = LocalStatus.UNAVAILABLE_DATA;
            distance = new TrajectoryDistance(0, Map.of());
        }
        return new PoweredCandidate(
            POWERED_MODEL_ID,
            seed,
            obligations,
            vehicle.wetMassKg(),
            vehicle.wetMassKg() * Math.exp(-dv / vehicle.exhaustVelocityMps()),
            angle,
            dv,
            fuel,
            seconds,
            distance.totalMeters(),
            distance.beltDistanceMeters(),
            status);
    }

    private record WellVelocity(double vxMps, double vyMps, List<CelestialObject> parents, boolean clearance) {}

    private static boolean wellDataAvailable(CelestialObject root, CelestialObject attractor,
        CelestialObject endpoint) {
        if (depth(root, attractor, endpoint) > 4) return false;
        while (endpoint != attractor) {
            if (endpoint.mu() <= 0 || endpoint.properties()
                .radiusEarthRelative() <= 0) return false;
            endpoint = GalaxiaCelestialAPI.findBodyByKey(root, endpoint.parentKey());
        }
        return true;
    }

    /** Transfers velocity through every physical parent well without introducing a parent visit or burn. */
    private static WellVelocity wellVelocity(CelestialObject root, CelestialObject attractor, CelestialObject endpoint,
        double epoch, double outerVx, double outerVy, boolean outgoing, int mask) {
        List<CelestialObject> path = new ArrayList<>();
        CelestialObject cursor = endpoint;
        while (cursor != attractor) {
            path.add(cursor);
            cursor = GalaxiaCelestialAPI.findBodyByKey(root, cursor.parentKey());
        }
        java.util.Collections.reverse(path);
        List<CelestialObject> parents = new ArrayList<>();
        double vx = OrbitalMechanics.velocityToMetresPerSecond(outerVx),
            vy = OrbitalMechanics.velocityToMetresPerSecond(outerVy);
        CelestialObject parent = attractor;
        boolean clearance = true;
        for (int i = 0; i < path.size(); i++) {
            CelestialObject body = path.get(i);
            var orbit = relative(root, parent, body, epoch);
            vx -= OrbitalMechanics.velocityToMetresPerSecond(orbit.vx());
            vy -= OrbitalMechanics.velocityToMetresPerSecond(orbit.vy());
            if (i == path.size() - 1) break;
            var encounter = relative(root, body, path.get(i + 1), epoch);
            var passage = OrbitalMechanics.hyperbolicVelocityAtPosition(
                OrbitalMechanics.muToSI(body.mu()),
                OrbitalMechanics.distanceToMetres(encounter.x()),
                OrbitalMechanics.distanceToMetres(encounter.y()),
                vx,
                vy,
                outgoing,
                (mask & (1 << i)) == 0 ? 1 : -1);
            boolean passesPeriapsis = outgoing ? passage.radialMps() < 0 : passage.radialMps() > 0;
            if (passesPeriapsis && passage.periapsisRadiusM() < body.properties()
                .radiusMetres()) clearance = false;
            vx = passage.vxMps();
            vy = passage.vyMps();
            parents.add(body);
            parent = body;
        }
        return new WellVelocity(vx, vy, List.copyOf(parents), clearance);
    }

    /** Measured model-frame path. Belt portions are disjoint even if authored bands overlap. */
    public record TrajectoryDistance(double totalMeters, Map<CelestialObjectKey, Double> beltDistanceMeters) {

        public TrajectoryDistance {
            if (!finiteNonNegative(totalMeters)) throw new IllegalArgumentException("Invalid path distance");
            beltDistanceMeters = checkedBeltDistances(beltDistanceMeters, totalMeters);
        }
    }

    private static Map<CelestialObjectKey, Double> checkedBeltDistances(Map<CelestialObjectKey, Double> distances,
        double total) {
        Map<CelestialObjectKey, Double> copy = new TreeMap<>();
        double sum = 0;
        for (var entry : distances.entrySet()) {
            if (entry.getKey() == null || !finitePositive(entry.getValue()))
                throw new IllegalArgumentException("Invalid belt distance");
            copy.put(entry.getKey(), entry.getValue());
            sum += entry.getValue();
        }
        if (!Double.isFinite(sum) || sum > total + 1e-7 * Math.max(1, total))
            throw new IllegalArgumentException("Belt distances exceed total travel");
        return Collections.unmodifiableMap(copy);
    }

    /** Distance in common-attractor and co-moving local frames. Parent passages are already in the coast. */
    @Nullable
    private static TrajectoryDistance approximatePathDistance(CelestialObject root,
        List<ManeuverObligation> obligations, TransferCandidate seed) {
        double departure = obligations.stream()
            .filter(o -> o.kind() == ManeuverKind.COAST)
            .mapToDouble(ManeuverObligation::startEpochOsu)
            .findFirst()
            .orElse(0);
        TrajectoryDistance arc = seed == null ? new TrajectoryDistance(0, Map.of())
            : measureTransferDistance(root, seed, departure);
        if (arc == null) return null;
        double distance = arc.totalMeters();
        Map<CelestialObjectKey, Double> regions = new TreeMap<>(arc.beltDistanceMeters());
        List<BeltBand> bands = beltBands();
        for (ManeuverObligation obligation : obligations) {
            if (obligation.kind() == ManeuverKind.COAST || obligation.kind() == ManeuverKind.WELL_PASSAGE) continue;
            CelestialObject body = GalaxiaCelestialAPI.findBodyByKey(root, obligation.bodyKey());
            if (body == null) return null;
            double r = OrbitalMechanics.distanceToMetres(
                body.properties()
                    .parkingOrbitRadius());
            double circular = Math.sqrt(OrbitalMechanics.muToSI(body.mu()) / r);
            double local = switch (obligation.kind()) {
                case ASCENT, LANDING -> r - body.properties()
                    .radiusMetres();
                case PARKING_COAST -> circular * obligation.physicalSeconds();
                case DEPARTURE, CAPTURE, UNDOCKING, DOCKING -> (circular + obligation.deltaVMps() / 2)
                    * obligation.burnPhysicalSeconds();
                default -> 0;
            };
            distance += local;
            // Local distances use the endpoint's co-moving position in the authored annulus.
            for (BeltBand band : bands) {
                var position = relative(root, band.parent(), body, obligation.startEpochOsu());
                if (position.radius() >= band.inner() && position.radius() <= band.outer()) {
                    if (local > 0) regions.merge(band.key(), local, Double::sum);
                    break;
                }
            }
        }
        return new TrajectoryDistance(distance, regions);
    }

    /** Accumulates internal adaptive trajectory segments, not the endpoint chord or sparse output chords. */
    public static double transferArcLengthMeters(TransferCandidate seed) {
        TrajectoryDistance result = measureTransferDistance(GalaxiaCelestialAPI.getPrimaryRoot(), seed, 0);
        return result == null ? Double.NaN : result.totalMeters();
    }

    /** Uses existing authored asteroid-field annuli. A missing band width is never guessed from a sprite. */
    @Nullable
    public static TrajectoryDistance measureTransferDistance(CelestialObject root, TransferCandidate seed,
        double departureEpoch) {
        TransferRoute route = seed.route();
        CelestialObject attractor = GalaxiaCelestialAPI.findBodyByKey(root, route.attractorBodyKey());
        if (attractor == null) return null;
        double[] x = new double[256], y = new double[256];
        ArcDistance measure = new ArcDistance(root, attractor, departureEpoch, beltBands());
        int count = sampleTransferArcInto(
            0,
            0,
            route.r1x(),
            route.r1y(),
            route.departureVelocityX(),
            route.departureVelocityY(),
            route.tofOsu(),
            attractor.mu(),
            x,
            y,
            x.length,
            measure);
        return count == x.length
            ? new TrajectoryDistance(OrbitalMechanics.distanceToMetres(measure.total), measure.meters)
            : null;
    }

    private record BeltBand(CelestialObjectKey key, CelestialObject parent, double inner, double outer) {}

    private static List<BeltBand> beltBands() {
        List<BeltBand> bands = new ArrayList<>();
        for (CelestialObject body : GalaxiaCelestialAPI.getAllBodies()
            .values()) {
            var profile = body.properties()
                .asteroidFieldProfile();
            if (!body.isAsteroidBelt() || profile == null) continue;
            var parent = GalaxiaCelestialAPI.getAllBodies()
                .get(body.parentKey());
            if (parent != null)
                bands.add(new BeltBand(body.key(), parent, profile.innerOrbitalRadius(), profile.outerOrbitalRadius()));
        }
        bands.sort(Comparator.comparing(BeltBand::key));
        return bands;
    }

    /** One integration segment is partitioned at every annulus boundary before assigning its travel once. */
    private static final class ArcDistance {

        private final CelestialObject root, attractor;
        private final double departure;
        private final List<BeltBand> bands;
        private final Map<CelestialObjectKey, Double> meters = new TreeMap<>();
        private double total;

        private ArcDistance(CelestialObject root, CelestialObject attractor, double departure, List<BeltBand> bands) {
            this.root = root;
            this.attractor = attractor;
            this.departure = departure;
            this.bands = bands;
        }

        private void segment(OrbitalState a, OrbitalState b, double from, double to) {
            double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
            total += length;
            if (bands.isEmpty()) return;
            List<Double> cuts = new ArrayList<>();
            cuts.add(0.0);
            cuts.add(1.0);
            double[][] positions = new double[bands.size()][4];
            for (int i = 0; i < bands.size(); i++) {
                BeltBand band = bands.get(i);
                var c0 = band.parent() == attractor ? new OrbitalState(0, 0, 0, 0)
                    : relative(root, attractor, band.parent(), departure + from);
                var c1 = band.parent() == attractor ? c0 : relative(root, attractor, band.parent(), departure + to);
                var p = positions[i];
                p[0] = a.x() - c0.x();
                p[1] = a.y() - c0.y();
                p[2] = b.x() - c1.x() - p[0];
                p[3] = b.y() - c1.y() - p[1];
                circleCuts(p, band.inner(), cuts);
                circleCuts(p, band.outer(), cuts);
            }
            cuts.sort(Double::compare);
            for (int i = 1; i < cuts.size(); i++) {
                double low = cuts.get(i - 1), high = cuts.get(i), mid = (low + high) / 2;
                if (high <= low) continue;
                for (int j = 0; j < bands.size(); j++) {
                    var p = positions[j];
                    double r = Math.hypot(p[0] + mid * p[2], p[1] + mid * p[3]);
                    BeltBand band = bands.get(j);
                    if (r >= band.inner() && r <= band.outer()) {
                        meters.merge(band.key(), OrbitalMechanics.distanceToMetres(length * (high - low)), Double::sum);
                        break;
                    }
                }
            }
        }

        private static void circleCuts(double[] p, double radius, List<Double> cuts) {
            double a = p[2] * p[2] + p[3] * p[3];
            if (a == 0) return;
            double b = p[0] * p[2] + p[1] * p[3], c = p[0] * p[0] + p[1] * p[1] - radius * radius;
            double discriminant = b * b - a * c;
            if (discriminant <= 0) return;
            double square = Math.sqrt(discriminant);
            double first = (-b - square) / a, second = (-b + square) / a;
            if (first > 0 && first < 1) cuts.add(first);
            if (second > 0 && second < 1) cuts.add(second);
        }
    }

    private static OrbitalMechanics.OrbitalState parkingReference(CelestialObject body, double angle, int direction) {
        double r = body.properties()
            .parkingOrbitRadius(), v = direction * Math.sqrt(body.mu() / r);
        return new OrbitalMechanics.OrbitalState(
            r * Math.cos(angle),
            r * Math.sin(angle),
            -v * Math.sin(angle),
            v * Math.cos(angle));
    }

    private static OrbitalMechanics.OrbitalState relative(CelestialObject root, CelestialObject attractor,
        CelestialObject body, double epoch) {
        var b = OrbitalMechanics.resolveWorldState(root, body, epoch);
        var a = OrbitalMechanics.resolveWorldState(root, attractor, epoch);
        return new OrbitalMechanics.OrbitalState(b.x() - a.x(), b.y() - a.y(), b.vx() - a.vx(), b.vy() - a.vy());
    }

    private static double normalize(double angle) {
        return (angle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-7 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }

    private static int depth(CelestialObject root, CelestialObject ancestor, CelestialObject body) {
        int depth = 0;
        while (body != ancestor) {
            if (body.parentKey() == null) return Integer.MAX_VALUE;
            body = GalaxiaCelestialAPI.findBodyByKey(root, body.parentKey());
            if (body == null) return Integer.MAX_VALUE;
            depth++;
        }
        return depth;
    }

    private static CelestialObject commonAttractor(CelestialObject root, CelestialObject a, CelestialObject b) {
        var ancestry = new LinkedHashSet<CelestialObjectKey>();
        while (a != null) {
            ancestry.add(a.key());
            a = a.parentKey() == null ? null : GalaxiaCelestialAPI.findBodyByKey(root, a.parentKey());
        }
        while (b != null) {
            if (ancestry.contains(b.key())) return b;
            b = b.parentKey() == null ? null : GalaxiaCelestialAPI.findBodyByKey(root, b.parentKey());
        }
        return null;
    }

    private static NBTTagCompound encodeGeometry(TransferCandidate seed) {
        NBTTagCompound out = new NBTTagCompound();
        TransferRoute r = seed.route();
        out.setTag("attractor", CelestialObjectKeyState.encode(r.attractorBodyKey()));
        out.setBoolean("prograde", r.prograde());
        double[] values = { r.tofOsu(), r.totalDv(), r.departureDv(), r.captureDv(), r.anchorX(), r.anchorY(), r.r1x(),
            r.r1y(), r.departureVelocityX(), r.departureVelocityY(), seed.r2x(), seed.r2y(), seed.arrivalVelocityX(),
            seed.arrivalVelocityY(), seed.sourceVelocityX(), seed.sourceVelocityY(), seed.destinationVelocityX(),
            seed.destinationVelocityY() };
        for (int i = 0; i < values.length; i++) out.setDouble("v" + i, values[i]);
        return out;
    }

    private static TransferCandidate decodeGeometry(NbtReader in) {
        double[] v = new double[18];
        for (int i = 0; i < v.length; i++) {
            v[i] = in.doubleValue("v" + i);
            if (!Double.isFinite(v[i])) throw new IllegalArgumentException("Non-finite transfer geometry");
        }
        if (v[0] <= 0 || v[1] < 0 || v[2] < 0 || v[3] < 0)
            throw new IllegalArgumentException("Invalid transfer geometry costs");
        return new TransferCandidate(
            new TransferRoute(
                v[0],
                v[1],
                v[2],
                v[3],
                CelestialObjectKeyState.decode(in.compound("attractor")),
                v[4],
                v[5],
                v[6],
                v[7],
                v[8],
                v[9],
                in.bool("prograde")),
            v[10],
            v[11],
            v[12],
            v[13],
            v[14],
            v[15],
            v[16],
            v[17]);
    }

    // -------------------------------------------------------------------------
    // Route computation
    // -------------------------------------------------------------------------

    /**
     * Selects from the bounded candidate scan using the requested time-of-flight or delta-v priority.
     *
     * <p>
     * Uses {@code attractor} as the central body for Lambert (typically the host star
     * for SYSTEM-scope, or the planetary anchor for PLANETARY-scope transfers).
     *
     * @return the preferred {@link TransferRoute}, or {@code null} if no valid route exists
     */

    public static TransferRoute computeRoute(CelestialObject root, CelestialObject attractor, CelestialObject source,
        CelestialObject dest, double departureTime, RoutePriority priority) {
        RoutePriority effectivePriority = priority != null ? priority : RoutePriority.PRIORITIZE_TOF;
        TransferRoute bestRoute = null;
        double previousTof = Double.NaN;
        for (TransferCandidate candidate : computeCandidates(root, attractor, source, dest, departureTime)) {
            TransferRoute route = candidate.route();
            // Fixed-time branches are ordered by the existing delta-v/tie preference. Current callers choose that
            // branch before comparing time slots. Vehicle validation uses the complete candidate set instead.
            if (route.tofOsu() == previousTof) continue;
            previousTof = route.tofOsu();
            boolean acceptCandidate;
            if (bestRoute == null) {
                acceptCandidate = true;
            } else if (effectivePriority == RoutePriority.PRIORITIZE_DV) {
                acceptCandidate = route.totalDv() < bestRoute.totalDv()
                    || (Math.abs(route.totalDv() - bestRoute.totalDv()) < 1e-9 && route.tofOsu() < bestRoute.tofOsu());
            } else {
                acceptCandidate = route.tofOsu() < bestRoute.tofOsu()
                    || (Math.abs(route.tofOsu() - bestRoute.tofOsu()) < 1e-9 && route.totalDv() < bestRoute.totalDv());
            }
            if (acceptCandidate) bestRoute = route;
        }
        return bestRoute;
    }

    /**
     * Returns every valid branch from the existing bounded 64-point time scan, before vehicle constraints or
     * cross-time ranking. Results follow ascending flight time, with the preferred branch first at each time.
     * This finite scan does not claim to enumerate all possible flight times or multi-revolution trajectories.
     */
    public static List<TransferCandidate> computeCandidates(CelestialObject root, CelestialObject attractor,
        CelestialObject source, CelestialObject dest, double departureTime) {
        if (root == null || attractor == null || source == null || dest == null || source == dest) return List.of();

        double mu = attractor.mu();
        if (mu <= 0.0 || attractor.properties()
            .radiusEarthRelative() <= 0) return List.of();

        double hohmannTof = attractor.getHohmannTof(source, dest, root, departureTime);
        if (hohmannTof <= 0.0) return List.of();

        double minPeriapsis = attractor.properties()
            .radiusEarthRelative();
        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, source, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        if (srcStateDep == null || attractorAtDep == null) return List.of();

        double r1x0 = srcStateDep.x() - attractorAtDep.x();
        double r1y0 = srcStateDep.y() - attractorAtDep.y();
        double vsrcX0 = srcStateDep.vx() - attractorAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - attractorAtDep.vy();

        List<TransferCandidate> routes = new ArrayList<>();
        int nScan = 64;
        for (int i = 0; i < nScan; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (nScan - 1);
            double tof = hohmannTof * frac;
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, dest, departureTime + tof);
            OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
                .resolveWorldState(root, attractor, departureTime + tof);
            if (dstState == null || attractorAtArr == null) continue;

            List<TransferCandidate> candidates = solveFixedCandidates(
                attractor.key(),
                mu,
                minPeriapsis,
                attractorAtDep.x(),
                attractorAtDep.y(),
                r1x0,
                r1y0,
                vsrcX0,
                vsrcY0,
                dstState.x() - attractorAtArr.x(),
                dstState.y() - attractorAtArr.y(),
                dstState.vx() - attractorAtArr.vx(),
                dstState.vy() - attractorAtArr.vy(),
                tof);
            routes.addAll(candidates);
        }

        return List.copyOf(routes);
    }

    public static TransferRoute computeFixedRoute(CelestialObject root, CelestialObject attractor,
        CelestialObject source, CelestialObject dest, double departureTime, double tof) {
        List<TransferCandidate> candidates = computeFixedCandidates(root, attractor, source, dest, departureTime, tof);
        return candidates.isEmpty() ? null
            : candidates.getFirst()
                .route();
    }

    /** Returns both valid branches for one flight time in raw orbital units, preferred branch first. */
    public static List<TransferCandidate> computeFixedCandidates(CelestialObject root, CelestialObject attractor,
        CelestialObject source, CelestialObject dest, double departureTime, double tof) {
        if (root == null || attractor == null || source == null || dest == null || source == dest || tof <= 0.0)
            return List.of();

        double mu = attractor.mu();
        if (mu <= 0.0 || attractor.properties()
            .radiusEarthRelative() <= 0) return List.of();

        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, source, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        OrbitalMechanics.OrbitalState dstState = OrbitalMechanics.resolveWorldState(root, dest, departureTime + tof);
        OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime + tof);
        if (srcStateDep == null || attractorAtDep == null || dstState == null || attractorAtArr == null)
            return List.of();

        return solveFixedCandidates(
            attractor.key(),
            mu,
            attractor.properties()
                .radiusEarthRelative(),
            attractorAtDep.x(),
            attractorAtDep.y(),
            srcStateDep.x() - attractorAtDep.x(),
            srcStateDep.y() - attractorAtDep.y(),
            srcStateDep.vx() - attractorAtDep.vx(),
            srcStateDep.vy() - attractorAtDep.vy(),
            dstState.x() - attractorAtArr.x(),
            dstState.y() - attractorAtArr.y(),
            dstState.vx() - attractorAtArr.vx(),
            dstState.vy() - attractorAtArr.vy(),
            tof);
    }

    /**
     * Pure numerical geometry for a circular phase family. Representative belt radii retain the belt's angular speed.
     */
    public static List<TransferCandidate> computeCircularCandidates(CelestialObject attractor, double sourceRadius,
        double sourceAngularSpeed, double destinationRadius, double destinationAngularSpeed, double relativePhase) {
        if (attractor.mu() <= 0 || attractor.properties()
            .radiusEarthRelative() <= 0
            || !finitePositive(sourceRadius)
            || !finitePositive(destinationRadius)
            || !Double.isFinite(sourceAngularSpeed)
            || !Double.isFinite(destinationAngularSpeed)
            || !Double.isFinite(relativePhase)) return List.of();
        double a = (sourceRadius + destinationRadius) / 2;
        double hohmann = Math.PI * Math.sqrt(a * a * a / attractor.mu());
        List<TransferCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            double tof = hohmann * (.1 + 2.9 * i / 63);
            double arrival = relativePhase + destinationAngularSpeed * tof;
            double x = destinationRadius * Math.cos(arrival), y = destinationRadius * Math.sin(arrival);
            candidates.addAll(
                solveFixedCandidates(
                    attractor.key(),
                    attractor.mu(),
                    attractor.properties()
                        .radiusEarthRelative(),
                    0,
                    0,
                    sourceRadius,
                    0,
                    0,
                    sourceAngularSpeed * sourceRadius,
                    x,
                    y,
                    -destinationAngularSpeed * y,
                    destinationAngularSpeed * x,
                    tof));
        }
        return List.copyOf(candidates);
    }

    private static List<TransferCandidate> solveFixedCandidates(CelestialObjectKey attractorBodyKey, double mu,
        double minPeriapsis, double anchorX, double anchorY, double r1x, double r1y, double vsrcX, double vsrcY,
        double r2x, double r2y, double vdstX, double vdstY, double tof) {
        if (mu <= 0.0 || tof <= 0.0 || isDegenerateTransferGeometry(r1x, r1y, r2x, r2y)) return List.of();

        boolean preferPrograde = preferredProgradeForTransfer(r1x, r1y, vsrcX, vsrcY, r2x, r2y);
        LambertTransfer.Solution prograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(true)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution retrograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(false)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution best = chooseLowerDvSolution(prograde, retrograde, preferPrograde);
        if (best == null || !best.valid()) return List.of();

        List<TransferCandidate> candidates = new ArrayList<>(2);
        candidates.add(
            candidate(
                best,
                best == prograde,
                attractorBodyKey,
                anchorX,
                anchorY,
                r1x,
                r1y,
                vsrcX,
                vsrcY,
                r2x,
                r2y,
                vdstX,
                vdstY,
                tof));
        LambertTransfer.Solution other = best == prograde ? retrograde : prograde;
        if (other != null && other.valid()) {
            candidates.add(
                candidate(
                    other,
                    other == prograde,
                    attractorBodyKey,
                    anchorX,
                    anchorY,
                    r1x,
                    r1y,
                    vsrcX,
                    vsrcY,
                    r2x,
                    r2y,
                    vdstX,
                    vdstY,
                    tof));
        }
        return List.copyOf(candidates);
    }

    private static TransferCandidate candidate(LambertTransfer.Solution solution, boolean prograde,
        CelestialObjectKey attractorBodyKey, double anchorX, double anchorY, double r1x, double r1y, double vsrcX,
        double vsrcY, double r2x, double r2y, double vdstX, double vdstY, double tof) {
        var route = new TransferRoute(
            tof,
            solution.totalDv(),
            solution.depDv(),
            Math.max(0.0, solution.totalDv() - solution.depDv()),
            attractorBodyKey,
            anchorX,
            anchorY,
            r1x,
            r1y,
            solution.dvx1(),
            solution.dvy1(),
            prograde);
        return new TransferCandidate(route, r2x, r2y, solution.dvx2(), solution.dvy2(), vsrcX, vsrcY, vdstX, vdstY);
    }

    public static boolean isDegenerateTransferGeometry(double r1x, double r1y, double r2x, double r2y) {
        double r1mag = Math.hypot(r1x, r1y);
        double r2mag = Math.hypot(r2x, r2y);
        if (r1mag <= 1e-10 || r2mag <= 1e-10) return true;
        double scale = Math.max(1e-20, r1mag * r2mag);
        double crossZ = r1x * r2y - r1y * r2x;
        double dot = r1x * r2x + r1y * r2y;
        double sinDth = Math.abs(crossZ) / scale;
        double cosDth = dot / scale;
        return sinDth < MIN_TRANSFER_ANGLE_SIN && cosDth > 0.0;
    }

    public static boolean preferredProgradeForTransfer(double r1x, double r1y, double vsrcX, double vsrcY, double r2x,
        double r2y) {
        double r1mag = Math.hypot(r1x, r1y);
        double r2mag = Math.hypot(r2x, r2y);
        double transferCross = r1x * r2y - r1y * r2x;
        double sinDth = Math.abs(transferCross) / Math.max(1e-20, r1mag * r2mag);
        if (sinDth < MIN_TRANSFER_ANGLE_SIN) {
            double orbitCross = r1x * vsrcY - r1y * vsrcX;
            double velocityScale = Math.max(1.0, r1mag * Math.hypot(vsrcX, vsrcY));
            if (Math.abs(orbitCross) > 1e-12 * velocityScale) return orbitCross >= 0.0;
        }
        return transferCross >= 0.0;
    }

    public static LambertTransfer.Solution chooseLowerDvSolution(LambertTransfer.Solution prograde,
        LambertTransfer.Solution retrograde, boolean preferPrograde) {
        boolean progradeValid = prograde != null && prograde.valid();
        boolean retrogradeValid = retrograde != null && retrograde.valid();
        if (progradeValid && !retrogradeValid) return prograde;
        if (!progradeValid && retrogradeValid) return retrograde;
        if (!progradeValid) return null;
        double dvDelta = prograde.totalDv() - retrograde.totalDv();
        if (Math.abs(dvDelta) <= BRANCH_TIE_DV_EPS) return preferPrograde ? prograde : retrograde;
        return dvDelta < 0.0 ? prograde : retrograde;
    }

    /** Samples equal-time points, returning zero if the shared integration budget cannot complete the trajectory. */
    public static int sampleTransferArcInto(double ax, double ay, double rx1, double ry1, double vx1, double vy1,
        double tof, double mu, double[] outXs, double[] outYs, int n) {
        return sampleTransferArcInto(ax, ay, rx1, ry1, vx1, vy1, tof, mu, outXs, outYs, n, null);
    }

    private static int sampleTransferArcInto(double ax, double ay, double rx1, double ry1, double vx1, double vy1,
        double tof, double mu, double[] outXs, double[] outYs, int n, ArcDistance distance) {
        if (outXs == null || outYs == null) return 0;
        int sampleCount = Math.max(2, Math.min(n, Math.min(outXs.length, outYs.length)));
        if (sampleCount <= 0) return 0;
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(rx1, ry1, vx1, vy1);
        double segmentDt = tof / (sampleCount - 1);
        outXs[0] = ax + state.x();
        outYs[0] = ay + state.y();
        int remainingSteps = sampleCount * TRAJECTORY_INTEGRATION_STEPS_PER_SAMPLE;
        double elapsed = 0;
        for (int i = 1; i < sampleCount; i++) {
            double remainingTime = segmentDt;
            while (remainingTime != 0.0) {
                // Re-evaluate the local orbit scale as the trajectory approaches or leaves the attractor.
                if (remainingSteps-- <= 0) return 0;
                int substeps = trajectoryIntegrationSubsteps(state, mu, Math.abs(remainingTime));
                double integrationDt = remainingTime / substeps;
                OrbitalMechanics.OrbitalState previous = state;
                state = OrbitalMechanics.propagateTwoBodyState(state, mu, integrationDt);
                if (state == null) return i;
                if (distance != null) distance.segment(previous, state, elapsed, elapsed + integrationDt);
                elapsed += integrationDt;
                remainingTime -= integrationDt;
            }
            outXs[i] = ax + state.x();
            outYs[i] = ay + state.y();
        }
        return sampleCount;
    }

    private static int trajectoryIntegrationSubsteps(OrbitalMechanics.OrbitalState state, double mu, double segmentDt) {
        if (state == null || mu <= 0.0 || segmentDt <= 0.0) return 1;
        double radius = Math.hypot(state.x(), state.y());
        if (radius <= 1e-9) return Integer.MAX_VALUE;
        double localTimeScale = Math.sqrt(radius * radius * radius / mu);
        if (!Double.isFinite(localTimeScale) || localTimeScale <= 1e-9) {
            return Integer.MAX_VALUE;
        }
        int substeps = (int) Math.ceil(segmentDt / (localTimeScale * TRAJECTORY_INTEGRATION_TIME_SCALE_FRACTION));
        return Math.max(1, substeps);
    }
}
