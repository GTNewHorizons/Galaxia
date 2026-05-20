package com.gtnewhorizons.galaxia.compat.teams;

public enum TeamAction {

    CREATE_ASSET(TeamRole.OFFICER),
    BUILD_MODULE(TeamRole.OFFICER),
    MODIFY_MODULE(TeamRole.OFFICER),
    CONFIGURE_LOGISTICS(TeamRole.OFFICER),
    MANAGE_INVENTORY(TeamRole.OFFICER),
    RENAME_ASSET(TeamRole.OFFICER),
    DESTROY_ASSET(TeamRole.OWNER),
    DECONSTRUCT_ASSET(TeamRole.OWNER);

    private final TeamRole defaultRole;

    TeamAction(TeamRole defaultRole) {
        this.defaultRole = defaultRole;
    }

    public TeamRole getDefaultRole() {
        return defaultRole;
    }
}
