package com.alchemedy.pharasnap.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.CoordinateF;


public class EnableButton extends androidx.appcompat.widget.AppCompatImageButton {

    public abstract static class TouchDelegateListener {
        public abstract void onTap(CoordinateF tappedCoordinate);
        public abstract void onDragGestureStarted();
        public abstract void onRelease();
    }
    TouchDelegateListener onTapListener;
    WindowManager.LayoutParams params;
    public boolean isExpanded = false;
    View overlayView;
    public boolean isLeftOriented;
    WindowManager windowManager;
    public EnableButton(Context context) {
        super(context);
    }

    public EnableButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EnableButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void configure(TouchDelegateListener onTapListener, View overlayView, WindowManager windowManager, WindowManager.LayoutParams params) {
        this.overlayView = overlayView;
        this.windowManager = windowManager;
        this.params = params;
        this.onTapListener = onTapListener;
        View buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        final int GESTURE_UNDETERMINED = 0, GESTURE_TAP = 1, GESTURE_DRAG = 2;
        setOnTouchListener(new OnTouchListener() {
            int gestureMode = GESTURE_UNDETERMINED;
            CoordinateF enableButtonDownCoordinate = new CoordinateF(0,0);
            private int initialY, initialX;
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                float x = motionEvent.getRawX();
                float y = motionEvent.getRawY();
                if (action == MotionEvent.ACTION_MOVE) {
                    if (isExpanded) {
                        buttonContainer.setTranslationY(initialY + (y - enableButtonDownCoordinate.y));
                        buttonContainer.setTranslationX(initialX + (x - enableButtonDownCoordinate.x));
                    } else {
                        params.y = (int) (initialY + (y - enableButtonDownCoordinate.y));
                        params.x = (int) (initialX + (isLeftOriented ? (x - enableButtonDownCoordinate.x) : (enableButtonDownCoordinate.x - x)));
                        windowManager.updateViewLayout(overlayView, params);
                    }
                    if (gestureMode == GESTURE_UNDETERMINED) {
                        if (!enableButtonDownCoordinate.isCloserTo(x, y, 15)) {
                            gestureMode = GESTURE_DRAG;
                            onTapListener.onDragGestureStarted();
                        }
                    }

                    return true;
                } else if (action == MotionEvent.ACTION_UP) {
                    if(gestureMode == GESTURE_UNDETERMINED && enableButtonDownCoordinate.isCloserTo(x, y, 15)) {
                        onTapListener.onTap(new CoordinateF(x, y));
                        gestureMode = GESTURE_TAP;
                        performClick();
                    } else
                        onTapListener.onRelease();
                    return true;
                } else if (action == MotionEvent.ACTION_DOWN) {
                    if (isExpanded) {
                        initialY = (int) buttonContainer.getTranslationY();
                        initialX = (int) buttonContainer.getTranslationX();
                    } else {
                        initialY = params.y;
                        initialX = params.x;
                    }
                    gestureMode = GESTURE_UNDETERMINED;
                    enableButtonDownCoordinate = new CoordinateF(x, y);
                    return true;
                }
                return false;
            }
        });
    }
}
