package com.ndds.lettersnap.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ndds.lettersnap.helper.Constants;
import com.ndds.lettersnap.utils.AccessibilityHandler;

public class NoDisplayHelperActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra(Constants.IGNORE_SERVICE_LAUNCH, false)) {
            if(!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please provide overlay permission", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } else if (!AccessibilityHandler.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(this, "Please enable the accessibility service", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }  else
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE));
        }
        finish();
    }
}
