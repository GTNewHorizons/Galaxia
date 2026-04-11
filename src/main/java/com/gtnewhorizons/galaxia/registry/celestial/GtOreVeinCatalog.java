package com.gtnewhorizons.galaxia.registry.celestial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.gtnewhorizons.galaxia.compat.GTUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;

public final class GtOreVeinCatalog {

    private static Map<String, GtOreVeinDefinition> veinsById = Collections.emptyMap();
    private static boolean loaded;

    private GtOreVeinCatalog() {}

    public static synchronized Optional<GtOreVeinDefinition> get(String veinId) {
        ensureLoaded();
        return Optional.ofNullable(veinsById.get(veinId));
    }

    public static synchronized List<GtOreVeinDefinition> resolveAll(List<String> veinIds) {
        if (veinIds == null || veinIds.isEmpty()) return Collections.emptyList();
        ensureLoaded();
        if (veinsById.isEmpty()) return Collections.emptyList();
        List<GtOreVeinDefinition> resolved = new ArrayList<>();
        for (String veinId : veinIds) {
            GtOreVeinDefinition vein = veinsById.get(veinId);
            if (vein != null) resolved.add(vein);
        }
        return Collections.unmodifiableList(resolved);
    }

    public static synchronized void reload() {
        loaded = false;
        veinsById = Collections.emptyMap();
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!GTUtility.isGTLoaded) {
            veinsById = Collections.emptyMap();
            return;
        }
        try {
            veinsById = loadFromGt5uOreMixes();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Galaxia.LOG.warn("Failed to load GT5U ore vein catalog, falling back to vanilla ores", e);
            veinsById = Collections.emptyMap();
        }
    }

    private static Map<String, GtOreVeinDefinition> loadFromGt5uOreMixes() throws ReflectiveOperationException {
        Class<?> oreMixesClass = Class.forName("gregtech.api.enums.OreMixes");
        Object[] oreMixes = oreMixesClass.getEnumConstants();
        if (oreMixes == null || oreMixes.length == 0) return Collections.emptyMap();

        Field oreMixBuilderField = oreMixesClass.getField("oreMixBuilder");
        Class<?> oreMixBuilderClass = Class.forName("gregtech.common.OreMixBuilder");
        Map<String, GtOreVeinDefinition> loadedVeins = new LinkedHashMap<>();

        for (Object oreMix : oreMixes) {
            Object oreMixBuilder = oreMixBuilderField.get(oreMix);
            GtOreVeinDefinition vein = readVeinDefinition(oreMixBuilderClass, oreMixBuilder);
            if (vein != null && !loadedVeins.containsKey(vein.id())) {
                loadedVeins.put(vein.id(), vein);
            }
        }

        Galaxia.LOG.info("Loaded {} GT5U ore vein definitions", loadedVeins.size());
        return Collections.unmodifiableMap(loadedVeins);
    }

    private static GtOreVeinDefinition readVeinDefinition(Class<?> oreMixBuilderClass, Object oreMixBuilder)
        throws ReflectiveOperationException {
        if (oreMixBuilder == null) return null;

        String id = readStringField(oreMixBuilderClass, oreMixBuilder, "oreMixName");
        if (id == null || id.isEmpty()) return null;

        String displayName = readStringField(oreMixBuilderClass, oreMixBuilder, "localizedName");
        if (displayName == null || displayName.isEmpty()) displayName = formatDisplayName(id);

        return new GtOreVeinDefinition(
            id,
            displayName,
            readOreMaterialInternalName(oreMixBuilderClass, oreMixBuilder, "primary"),
            readOreMaterialInternalName(oreMixBuilderClass, oreMixBuilder, "secondary"),
            readOreMaterialInternalName(oreMixBuilderClass, oreMixBuilder, "between"),
            readOreMaterialInternalName(oreMixBuilderClass, oreMixBuilder, "sporadic"),
            readIntField(oreMixBuilderClass, oreMixBuilder, "minY"),
            readIntField(oreMixBuilderClass, oreMixBuilder, "maxY"),
            readIntField(oreMixBuilderClass, oreMixBuilder, "weight"),
            readIntField(oreMixBuilderClass, oreMixBuilder, "density"),
            readIntField(oreMixBuilderClass, oreMixBuilder, "size"));
    }

    private static int readIntField(Class<?> ownerClass, Object instance, String fieldName)
        throws ReflectiveOperationException {
        Field field = ownerClass.getField(fieldName);
        return ((Number) field.get(instance)).intValue();
    }

    private static String readStringField(Class<?> ownerClass, Object instance, String fieldName)
        throws ReflectiveOperationException {
        Object value = ownerClass.getField(fieldName)
            .get(instance);
        return value == null ? "" : value.toString();
    }

    private static String readOreMaterialInternalName(Class<?> ownerClass, Object instance, String fieldName)
        throws ReflectiveOperationException {
        Object material = ownerClass.getField(fieldName)
            .get(instance);
        if (material == null) return "";
        String internalName = callStringMethod(material, "getInternalName");
        if (internalName != null && !internalName.isEmpty()) return internalName;
        String localizedName = callStringMethod(material, "getLocalizedName");
        if (localizedName != null && !localizedName.isEmpty()) return localizedName;
        return String.valueOf(material);
    }

    private static String callStringMethod(Object instance, String methodName) throws ReflectiveOperationException {
        Method method = instance.getClass()
            .getMethod(methodName);
        Object value = method.invoke(instance);
        return value == null ? "" : value.toString();
    }

    private static String formatDisplayName(String veinId) {
        String raw = veinId;
        if (raw.startsWith("ore.mix.")) raw = raw.substring("ore.mix.".length());
        int lastDot = raw.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < raw.length()) raw = raw.substring(lastDot + 1);
        raw = raw.replace('_', ' ')
            .replace('-', ' ')
            .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
            .trim();
        if (raw.isEmpty()) return veinId;
        String[] parts = raw.split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (displayName.length() > 0) displayName.append(' ');
            displayName.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) displayName.append(
                part.substring(1)
                    .toLowerCase(Locale.ROOT));
        }
        if (!displayName.toString()
            .toLowerCase(Locale.ROOT)
            .contains("vein")) {
            displayName.append(" Vein");
        }
        return displayName.toString();
    }
}
