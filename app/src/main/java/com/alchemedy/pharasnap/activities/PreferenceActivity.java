package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;

public class PreferenceActivity extends ThemeActivity {
    String activeLongPressButtonTrigger;
    boolean isActivelyCapturingLongPressingButton;
    private Button chooser, disableButton;
    private MessageHandler messageHandler;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preference);
        findViewById(R.id.back).setOnClickListener(v -> {
            cancelCapturingForLongPressButton();
            finish();
        });
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        activeLongPressButtonTrigger = sharedPreferences.getString(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON, null);
        chooser = findViewById(R.id.longButtonChooser);
        chooser.setText(activeLongPressButtonTrigger == null ? "Assign" : activeLongPressButtonTrigger);
        disableButton = findViewById(R.id.disableLongButtonTrigger);
        messageHandler = new MessageHandler(this);
        disableButton.setOnClickListener(v -> {
            v.setVisibility(View.GONE);
            messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                    .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.DISABLE_BUTTON));
            chooser.setText("Assign");
            sharedPreferences.edit().remove(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON).apply();
            activeLongPressButtonTrigger = null;
        });
        if (activeLongPressButtonTrigger == null)
            disableButton.setVisibility(View.GONE);

        chooser.setOnClickListener(view -> {
            if (isActivelyCapturingLongPressingButton) {
                cancelCapturingForLongPressButton();
                return;
            }

            chooser.setText("Cancel");
            disableButton.setVisibility(View.GONE);
            isActivelyCapturingLongPressingButton = true;
            messageHandler.registerReceiverOnce(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String result = intent.getStringExtra(Constants.NAVIGATION_LONG_PRESS.NEW_BUTTON_PAYLOAD);
                    assert result != null;
                    if (result.isEmpty()) {
                        cancelCapturingForLongPressButton();
                        return;
                    }
                    activeLongPressButtonTrigger = result;
                    chooser.setText(result);
                    isActivelyCapturingLongPressingButton = false;
                    disableButton.setVisibility(View.VISIBLE);
                    sharedPreferences.edit().putString(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON, result).apply();
                    Toast.makeText(context, "Long press button captured", Toast.LENGTH_SHORT).show();
                }
            }, Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION);
            messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                    .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.START_CAPTURE));
        });
    }

    private void cancelCapturingForLongPressButton() {
        if (activeLongPressButtonTrigger == null)
            chooser.setText("Assign");
        else {
            chooser.setText(activeLongPressButtonTrigger);
            disableButton.setVisibility(View.VISIBLE);
        }

        isActivelyCapturingLongPressingButton = false;
        messageHandler.unregisterReceiver(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION);
        messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.CANCEL_CAPTURE));
    }
}
