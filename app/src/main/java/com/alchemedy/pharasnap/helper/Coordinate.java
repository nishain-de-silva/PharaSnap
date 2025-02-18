package com.alchemedy.pharasnap.helper;

public class Coordinate {
    public int x, y;
    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isCloserTo(float x, float y, float threshold) {
        return Math.abs(this.x - x) < threshold
                && Math.abs(this.y - y) < threshold;
    }
}
