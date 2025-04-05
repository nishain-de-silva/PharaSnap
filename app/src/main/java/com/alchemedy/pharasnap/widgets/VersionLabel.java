package com.alchemedy.pharasnap.widgets;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.BuildConfig;

public class VersionLabel extends androidx.appcompat.widget.AppCompatTextView {
    public VersionLabel(Context context) {
        super(context);
        init();
    }

    public VersionLabel(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VersionLabel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText(getText().toString()
                .replace("x.x", String.format("%s%s", BuildConfig.VERSION_NAME, BuildConfig.DEBUG ? " (Dev)" : ""))
        );
    }
}
