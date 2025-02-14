package com.ndds.lettersnap.helper;

public abstract class OnTapListener {
    public abstract void onTap(CoordinateF tappedCoordinate);
    public void onDrag(float dragDistance) {}
}
