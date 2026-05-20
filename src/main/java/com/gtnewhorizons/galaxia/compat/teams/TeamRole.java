package com.gtnewhorizons.galaxia.compat.teams;

public enum TeamRole {

    MEMBER(0),
    OFFICER(1),
    OWNER(2);

    private final int rank;

    TeamRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(TeamRole other) {
        return this.rank >= other.rank;
    }
}
