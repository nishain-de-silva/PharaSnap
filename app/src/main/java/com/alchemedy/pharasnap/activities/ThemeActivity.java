package com.alchemedy.pharasnap.activities;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.alchemedy.pharasnap.R;

public class ThemeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(Color.WHITE);
        } else
            getWindow().setNavigationBarColor(getResources().getColor(R.color.darkPurple));

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0) |
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View contentView = (((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        Rect originalPadding = new Rect(contentView.getPaddingLeft(), contentView.getPaddingTop(), contentView.getPaddingRight(), contentView.getPaddingBottom());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(
                    contentView, (v, insets) -> {
                        // Get the insets for system bars (status bar, navigation bar, caption bar)
                        Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                        // Apply padding to the root layout
                        contentView.setPadding(
                                originalPadding.left + systemBarsInsets.left,
                                originalPadding.top + systemBarsInsets.top,
                                originalPadding.right + systemBarsInsets.right,
                                originalPadding.bottom + systemBarsInsets.bottom
                        );

                        // Consume the insets so they aren't propagated further down the view hierarchy
                        return WindowInsetsCompat.CONSUMED;
                    });
            contentView.requestApplyInsets();
        }
    }
}
