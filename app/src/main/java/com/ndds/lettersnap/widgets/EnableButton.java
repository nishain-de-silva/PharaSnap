package com.ndds.lettersnap.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.ndds.lettersnap.helper.CoordinateF;
import com.ndds.lettersnap.helper.OnTapListener;

public class EnableButton extends androidx.appcompat.widget.AppCompatImageButton {
    OnTapListener onTapListener;
    WindowManager.LayoutParams params;
    View overlayView;
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

    public void configure(OnTapListener onTapListener, View overlayView, WindowManager windowManager, WindowManager.LayoutParams params) {
        this.overlayView = overlayView;
        this.windowManager = windowManager;
        this.params = params;
        this.onTapListener = onTapListener;
    }

    public void switchToStationaryState() {
        setOnTouchListener(null);
        setOnClickListener(v-> onTapListener.onTap(null));
    }

    public void switchToMovableState() {
        setOnTouchListener(new OnTouchListener() {
            CoordinateF enableButtonDownCoordinate = new CoordinateF(0,0);
            private int initialY, initialX;
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                float x = motionEvent.getRawX();
                float y = motionEvent.getRawY();
                if (action == MotionEvent.ACTION_MOVE) {
                    params.y = (int) (initialY + (y - enableButtonDownCoordinate.y));
                    params.x = (int) (initialX + (enableButtonDownCoordinate.x - x));
                    windowManager.updateViewLayout(overlayView, params);
                    return true;
                } else if (action == MotionEvent.ACTION_UP) {
                    if(enableButtonDownCoordinate.isCloserTo(x, y, 15)) {
                        onTapListener.onTap(new CoordinateF(x, y));
                    }
                    return true;
                } else if (action == MotionEvent.ACTION_DOWN){
                    initialY = params.y;
                    initialX = params.x;
                    enableButtonDownCoordinate = new CoordinateF(x, y);
                    return true;
                }
                return false;
            }
        });
    }
}
