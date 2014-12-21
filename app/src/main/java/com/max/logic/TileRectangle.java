package com.max.logic;

/** Tile rectangle coordinates. */
public class TileRectangle {
    public final int tx0, ty0, tx1, ty1;
    public final int tileSize;

    public TileRectangle(int x0, int y0, int x1, int y1, int level) {
        tileSize = 1<<(20-level);
        tx0 = (1_200_000 + x0) / tileSize;
        ty0 = (8_500_000 - y1) / tileSize;
        tx1 = (1_200_000 + x1) / tileSize;
        ty1 = (8_500_000 - y0) / tileSize;
    }

    @Override public String toString() {
        return String.format("%d,%d-%d,%d (size=%d, tiles=%d)", tx0, ty0, tx1, ty1, tileSize, (tx1-tx0+1)*(ty1-ty0+1));
    }
}
