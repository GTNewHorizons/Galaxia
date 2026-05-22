package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/**
 * Verifies that {@link CelestialAssetStore#SERVER} and {@link CelestialAssetStore#CLIENT}
 * are isolated instances, that static convenience methods delegate to SERVER,
 * and that instance methods work correctly.
 */
final class CelestialAssetStoreRefactorTest {

    private static final UUID TEAM_A = UUID.randomUUID();
    private static final UUID TEAM_B = UUID.randomUUID();
    private static final CelestialObjectId BODY_1 = CelestialObjectId.MARS;
    private static final CelestialObjectId BODY_2 = CelestialObjectId.MOON;

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    // ── Instance isolation ──

    @Test
    void serverAndClientAreSeparateInstances() {
        CelestialAssetStore server = CelestialAssetStore.SERVER;
        CelestialAssetStore client = CelestialAssetStore.CLIENT;

        assertNotNull(server);
        assertNotNull(client);
        assertSame(server, CelestialAssetStore.SERVER);
        assertSame(client, CelestialAssetStore.CLIENT);

        // Add to SERVER, CLIENT should be empty
        CelestialAsset asset = createAsset(BODY_1);
        server.registerAssetInternal(TEAM_A, asset);

        assertNotNull(server.findAssetInternal(asset.assetId), "SERVER should have the asset");
        assertNull(client.findAssetInternal(asset.assetId), "CLIENT should NOT have the asset");

        // Add to CLIENT, SERVER should have both
        CelestialAsset clientAsset = createAsset(BODY_2);
        client.registerAssetInternal(TEAM_A, clientAsset);

        assertEquals(
            1,
            server.allAssetsInternal()
                .size(),
            "SERVER should have only the first asset");
        assertEquals(
            1,
            client.allAssetsInternal()
                .size(),
            "CLIENT should have only the second asset");

        // Cleanup
        server.clearInternal();
        client.clearInternal();
        CelestialAssetStore.clear();
    }

    @Test
    void staticMethodsDelegateToServer() {
        // Ensure pristine SERVER
        CelestialAssetStore.clear();
        // Static methods should operate on SERVER
        CelestialAsset asset = createAsset(BODY_1);
        CelestialAssetStore.registerAsset(TEAM_A, asset);

        assertNotNull(CelestialAssetStore.findAsset(asset.assetId), "static findAsset should find asset on SERVER");
        assertNull(
            CelestialAssetStore.CLIENT.findAssetInternal(asset.assetId),
            "CLIENT should be untouched by static methods");
        assertEquals(TEAM_A, CelestialAssetStore.getTeamId(asset.assetId));
        assertTrue(CelestialAssetStore.isOwnedBy(TEAM_A, asset.assetId));
        assertFalse(CelestialAssetStore.isOwnedBy(TEAM_B, asset.assetId));

        CelestialAssetStore.clear();
        assertNull(CelestialAssetStore.findAsset(asset.assetId), "should be cleared from SERVER");
        assertEquals(
            0,
            CelestialAssetStore.allAssets()
                .size());
        // Ensure staticAssets test left nothing
        CelestialAssetStore.CLIENT.clearInternal();
    }

    // ── Instance CRUD ──

    @Test
    void addAndFindInstance() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = createAsset(BODY_1);

