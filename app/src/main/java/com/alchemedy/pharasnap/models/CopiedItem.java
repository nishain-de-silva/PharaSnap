package com.alchemedy.pharasnap.models;

import android.graphics.Rect;

public class CopiedItem {
    public String text;
    public Rect rect;
    public boolean isOCRText;

    public CopiedItem(String text, Rect rect, boolean isOCRText) {
        this.text = text;
        this.rect = rect;
        this.isOCRText = isOCRText;
    }

    public CopiedItem(Rect rect) {
        this.rect = rect;
    }
}
