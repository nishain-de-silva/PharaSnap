package com.alchemedy.pharasnap.widgets;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.alchemedy.pharasnap.R;

public class WidgetHighlighter extends View {
    View targetView;
    Canvas targetViewCanvas;
    Bitmap targetViewBitmap;
    public WidgetHighlighter(Context context) {
        super(context);
    }
    float animateFraction;
    Paint paint = new Paint();

    public static WidgetHighlighter create(ViewGroup parentView, View targetView) {
        WidgetHighlighter createdView = new WidgetHighlighter(parentView.getContext());
        createdView.setClickable(false);
        createdView.paint.setColor(parentView.getContext().getColor(R.color.primaryBlue));
        createdView.paint.setStyle(Paint.Style.FILL);
        int size = (int) (targetView.getWidth() + (targetView.getHeight() * 1.5f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                size,
                size,
                Gravity.CENTER
        );

        createdView.updateLayout(parentView, targetView, params, true);
        parentView.addView(createdView,parentView.getChildCount() - 1);


        return createdView;
    }

    private void updateLayout(ViewGroup parentView, View targetView, FrameLayout.LayoutParams params, boolean animate) {
        int size = (int) Math.max(targetView.getWidth() * 1.2f, targetView.getHeight() * 2.5f);
        params.height = size;
        params.width = size;
        this.targetView = targetView;
        targetViewBitmap = Bitmap.createBitmap(targetView.getWidth(), targetView.getHeight(), Bitmap.Config.ARGB_8888);
        targetViewCanvas = new Canvas(targetViewBitmap);
        int[] rootOverlayOffset = new int[2], targetViewOffset = new int[2];
        parentView.getLocationOnScreen(rootOverlayOffset);
        targetView.getLocationOnScreen(targetViewOffset);
        Rect rootOverlayRect = new Rect(rootOverlayOffset[0], rootOverlayOffset[1], rootOverlayOffset[0] + parentView.getWidth(), rootOverlayOffset[1] + parentView.getHeight());
        Rect targetViewRect = new Rect(targetViewOffset[0], targetViewOffset[1], targetViewOffset[0] + targetView.getWidth(), targetViewOffset[1] + targetView.getHeight());

        params.topMargin = targetViewRect.centerY() - rootOverlayRect.centerY();
        params.leftMargin = targetViewRect.centerX() - rootOverlayRect.centerX();

        if (animate)
            post(() -> {
                ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
                animator.setDuration(400);
                animator.addUpdateListener(valueAnimator -> {
                    animateFraction = valueAnimator.getAnimatedFraction();
                    invalidate();
                });
                animator.start();
            });
        setLayoutParams(params);
    }

    public void update(ViewGroup parentView, View targetView) {
       updateLayout(parentView, targetView, (FrameLayout.LayoutParams) getLayoutParams(), false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawCircle(
                getWidth() / 2f,
                getHeight() / 2f,
                (getWidth() / 2f) * animateFraction,
                paint
        );
        targetViewCanvas.drawColor(Color.TRANSPARENT);
        targetView.draw(targetViewCanvas);
        canvas.drawBitmap(
                targetViewBitmap,
                (getWidth() - targetViewBitmap.getWidth()) / 2f,
                (getHeight() - targetViewBitmap.getHeight()) / 2f,
                paint
                );
    }

    public void orientationChanged() {
        updateLayout((ViewGroup) getParent(), targetView, (FrameLayout.LayoutParams) getLayoutParams(), false);
    }
}
