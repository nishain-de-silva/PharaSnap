package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.LongPressButtonTriggerInfo;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

public class SettingsActivity extends ThemeActivity {
    boolean isActivelyCapturingLongPressingButton;
    private TextView longPressButtonPreview;
    private Button disableButton, changeButton;
    private View indicator;
    private MessageHandler messageHandler;
    private LongPressButtonTriggerInfo longPressButtonTrigger;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        prepareLongPressTriggerSettings();
        prepareWidgetPositionOrientationSetting();

    }

    private void prepareLongPressTriggerSettings() {
        String longPressButtonTriggerJSON = sharedPreferences.getString(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON, null);
        if (longPressButtonTriggerJSON != null)
            longPressButtonTrigger = new LongPressButtonTriggerInfo(longPressButtonTriggerJSON);
        longPressButtonPreview = findViewById(R.id.setting_long_button_indicator);
        longPressButtonPreview.setText(longPressButtonTrigger == null ? "None selected" : String.format("%s button", longPressButtonTrigger.contentDescription.toLowerCase()));
        disableButton = findViewById(R.id.setting_disable_long_press);
        messageHandler = new MessageHandler(this);
        disableButton.setOnClickListener(v -> {
            v.setVisibility(View.GONE);
            messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                    .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.DISABLE_BUTTON));
            longPressButtonPreview.setText("None selected");
            changeButton.setText(R.string.long_press_button_changer_default_label);
            sharedPreferences.edit().remove(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON).apply();
            longPressButtonTrigger = null;
        });
        changeButton = findViewById(R.id.setting_change_long_press_button);
        indicator = findViewById(R.id.setting_long_press_button_record_indicator);
        indicator.setVisibility(View.GONE);
        if (longPressButtonTrigger == null) {
            changeButton.setText(R.string.long_press_button_changer_default_label);
        } else
            disableButton.setVisibility(View.VISIBLE);

        changeButton.setOnClickListener(view -> {
            if (isActivelyCapturingLongPressingButton) {
                cancelCapturingForLongPressButton(true);
                return;
            }

            changeButton.setText("Cancel");
            disableButton.setVisibility(View.GONE);
            indicator.setVisibility(View.VISIBLE);

            isActivelyCapturingLongPressingButton = true;
            animateNavigationBar();
            messageHandler.registerReceiverOnce(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String result = intent.getStringExtra(Constants.NAVIGATION_LONG_PRESS.NEW_BUTTON_PAYLOAD);
                    assert result != null;
                    if (result.isEmpty()) {
                        cancelCapturingForLongPressButton(false);
                        return;
                    }
                    longPressButtonTrigger = new LongPressButtonTriggerInfo(result);
                    endLongButtonCaptureSession();
                    sharedPreferences.edit().putString(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON, result).apply();
                    Toast.makeText(context, "button successfully recorded", Toast.LENGTH_SHORT).show();
                }
            }, Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION);
            messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                    .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.START_CAPTURE));
        });
    }

    public void prepareWidgetPositionOrientationSetting() {
        Spinner positionOrientationSpinner = findViewById(R.id.setting_widget_position_orientation);
        positionOrientationSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, new String[] {
                "left hand", "right hand"
        }));
        positionOrientationSpinner.setSelection(sharedPreferences.getBoolean(Constants.IS_WIDGET_LEFT_ORIENTED, false) ? 0 : 1);
        positionOrientationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0)
                    sharedPreferences.edit().putBoolean(Constants.IS_WIDGET_LEFT_ORIENTED, true).apply();
                else
                    sharedPreferences.edit().remove(Constants.IS_WIDGET_LEFT_ORIENTED).apply();
                if (FloatingWidget.isWidgetIsShowing)
                    messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.IS_WIDGET_LEFT_ORIENTED, i == 0));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void cancelCapturingForLongPressButton(boolean notifyAccessibilityService) {
        endLongButtonCaptureSession();
        messageHandler.unregisterReceiver(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION);
        if (notifyAccessibilityService)
            messageHandler.sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                    .putExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, Constants.NAVIGATION_LONG_PRESS.CANCEL_CAPTURE));
    }
    private void endLongButtonCaptureSession() {
        longPressButtonPreview.setText(longPressButtonTrigger == null ? "None selected" : String.format("%s button", longPressButtonTrigger.contentDescription.toLowerCase()));
        if (longPressButtonTrigger == null)
            changeButton.setText(R.string.long_press_button_changer_default_label);
        else
            changeButton.setText("Change");
        isActivelyCapturingLongPressingButton = false;
        disableButton.setVisibility(longPressButtonTrigger == null ? View.GONE : View.VISIBLE);
        indicator.setVisibility(View.GONE);
    }

    @Override
    public void finish() {
        if (isActivelyCapturingLongPressingButton)
            cancelCapturingForLongPressButton(true);
        super.finish();
    }

    private void animateNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Handler handler = new Handler();
            changeNavigationBarColorMode(true);
            handler.postDelayed(new Runnable() {
                int iteration = 0;
                @Override
                public void run() {
                    iteration++;
                    changeNavigationBarColorMode(iteration % 2 == 0);
                    if (iteration < 5)
                        handler.postDelayed(this, 400);
                }
            }, 400);
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void changeNavigationBarColorMode(boolean isNavigationBarDark) {
        if (isNavigationBarDark) {
            getWindow().setNavigationBarColor(getColor(R.color.primaryGreen));
        } else {
            getWindow().setNavigationBarColor(Color.WHITE);
        }
    }
}
