package com.alchemedy.pharasnap.utils;

import android.animation.ValueAnimator;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.widgets.EnableButton;

public class FloatingDismissWidget {
    WindowManager windowManager;
    NodeExplorerAccessibilityService hostingService;
    int offset;
    ValueAnimator animator;
    WindowManager.LayoutParams dismissWidgetButtonParams;
    View dismissWidgetButtonView, overlayView;
    Rect dismissButtonLocationOnScreen;
    ValueAnimator.AnimatorUpdateListener updateListener;
    View.OnLayoutChangeListener onLayoutChangeListener;

    public FloatingDismissWidget(WindowManager windowManager, View overlayView, NodeExplorerAccessibilityService hostingService) {
        this.windowManager = windowManager;
        this.overlayView = overlayView;
        this.hostingService = hostingService;
    }

    public void onGestureStarted() {
        ValueAnimator.AnimatorUpdateListener newUpdateListener = valueAnimator -> {
            float amount = (float) valueAnimator.getAnimatedValue();
            dismissWidgetButtonParams.y = (int) (amount);
            windowManager.updateViewLayout(dismissWidgetButtonView, dismissWidgetButtonParams);
            if (amount == offset) {
                animator = null;
                int [] dismissButtonLocationStart = new int[2];
                dismissWidgetButtonView.getLocationOnScreen(dismissButtonLocationStart);
                dismissButtonLocationOnScreen = new Rect(
                        dismissButtonLocationStart[0],
                        dismissButtonLocationStart[1],
                        dismissButtonLocationStart[0] + dismissWidgetButtonView.getWidth(),
                        dismissButtonLocationStart[1] + dismissWidgetButtonView.getHeight()
                );
            }
        };

        if (animator != null) {
            animator.removeUpdateListener(updateListener);
            animator.addUpdateListener(newUpdateListener);
            updateListener = newUpdateListener;
            animator.reverse();
        } else {
            offset = hostingService.getResources().getDimensionPixelSize(R.dimen.hover_delete_button_bottom_margin);
            animator = ValueAnimator.ofFloat(0, offset);
            animator.setDuration(250);
            updateListener = newUpdateListener;
            animator.addUpdateListener(updateListener);
            dismissWidgetButtonView = LayoutInflater.from(hostingService).inflate(R.layout.delete_button, null);
            onLayoutChangeListener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                    dismissWidgetButtonView.removeOnLayoutChangeListener(this);
                    onLayoutChangeListener = null;
                    animator.start();
                }
            };
            dismissWidgetButtonView.addOnLayoutChangeListener(onLayoutChangeListener);
            dismissWidgetButtonParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            dismissWidgetButtonParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            windowManager.addView(dismissWidgetButtonView, dismissWidgetButtonParams);
        }
    }

    public void clearResource() {
        if (dismissWidgetButtonView != null) {
            windowManager.removeView(dismissWidgetButtonView);
            dismissWidgetButtonView = null;
            if(animator != null) {
                animator.cancel();
            }
        }
    }

    public boolean onGestureReleased(EnableButton enableButton, WindowManager.LayoutParams params) {
        int[] enableButtonLocation = new int[2];
        enableButton.getLocationOnScreen(enableButtonLocation);
        boolean shouldStopWidget = dismissButtonLocationOnScreen != null && (
                dismissButtonLocationOnScreen.contains(enableButtonLocation[0], enableButtonLocation[1])
                        || dismissButtonLocationOnScreen.contains(enableButtonLocation[0] + enableButton.getWidth(), enableButtonLocation[1])
                        || dismissButtonLocationOnScreen.contains(enableButtonLocation[0] + enableButton.getWidth(), enableButtonLocation[1] + enableButton.getHeight())
                        || dismissButtonLocationOnScreen.contains(enableButtonLocation[0], enableButtonLocation[1] + enableButton.getHeight())
        );
        int initialEnableButtonYLevel = params.y;
        ValueAnimator.AnimatorUpdateListener newUpdateListener = valueAnimator -> {
            if (dismissWidgetButtonParams == null) return;
            float amount = (float) valueAnimator.getAnimatedValue();
            dismissWidgetButtonParams.y = (int) amount;
            windowManager.updateViewLayout(dismissWidgetButtonView, dismissWidgetButtonParams);
            if (shouldStopWidget) {
                params.y = (int) (initialEnableButtonYLevel + (offset - amount));
                windowManager.updateViewLayout(overlayView, params);
            }

            if (amount == 0) {
                animator = null;
                windowManager.removeView(dismissWidgetButtonView);
                dismissWidgetButtonView = null;
                dismissWidgetButtonParams = null;
                dismissButtonLocationOnScreen = null;
                if(shouldStopWidget) {
                    hostingService.onStopWidget();
                }
            }
        };
        if (animator != null) {
            if (onLayoutChangeListener != null) {
                dismissWidgetButtonView.removeOnLayoutChangeListener(onLayoutChangeListener);
                onLayoutChangeListener = null;
            }
            animator.removeUpdateListener(updateListener);
            animator.addUpdateListener(newUpdateListener);
            updateListener = newUpdateListener;
            animator.reverse();
        } else {
            offset = hostingService.getResources().getDimensionPixelSize(R.dimen.hover_delete_button_bottom_margin);
            animator = ValueAnimator.ofFloat(offset, 0);
            animator.setDuration(250);
            updateListener = newUpdateListener;
            animator.addUpdateListener(updateListener);
            animator.start();
        }
        return shouldStopWidget;
    }
}
