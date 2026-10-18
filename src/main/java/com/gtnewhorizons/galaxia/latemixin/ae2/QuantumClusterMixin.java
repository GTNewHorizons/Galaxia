package com.gtnewhorizons.galaxia.latemixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.compat.ae2.IQuantumLink;
import com.gtnewhorizons.galaxia.compat.ae2.QuantumLinkRules;

import appeng.api.IAppEngApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.cache.helpers.ConnectionWrapper;
import appeng.me.cluster.implementations.QuantumCluster;
import appeng.tile.qnb.TileQuantumBridge;

/**
 * Quantum bridges only link across space when both ends have a relay satellite, see {@link QuantumLinkRules}
 */
@Mixin(value = QuantumCluster.class, remap = false)
public abstract class QuantumClusterMixin implements IQuantumLink {

    @Shadow
    private ConnectionWrapper connection;

    @Shadow
    private TileQuantumBridge center;

    @Shadow
    private boolean isDestroyed;

    @Shadow
    public abstract void updateStatus(boolean updateGrid);

    @Inject(method = "updateStatus", at = @At("HEAD"))
    private void galaxia$cutForbiddenLink(boolean updateGrid, CallbackInfo ci) {
        QuantumLinkRules.track(this);
        if (connection == null) return;
        IGridConnection link = connection.getConnection();
        if (link == null || QuantumLinkRules.isLinkAllowed(link.a(), link.b())) return;
        // Both ends share the wrapper, clearing it unlinks both
        link.destroy();
        connection.setConnection(null);
    }

    @Redirect(
        method = "updateStatus",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/IAppEngApi;createGridConnection(Lappeng/api/networking/IGridNode;Lappeng/api/networking/IGridNode;)Lappeng/api/networking/IGridConnection;"))
    private IGridConnection galaxia$vetoForbiddenLink(IAppEngApi api, IGridNode a, IGridNode b)
        throws FailedConnection {
        if (!QuantumLinkRules.isLinkAllowed(a, b)) {
            throw new FailedConnection("No relay satellites between the bridges");
        }
        return api.createGridConnection(a, b);
    }

    @Override
    public void galaxia$recheckLink() {
        if (galaxia$isAlive()) updateStatus(true);
    }

    @Override
    public boolean galaxia$isAlive() {
        return !isDestroyed && center != null;
    }
}
