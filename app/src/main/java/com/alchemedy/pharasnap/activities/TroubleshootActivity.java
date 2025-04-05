package com.alchemedy.pharasnap.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.R;

public class TroubleshootActivity extends ThemeActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_troubleshoot);
        findViewById(R.id.troubleshoot_back).setOnClickListener(v -> finish());
    }
}
