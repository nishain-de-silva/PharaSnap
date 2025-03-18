package com.alchemedy.pharasnap.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.WidgetController;

public class NoDisplayHelperActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra(Constants.IGNORE_SERVICE_LAUNCH, false)) {
            WidgetController.launchWidget(this, false);
        }
        finish();
    }
}
