package com.gtnewhorizons.galaxia.client.gui.mui;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/**
 * Phantom slots whose widgets are created after {@link com.cleanroommc.modularui.widget.WidgetTree#collectSyncValues}
 * ran (e.g. in {@code onInit} or dynamic modals) never get their
 * {@link com.cleanroommc.modularui.value.sync.SyncHandler}
 * registered; {@link com.cleanroommc.modularui.widgets.slot.ItemSlot#onUpdate} then calls {@code setEnabled} and
 * crashes.
 * Skip the sync-dependent part until the handler is initialised (or indefinitely if registration never happens).
 */
public final class SafePhantomItemSlot extends PhantomItemSlot {

    public static SafePhantomItemSlot create() {
        return new SafePhantomItemSlot();
    }

    private SafePhantomItemSlot() {}

    @Override
    public void onUpdate() {
        ItemSlotSH sh = getSyncHandler();
        if (!sh.isValid()) {
            return;
        }
        super.onUpdate();
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        ItemSlotSH sh = getSyncHandler();
        if (!sh.isValid()) {
            return Interactable.Result.IGNORE;
        }
        return super.onMousePressed(mouseButton);
    }

    @Override
    public SafePhantomItemSlot slot(ModularSlot slot) {
        super.slot(slot);
        return this;
    }
}
