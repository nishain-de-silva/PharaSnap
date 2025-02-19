package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.widgets.WalkthroughSlider;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    WalkthroughSlider walkthroughSlider;
    BroadcastReceiver tileAddedSignalBroadcastListener;
    boolean needToDisplayInitialWalkthrough = false;
    private ArrayList<WalkthroughSlider.PageContent> pages = new ArrayList<>();

    private void addTutorials(boolean isTutorialRestart) {
        pages.add(0, new WalkthroughSlider.PageContent(R.string.introduction));
        WalkthroughSlider.PageContent secondPage = new WalkthroughSlider.PageContent(R.string.add_tile_description);
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
                R.string.control_use_tutorial
        ));
        pages.add(new WalkthroughSlider.PageContent(R.string.control_use_tutorial_extended));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pages = new ArrayList<>();
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        needToDisplayInitialWalkthrough = !sharedPreferences.getBoolean(Constants.IS_TUTORIAL_SHOWN, false);
        walkthroughSlider = findViewById(R.id.walkthrough_slider);
        if(!AccessibilityHandler.isAccessibilityServiceEnabled(this))
            pages.add(new WalkthroughSlider.PageContent(
                    R.string.accessibility_requirement_description
            ).setButtonText("Grant Accessibility Permission", false));
        if (!Settings.canDrawOverlays(this))
            pages.add(new WalkthroughSlider.PageContent(R.string.overlay_requirement_description).setButtonText("Grant overdraw permission", false));


        if(needToDisplayInitialWalkthrough) {
            addTutorials(false);
        }

        walkthroughSlider.start(pages, new WalkthroughSlider.EventHandler() {
            @Override
            public boolean onResume(int id) {
                if (id == R.string.accessibility_requirement_description) return AccessibilityHandler.isAccessibilityServiceEnabled(MainActivity.this);
                if (id == R.string.overlay_requirement_description) return Settings.canDrawOverlays(MainActivity.this);

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
                walkthroughSlider.setVisibility(View.GONE);
                findViewById(R.id.bottom_button_layer)
                        .setVisibility(View.VISIBLE);
                sharedPreferences.edit().putBoolean(Constants.IS_TUTORIAL_SHOWN, true).apply();
            }
        });
        Button launchButton = findViewById(R.id.launch_widget);
        launchButton.setOnClickListener(v -> {
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE));
        });
        findViewById(R.id.restart_tutorial).setOnClickListener(v -> {
            pages.clear();
            walkthroughSlider.setVisibility(View.VISIBLE);
            findViewById(R.id.bottom_button_layer).setVisibility(View.GONE);
            addTutorials(true);
            walkthroughSlider.start(pages, null);
        });
        if (pages.size() > 0)
            findViewById(R.id.bottom_button_layer).setVisibility(View.GONE);
        else
            walkthroughSlider.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        walkthroughSlider.onActivityResume();
    }

    @Override
    protected void onDestroy() {
        if (tileAddedSignalBroadcastListener != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tileAddedSignalBroadcastListener);
        super.onDestroy();
    }
}

