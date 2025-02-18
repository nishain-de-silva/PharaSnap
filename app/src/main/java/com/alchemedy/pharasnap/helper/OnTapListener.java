package com.alchemedy.pharasnap.helper;

public abstract class OnTapListener {
    public abstract void onTap(CoordinateF tappedCoordinate);
    public void onDrag(float dragDistance) {}
}
