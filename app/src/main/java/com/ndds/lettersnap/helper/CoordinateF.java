package com.ndds.lettersnap.helper;

public class CoordinateF {
    public float x, y;
    public CoordinateF(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public boolean isCloserTo(float x, float y, float threshold) {
        return Math.abs(this.x - x) < threshold
                && Math.abs(this.y - y) < threshold;
    }
}
