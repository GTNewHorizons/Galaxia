package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.client.gui.TeamPermissionScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import dev.modularui.preview.PreviewEntrypoint;

final class UtilityPreviews {

    private UtilityPreviews() {}

    static PreviewEntrypoint teamPermissions() {
        return PreviewEntrypoint.of(TeamPermissionScreen.class, context -> {
            PreviewSupport.initializePreviewTeam();
            return new TeamPermissionScreen()
                .buildUI(PreviewSupport.guiData(), PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    static PreviewEntrypoint itemPicker() {
        return PreviewEntrypoint.of(ItemPickerScreen.class, context -> {
            PreviewSupport.initializeClient();
            ItemPickerScreen.setPendingForSidebarDebugWithoutReturnScreen();
            return new ItemPickerScreen()
                .buildUI(PreviewSupport.guiData(), PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }
}
