package com.alchemedy.pharasnap.activities;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.BuildConfig;
import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.WidgetController;
import com.alchemedy.pharasnap.widgets.WalkthroughSlider;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    WalkthroughSlider walkthroughSlider;
    BroadcastReceiver tileAddedSignalBroadcastListener;
    boolean needToDisplayInitialWalkthrough = false;
    private ArrayList<WalkthroughSlider.PageContent> pages = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    private void addTutorials(boolean isTutorialRestart) {
        pages.add(0, new WalkthroughSlider.PageContent(R.string.introduction, "Introduction"));
        WalkthroughSlider.PageContent secondPage = new WalkthroughSlider.PageContent(R.string.add_tile_description, "Notification Shortcut");
        if (!isTutorialRestart) {
            secondPage
                    .setButtonText("Skip", true)
                    .onCreate(new WalkthroughSlider.PageContent.CreateCallback() {
                        @Override
                        protected void onCreate() {
                            tileAddedSignalBroadcastListener = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(this);
                                    tileAddedSignalBroadcastListener = null;
                                    walkthroughSlider.indicateTaskCompleted("Well done on adding the shortcut!");
                                }
                            };
                            LocalBroadcastManager.getInstance(MainActivity.this)
                                    .registerReceiver(tileAddedSignalBroadcastListener, new IntentFilter(Constants.TILE_ADDED_WHILE_TUTORIAL));
                        }
                    });
        }

        pages.add(secondPage);
        pages.add(new WalkthroughSlider.PageContent(
                R.string.control_use_tutorial,
                "Capture Modes"
        ));
        pages.add(new WalkthroughSlider.PageContent(R.string.control_use_tutorial_extended, "Final bit"));
    }

    private void showWalkthroughSlider() {
        setContentView(R.layout.introduction_layout);
        walkthroughSlider = findViewById(R.id.walkthrough_slider);
        walkthroughSlider.start(pages, new WalkthroughSlider.EventHandler() {
            @Override
            public boolean onResume(int id) {
                if (id == R.string.accessibility_requirement_description)
                    return AccessibilityHandler.isAccessibilityServiceEnabled(MainActivity.this);
                if (id == R.string.overlay_requirement_description)
                    return Settings.canDrawOverlays(MainActivity.this);

                return false;
            }

            @Override
            public boolean onButtonPress(int id) {
                if (id == R.string.accessibility_requirement_description) {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return true;
                }
                if (id == R.string.overlay_requirement_description) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                    return true;
                }
                if (id == R.string.add_tile_description && tileAddedSignalBroadcastListener != null) {
                    LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(tileAddedSignalBroadcastListener);
                    tileAddedSignalBroadcastListener = null;
                }
                return false;
            }

            @Override
            public void onComplete() {
                showMainMenu();
                pages.clear();
                sharedPreferences.edit().putBoolean(Constants.IS_TUTORIAL_SHOWN, true).apply();
            }
        });
    }

    private void showMainMenu() {
        setContentView(R.layout.activity_main);
        walkthroughSlider = null;
        View launchButton = findViewById(R.id.launch_widget);

        launchButton.setOnClickListener(v -> {
            WidgetController.launchWidget(this);
        });
        TextView versionLabel = findViewById(R.id.version_label);
        versionLabel.setText(versionLabel.getText().toString()
                .replace("code", String.valueOf(BuildConfig.VERSION_CODE))
                .replace("x.x", BuildConfig.VERSION_NAME)
        );
        findViewById(R.id.restart_tutorial).setOnClickListener(v -> {
            addTutorials(true);
            showWalkthroughSlider();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pages = new ArrayList<>();
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        needToDisplayInitialWalkthrough = !sharedPreferences.getBoolean(Constants.IS_TUTORIAL_SHOWN, false);

        if(!AccessibilityHandler.isAccessibilityServiceEnabled(this))
            pages.add(new WalkthroughSlider.PageContent(
                    R.string.accessibility_requirement_description,
                    "Accessibility Required"
            ).setButtonText("Grant Accessibility Permission", false));
        if (!Settings.canDrawOverlays(this))
            pages.add(new WalkthroughSlider.PageContent(
                    R.string.overlay_requirement_description,
                    "Overlay Permission Required"
            )
                    .setButtonText("Grant Draw Overlay Permission", false));

        if(needToDisplayInitialWalkthrough) {
            addTutorials(false);
        }
        if (pages.size() > 0)
            showWalkthroughSlider();
        else
            showMainMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (walkthroughSlider != null)
            walkthroughSlider.onActivityResume();
    }

    @Override
    protected void onDestroy() {
        if (tileAddedSignalBroadcastListener != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tileAddedSignalBroadcastListener);
        super.onDestroy();
    }
}

