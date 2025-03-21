package com.alchemedy.pharasnap.helper;

public abstract class OnTapListener {
    public abstract void onTap(CoordinateF tappedCoordinate);
    public void onMove(float dragDistance) {}
}
