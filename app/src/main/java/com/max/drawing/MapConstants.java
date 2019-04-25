package com.max.drawing;

public class MapConstants {
    // extreme points from lantmateriet zoom level 0 (tile size 2^20; partial tiles for row/col 3)
    // these are used as global extreme points, e.g. for valid GPS coordinates in the quad tree
    public static final int UTM_EXTREME_X0 = -1_200_000;
    public static final int UTM_EXTREME_Y0 = 4_700_000;
    public static final int UTM_EXTREME_X1 = 2_600_000;
    public static final int UTM_EXTREME_Y1 = 8_500_000;

    /** Minimum zoom level at which we only have a map for Sweden (and not the rest of Europe). */
    public static final int SWEDEN_ONLY_MIN_ZOOM = 3;

    /** Maximum zoom level at which we have the full map for Sweden. */
    public static final int ALL_OF_SWEDEN_MAX_ZOOM = 5;

    // Numbers below chosen to correspond to tiles that actually exist on disk. This is to make
    // the region covered by map the same when zooming in and out. The reason the numbers have
    // been changed a bit from the region limits used by Lantmateriet is to get rid of some edge
    // tiles that didn't contain anything of interest anyway.
    public static final int UTM_SWEDEN_X0 = -1_200_000 + 44*(1<<15); // 241_792 vs 240_000 with Lantmateriet
    public static final int UTM_SWEDEN_Y0 = 8_500_000 - 73*(1<<15); // 6_107_936 vs 6_100_000 with Lantmateriet
    public static final int UTM_SWEDEN_X1 = -1_200_000 + 65*(1<<15); // 929_920 vs 940_000 with Lantmateriet
    public static final int UTM_SWEDEN_Y1 = 7_675_000;

}
