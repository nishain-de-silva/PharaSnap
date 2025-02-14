package com.ndds.lettersnap.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ndds.lettersnap.helper.OnTapListener;
import com.ndds.lettersnap.helper.CoordinateF;

public class CustomOverlayView extends FrameLayout {

    private Paint paint;
    private int highlightFillColor = Color.parseColor("#6430C5FF");
    private int highlightStrokeColor = Color.parseColor("#30C5FF");
    private final int PRIMARY_STROKE_WIDTH = 3;

    private CoordinateF debugDot = null;

    private OnTapListener onTapListener;
    private OnDismissListener onDismissListener;
    private WindowManager.LayoutParams params;

    public static abstract class OnDismissListener {
        protected abstract void onDismiss();
    }

    public CustomOverlayView(@NonNull Context context) {
        super(context);
        initPaint();
    }

    public void setOnTapListener(OnTapListener onTapListener) {
        this.onTapListener = onTapListener;
    }
    public CustomOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public CustomOverlayView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    public CustomOverlayView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPaint();
    }
    RectF boundingBox = null;

    private void initPaint() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10);
        paint.setColor(Color.GREEN);
    }

    public void drawDot(CoordinateF dot) {
        int[] offset = new int[2];
        getLocationOnScreen(offset);
        debugDot = new CoordinateF(dot.x, dot.y);
        debugDot.x -= offset[0];
        debugDot.y -= offset[1];
        invalidate();
    }

    public void setOnDismissListener(OnDismissListener onDismissListener, WindowManager.LayoutParams params) {
        this.params = params;
        this.onDismissListener = onDismissListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK) && event.getAction() == MotionEvent.ACTION_UP) {
            onDismissListener.onDismiss();
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    public void disableTouchListener() {
        setOnTouchListener(null);
    }
    public void enableTouchListener() {
        setOnTouchListener(new OnTouchListener() {
            CoordinateF downCoordinate = new CoordinateF(0, 0);
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int eventAction = event.getAction();
                float x = event.getRawX();
                float y = event.getRawY();

                if (eventAction == MotionEvent.ACTION_UP) {
                    boolean isTap = downCoordinate.isCloserTo(x, y, 15);
                    if (isTap){
                        onTapListener.onTap(new CoordinateF(x, y));
                        return true; // Consume the event
                    } else {

                        onTapListener.onDrag(
                                (float) Math.sqrt(Math.pow(x - downCoordinate.x, 2) + Math.pow(y - downCoordinate.y, 2))
                        );
                    }
                } else if (eventAction == MotionEvent.ACTION_DOWN) {
                    downCoordinate = new CoordinateF(x, y);
                    return true;
                }
                return false; // Allow the event to pass through
            }
        });
    }

    public void showBoundingBox(Rect boundingBox) {
        int[] offset = new int[2];
        getLocationOnScreen(offset);
        boundingBox.offset(-offset[0], -offset[1]);

        RectF animatingBoundingBox = new RectF(boundingBox);

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.setDuration(200);
        int width = boundingBox.width();
        int height = boundingBox.height();
        this.boundingBox = animatingBoundingBox;
        valueAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            animatingBoundingBox.set(
                    (int) (boundingBox.left + width * (1 - fraction) / 2),
                    (int) (boundingBox.top + height * (1 - fraction) / 2),
                    (int) (boundingBox.right - width * (1 - fraction) / 2),
                    (int) (boundingBox.bottom - height * (1 - fraction) / 2)
            );
            invalidate();
        });
        valueAnimator.start();
    }
    public void hideBoundingBox() {
        if (boundingBox == null) return;
        boundingBox = null;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (boundingBox != null) {
            paint.setColor(highlightFillColor);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(boundingBox, 10, 10, paint);
            paint.setStrokeWidth(PRIMARY_STROKE_WIDTH);
            paint.setColor(highlightStrokeColor);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(boundingBox, 10, 10, paint);
        }
        if (debugDot != null) {
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(debugDot.x, debugDot.y, 15, paint);
        }
    }
}
