package com.ndds.lettersnap.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ndds.lettersnap.R;

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
        if (onTapListener != null) {
            setOnClickListener(null);
            onTapListener = null;
        }
    }

    @Override
    public void setVisibility(int visibility) {
        this.visibility = visibility;
        View parent = (View) getParent();
        parent.setVisibility(visibility);
    }

    public void changeMode(boolean isImageRecognitionMode) {
        if (isImageRecognitionMode) {
            changeText("Image mode. Tap the text on the image to capture");
        } else  {
            changeText(getContext().getText(R.string.text_hint_default));
        }
    }

    public void onTextCaptured(OnTapListener onTapListener) {
        if (visibility != View.VISIBLE)
            setVisibility(View.VISIBLE);
        setText("Text copied to clipboard. Tap to edit selection");
        this.onTapListener = onTapListener;
        setOnClickListener(v -> onTapListener.onTap());
    }
}
