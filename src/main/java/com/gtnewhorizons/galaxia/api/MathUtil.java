package com.gtnewhorizons.galaxia.api;

public class MathUtil {

    public static long sign(long n) {
        return n >>> 63;
    }

    public static int sign(int n) {
        return n >>> 31;
    }
}
