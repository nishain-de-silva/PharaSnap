package com.alchemedy.pharasnap.widgets;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;

public class TextHint extends androidx.appcompat.widget.AppCompatTextView {
    private int visibility = VISIBLE;
    public TextHint(@NonNull Context context) {
        super(context);
    }

    public TextHint(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TextHint(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

    public void changeMode(boolean isImageRecognitionMode) {
        if (isImageRecognitionMode) {
            changeText("Image mode. Tap the text on the image to capture");
        } else  {
            changeText(getContext().getText(R.string.text_hint_default));
        }
    }

    public void setOnTapListener(OnTapListener onTapListener) {
        this.onTapListener = onTapListener;
        setOnClickListener(v -> onTapListener.onTap());
    }
}