        store.registerAssetInternal(TEAM_A, asset);
        assertSame(asset, store.findAssetInternal(asset.assetId));
        assertEquals(TEAM_A, store.getTeamIdInternal(asset.assetId));
    }

    @Test
    void getStateByTeamAndBody() {
        CelestialAssetStore store = newStore();
        CelestialAsset a1 = createAsset(BODY_1);
        CelestialAsset a2 = createAsset(BODY_2);
        CelestialAsset a3 = createAsset(BODY_1);

        store.registerAssetInternal(TEAM_A, a1);
        store.registerAssetInternal(TEAM_A, a2);
        store.registerAssetInternal(TEAM_A, a3);

        List<CelestialAsset> body1Assets = store.getStateInternal(TEAM_A, BODY_1);
        assertEquals(2, body1Assets.size(), "TEAM_A should have 2 assets on BODY_1");

        List<CelestialAsset> body2Assets = store.getStateInternal(TEAM_A, BODY_2);
        assertEquals(1, body2Assets.size(), "TEAM_A should have 1 asset on BODY_2");

        // Different team should have nothing
        List<CelestialAsset> teamBbody1 = store.getStateInternal(TEAM_B, BODY_1);
        assertTrue(teamBbody1.isEmpty(), "TEAM_B should have no assets on BODY_1");
    }

    @Test
    void getTeamAssets() {
        CelestialAssetStore store = newStore();
        CelestialAsset a1 = createAsset(BODY_1);
        CelestialAsset a2 = createAsset(BODY_2);

        store.registerAssetInternal(TEAM_A, a1);
        store.registerAssetInternal(TEAM_A, a2);

        Map<CelestialObjectId, Set<CelestialAsset>> teamAssets = store.getTeamAssetsInternal(TEAM_A);
        assertEquals(2, teamAssets.size());
        assertTrue(teamAssets.containsKey(BODY_1));
        assertTrue(teamAssets.containsKey(BODY_2));
    }

    @Test
    void allAssetsIncludesAllTeams() {
        CelestialAssetStore store = newStore();
        CelestialAsset a1 = createAsset(BODY_1);
        CelestialAsset a2 = createAsset(BODY_2);

        store.registerAssetInternal(TEAM_A, a1);
        store.registerAssetInternal(TEAM_B, a2);

        List<CelestialAsset> all = store.allAssetsInternal();
        assertEquals(2, all.size());
    }

    @Test
    void destroyAssetRemovesFromAllMaps() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = createAsset(BODY_1);
        store.registerAssetInternal(TEAM_A, asset);

        assertTrue(store.destroyAssetInternal(asset.assetId));
        assertNull(store.findAssetInternal(asset.assetId));
        assertNull(store.getTeamIdInternal(asset.assetId));
        assertTrue(
            store.getStateInternal(TEAM_A, BODY_1)
                .isEmpty());

        // Double destroy returns false
        assertFalse(store.destroyAssetInternal(asset.assetId));
    }

    @Test
    void cancelConstructionOnlyForConstructionSites() {
        CelestialAssetStore store = newStore();
        CelestialAsset operational = createAsset(BODY_1);
        store.registerAssetInternal(TEAM_A, operational);

        // Operational asset cannot be cancelled
        assertFalse(store.cancelConstructionInternal(operational.assetId));
        assertNotNull(store.findAssetInternal(operational.assetId));

        // Construction site can be cancelled
        CelestialAsset construction = CelestialAsset
            .create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.CONSTRUCTION_SITE);
        construction.setDisplayName("test");
        store.registerAssetInternal(TEAM_A, construction);
        assertTrue(store.cancelConstructionInternal(construction.assetId));
        assertNull(store.findAssetInternal(construction.assetId));
    }

    @Test
    void renameAsset() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = CelestialAsset
            .create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.CONSTRUCTION_SITE);
        asset.setDisplayName("OldName");
        store.registerAssetInternal(TEAM_A, asset);
        assertTrue(store.renameAssetInternal(asset.assetId, "NewName"));
        assertEquals("NewName", asset.displayName());

        // Empty name rejected
        assertFalse(store.renameAssetInternal(asset.assetId, ""));
        assertFalse(store.renameAssetInternal(asset.assetId, "   "));
    }

    @Test
    void clearRemovesAllState() {
        CelestialAssetStore store = newStore();
        store.registerAssetInternal(TEAM_A, createAsset(BODY_1));
        store.registerAssetInternal(TEAM_B, createAsset(BODY_2));
        assertEquals(
            2,
            store.allAssetsInternal()
                .size());

        store.clearInternal();
        assertEquals(
            0,
            store.allAssetsInternal()
                .size());
        assertNull(store.findAssetInternal(null));
    }

    @Test
    void createAssetInConstruction() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = CelestialAsset
            .create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.CONSTRUCTION_SITE);
        asset.setDisplayName("My Outpost");
        store.registerAssetInternal(TEAM_A, asset);
        assertNotNull(asset);
        assertEquals(Buildable.Status.CONSTRUCTION_SITE, asset.status());
        assertEquals("My Outpost", asset.displayName());
        assertSame(asset, store.findAssetInternal(asset.assetId));
    }

    @Test
    void createOperationalAsset() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = CelestialAsset
            .create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
        asset.setDisplayName("My Outpost");
        store.registerAssetInternal(TEAM_A, asset);
        assertNotNull(asset);
        assertEquals(Buildable.Status.OPERATIONAL, asset.status());
    }

    @Test
    void isOwnedByChecksTeamMatch() {
        CelestialAssetStore store = newStore();
        CelestialAsset asset = CelestialAsset
            .create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.CONSTRUCTION_SITE);
        asset.setDisplayName("test");
        store.registerAssetInternal(TEAM_A, asset);
        assertTrue(store.isOwnedByInternal(TEAM_A, asset.assetId));
        assertFalse(store.isOwnedByInternal(TEAM_B, asset.assetId));
        assertFalse(store.isOwnedByInternal(TEAM_A, CelestialAsset.ID.create()));
    }

    @Test
    void staticClearDoesNotAffectClientInstance() {
        // Ensure pristine state
        CelestialAssetStore.clear();
        CelestialAssetStore.CLIENT.clearInternal();

        // Add to SERVER and CLIENT
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM_A, createAsset(BODY_1));
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM_A, createAsset(BODY_2));

        assertEquals(
            1,
            CelestialAssetStore.SERVER.allAssetsInternal()
                .size());
        assertEquals(
            1,
            CelestialAssetStore.CLIENT.allAssetsInternal()
                .size());

        // Static clear() clears SERVER only
        CelestialAssetStore.clear();

        assertEquals(
            0,
            CelestialAssetStore.SERVER.allAssetsInternal()
                .size());
        assertEquals(
            1,
            CelestialAssetStore.CLIENT.allAssetsInternal()
                .size(),
            "CLIENT should not be affected by static clear()");

        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void staticAddToConstructionInventory() {
        CelestialAsset asset = CelestialAsset.create(BODY_1, CelestialAsset.Kind.AUTOMATED_OUTPOST, false);
        asset.setDisplayName("test");
        CelestialAssetStore.registerAsset(TEAM_A, asset);
        assertNotNull(asset);
        assertSame(asset, CelestialAssetStore.findAsset(asset.assetId));
        assertEquals(Buildable.Status.CONSTRUCTION_SITE, asset.status());

        // static method should delegate to SERVER
        assertNull(
            CelestialAssetStore.CLIENT.findAssetInternal(asset.assetId),
            "CLIENT should not see assets created via static methods");

        // Clean up SERVER
        CelestialAssetStore.clear();
    }

    // ── Helpers ──

    private static CelestialAssetStore newStore() {
        return new CelestialAssetStore();
    }

    private static CelestialAsset createAsset(CelestialObjectId bodyId) {
        return CelestialAsset.create(bodyId, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
    }
}
