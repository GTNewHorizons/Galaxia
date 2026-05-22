package com.gtnewhorizons.galaxia.registry.outpost.feature;

public final class FeatureContributionFormatter {

    private FeatureContributionFormatter() {}

    public static String percentDelta(String label, int deltaPercent) {
        return label + " " + signed(deltaPercent) + "%";
    }

    public static String percentMultiplierDelta(String label, int multiplierPercent) {
        return percentDelta(label, multiplierPercent - 100);
    }

    public static String bonus(String label, int amount) {
        return label + " " + signed(amount);
    }

    public static String perTickBonus(String label, int amount) {
        return bonus(label, amount) + "/tick";
    }

    public static String chance(String label, int chancePercent) {
        return label + " " + chancePercent + "%";
    }

    private static String signed(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }
}
