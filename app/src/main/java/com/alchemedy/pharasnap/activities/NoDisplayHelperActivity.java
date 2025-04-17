package com.alchemedy.pharasnap.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.WidgetController;

public class NoDisplayHelperActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent.getBooleanExtra(Constants.LAUNCH_SERVICE_WITHOUT_CHECK, false)) {
            new MessageHandler(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
        } else {
            WidgetController.launchWidget(
                    this,
                    false,
                    intent.getBooleanExtra(Constants.IS_KNOWN_ACCESSIBILITY_DISABLED, false)
            );
        }
        finish();
    }
}
