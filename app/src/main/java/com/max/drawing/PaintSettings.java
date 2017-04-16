package com.max.drawing;

public interface PaintSettings {
    int fontSizeGPSStats();
    int fontSizeNavigationStats();
    int fontSizeNavigationSubscript();
    int fontSizePoi();
    int fontSizeScale();

    int navigationStatsOffsetFromRightEdge();
    int navigationSpeedOffsetFromCenter();
    int navigationDistOffsetFromCenter();

    /** in pixels */
    int scaleMarkerWidth();

    double tileScaleFactor();

    public class GalaxyS5 implements PaintSettings {
        @Override public int fontSizeGPSStats() { return 112; }
        @Override public int fontSizeNavigationStats() { return 72; }
        @Override public int fontSizeNavigationSubscript() { return 48; }
        @Override public int fontSizePoi() { return 20; }
        @Override public int fontSizeScale() { return 22; }
        @Override public int scaleMarkerWidth() { return 256; }
        @Override public int navigationStatsOffsetFromRightEdge() { return 220; }
        @Override public int navigationSpeedOffsetFromCenter() { return -175; }
        @Override public int navigationDistOffsetFromCenter() { return 225; }
        @Override public double tileScaleFactor() { return 1.0; }
    }

    public class GalaxyS6 implements PaintSettings {
        @Override public int fontSizeGPSStats() { return 150; }
        @Override public int fontSizeNavigationStats() { return 96; }
        @Override public int fontSizeNavigationSubscript() { return 64; }
        @Override public int fontSizePoi() { return 27; }
        @Override public int fontSizeScale() { return 29; }
        @Override public int scaleMarkerWidth() { return 350; }
        @Override public int navigationStatsOffsetFromRightEdge() { return 293; }
        @Override public int navigationSpeedOffsetFromCenter() { return -233; }
        @Override public int navigationDistOffsetFromCenter() { return 301; }
        @Override public double tileScaleFactor() { return 1.33; }
    }
}
