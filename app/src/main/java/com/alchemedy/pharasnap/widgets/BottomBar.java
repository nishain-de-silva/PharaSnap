package com.alchemedy.pharasnap.widgets;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.utils.FloatingWidget;

import java.util.ArrayList;

public class BottomBar extends androidx.appcompat.widget.AppCompatTextView {
    private int visibility = VISIBLE;
    public BottomBar(@NonNull Context context) {
        super(context);
    }

    public BottomBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public static abstract class OnTapListener {
        public abstract void onTap();
    }

    OnTapListener onTapListener;


    public void changeText(CharSequence text) {
        if (visibility != View.VISIBLE)
            setVisibility(View.VISIBLE);
        setText(text);
    }
    public void changeTextAndNotify(CharSequence text) {
        if (visibility != View.VISIBLE)
            setVisibility(View.VISIBLE);
        else {
            ObjectAnimator.ofFloat(this, "translationX", 0, -30, 30, 0, -30, 0)
                    .setDuration(500)
                    .start();
        }
        setText(text);
    }

    @Override
    public void setVisibility(int newVisibility) {
        this.visibility = newVisibility;
        View parent = (View) getParent();
        parent.setVisibility(newVisibility);
    }

    public void changeMode(int newMode) {
        if (newMode == FloatingWidget.Mode.TEXT)
            changeText(getContext().getText(R.string.text_hint_default));
        else
            changeText("Picture mode. Tap on an image to capture cropped image from the screen");
    }

    public void resetTranslationIfNeeded(ArrayList<Rect> existingBounds) {
        View parent = (View) getParent();
        int[] offset = new int[2];
        parent.getLocationOnScreen(offset);
        offset[1] -= (int) parent.getTranslationY();
        Rect bounds = new Rect(offset[0], offset[1], offset[0] + parent.getWidth(), offset[1] + parent.getHeight());

        for (Rect existingBound : existingBounds) {
            if(bounds.intersects(existingBound.left, existingBound.top, existingBound.right, existingBound.bottom))
                return;
        }
        resetTranslation();
    }
    public void resetTranslation() {
        ((View) getParent()).setTranslationY(0);
    }

    public void setOnTapListener(OnTapListener onTapListener) {
        this.onTapListener = onTapListener;
        ((View) getParent()).setOnTouchListener(new OnTouchListener() {
            CoordinateF downCoordinate;
            float downTranslationY;
            boolean isDrag;
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    isDrag = false;
                    downCoordinate = new CoordinateF(motionEvent.getRawX(), motionEvent.getRawY());
                    downTranslationY = view.getTranslationY();
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (!isDrag && !downCoordinate.isCloserTo(motionEvent.getRawX(), motionEvent.getRawY(), 15)) {
                        isDrag = true;
                    }
                    if (isDrag) {
                        float newTranslation = downTranslationY + motionEvent.getRawY() - downCoordinate.y;
                        if (newTranslation < 0)
                            view.setTranslationY(newTranslation);
                        else
                            view.setTranslationY(0);
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    if (!isDrag) {
                        onTapListener.onTap();
                        view.performClick();
                    }
                }
                return true;
            }
        });
    }
}
