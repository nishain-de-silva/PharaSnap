package com.alchemedy.pharasnap.activities;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
}
