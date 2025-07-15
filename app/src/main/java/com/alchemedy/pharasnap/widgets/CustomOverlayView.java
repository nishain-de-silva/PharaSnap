package com.alchemedy.pharasnap.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.models.CopiedItem;

import java.util.ArrayList;

public class CustomOverlayView extends FrameLayout {

    public static abstract class OnTapListener {
        public abstract void onTap(CoordinateF tappedCoordinate, boolean isLongPress);
        public void onMove(float dragDistance) {}
    }

    private Paint paint;
    private int highlightFillColor = Color.parseColor("#6430C5FF");
    private int highlightStrokeColor = Color.parseColor("#30C5FF");
    private final int PRIMARY_STROKE_WIDTH = 3;
//    private Rect dot = null;

    private OnTapListener onTapListener;
    public CoordinateF lastTapCoordinate;
    private OnDismissListener onDismissListener;
    ArrayList<RectF> selections = new ArrayList<>();

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


    private void initPaint() {
        paint = new Paint();
    }

    public void setOnDismissListener(OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }
    private boolean isBackButtonPressedDown;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                isBackButtonPressedDown = true;
            } else if(event.getAction() == MotionEvent.ACTION_UP) {
                if (isBackButtonPressedDown)
                    onDismissListener.onDismiss();
                isBackButtonPressedDown = false;
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    public void disableTouchListener() {
        setOnTouchListener(null);
    }
    public void enableTouchListener() {
        setOnTouchListener(new OnTouchListener() {
            CoordinateF downCoordinate = new CoordinateF(0, 0);
            boolean isDragGesture;
            Handler longPressHandler = null;
            Runnable longPressHandlerTask = null;

            void purgeTimer() {
                if (longPressHandler != null) {
                    longPressHandler.removeCallbacks(longPressHandlerTask);
                    longPressHandler = null;
                    longPressHandlerTask = null;
                }
            }
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int eventAction = event.getAction();
                float x = event.getRawX();
                float y = event.getRawY();
                if (eventAction == MotionEvent.ACTION_MOVE) {
                    if (!downCoordinate.isCloserTo(x, y, 15) && !isDragGesture) {
                        isDragGesture = true;
                        purgeTimer();
                    }
                } else if (eventAction == MotionEvent.ACTION_UP) {
                    if (!isDragGesture && longPressHandler != null) {
                        purgeTimer();
                        lastTapCoordinate = new CoordinateF(x, y);
                        onTapListener.onTap(lastTapCoordinate, false);
                        performClick();
                        return true; // Consume the event
                    } else {
                        purgeTimer();
                        onTapListener.onMove(
                                (float) Math.sqrt(Math.pow(x - downCoordinate.x, 2) + Math.pow(y - downCoordinate.y, 2))
                        );
                    }
                } else if (eventAction == MotionEvent.ACTION_DOWN) {
                    downCoordinate = new CoordinateF(x, y);
                    longPressHandler = new Handler();
                    longPressHandlerTask = () -> {
                        longPressHandler = null;
                        longPressHandlerTask = null;
                        onTapListener.onTap(new CoordinateF(x, y), true);
                        performLongClick();
                    };
                    isDragGesture = false;
                    longPressHandler.postDelayed(longPressHandlerTask, 700);
                    return true;
                }
                return false; // Allow the event to pass through
            }
        });
    }

    public void drawDebugDot(int x, int y) {
        int radius = getResources().getDimensionPixelSize(R.dimen.node_capture_proximity_radius);
        int[] offset = new int[2];
        getLocationOnScreen(offset);
//        dot = new Rect(x - offset[0] - radius, y - offset[1] - radius, x - offset[0] + radius, y - offset[1] + radius);
        invalidate();
    }

    public void addSingleBoundingBox(Rect boundingBox) {
        int[] offset = new int[2];
        getLocationOnScreen(offset);
        boundingBox.offset(-offset[0], -offset[1]);
        RectF animatingBoundingBox = new RectF(boundingBox);

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.setDuration(200);
        int width = boundingBox.width();
        int height = boundingBox.height();

        selections.clear();
        selections.add(animatingBoundingBox);

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
    public boolean addNewBoundingBox(ArrayList<CopiedItem> capturedTexts, CopiedItem copiedItem) {
        int[] offset = new int[2];
        getLocationOnScreen(offset);
        Rect boundingBox = copiedItem.rect;
        boundingBox.offset(-offset[0], -offset[1]);
        RectF animatingBoundingBox = new RectF(boundingBox);
        for (int i = 0; i < selections.size(); i++) {
            RectF selection = selections.get(i);
            // if the given bounding box is already existing remove it and return adding selection is un-necessary.
            if (selection.top == animatingBoundingBox.top && selection.bottom == animatingBoundingBox.bottom
            && selection.left == animatingBoundingBox.left && selection.right == animatingBoundingBox.right) {
                selections.remove(i);
                capturedTexts.remove(i);
                return false;
            }

            // checking if the given bounding box is overlapping with any other bounding boxes
            if (animatingBoundingBox.contains(selection) || (
                    animatingBoundingBox.intersects(selection.left, selection.top, selection.right, selection.bottom) &&
                            !(capturedTexts.get(i).isOCRText && copiedItem.isOCRText)
            )) {
                selections.remove(i);
                capturedTexts.remove(i);
                i--;
            }
        }

        // add the new selection bounding box in ordered index in natural reading order of the screen
        // (top-bottom - first priority and left-right - second priority)
        if (!selections.isEmpty()) {
            int index;
            for (index = 0; index < selections.size(); index++) {
                RectF selection = selections.get(index);
                if (animatingBoundingBox.top < selection.top || (animatingBoundingBox.top == selection.top
                        && animatingBoundingBox.left < selection.left)) {
                    break;
                }
            }
            selections.add(index, animatingBoundingBox);
            capturedTexts.add(index, copiedItem);
        } else {
            selections.add(animatingBoundingBox);
            capturedTexts.add(copiedItem);
        }

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.setDuration(200);
        int width = boundingBox.width();
        int height = boundingBox.height();

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
        return true;
    }
    public boolean clearAllSelections() {
        boolean isEmpty = selections.isEmpty();
        selections.clear();
        invalidate();
        return isEmpty;
    }

    public int removeSelection(float tappedX, float tappedY) {
        if (selections.isEmpty()) return -1;
        int i = 0;
        int[] offset = new int[2];
        getLocationOnScreen(offset);
        float relativeX = tappedX - offset[0];
        float relativeY = tappedY - offset[0];
        for (RectF boundingBox: selections) {
            if (boundingBox.contains(relativeX, relativeY)) {
                selections.remove(i);
                invalidate();
                return i;
            }
            i++;
        }
        return -1;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (RectF boundingBox: selections) {
            paint.setColor(highlightFillColor);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(boundingBox, 10, 10, paint);
            paint.setStrokeWidth(PRIMARY_STROKE_WIDTH);
            paint.setColor(highlightStrokeColor);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(boundingBox, 10, 10, paint);
        }
//        if (dot != null) {
//            paint.setColor(Color.RED);
//            paint.setStyle(Paint.Style.FILL);
//            canvas.drawRect(dot, paint);
//        }
    }
}
