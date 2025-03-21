package com.alchemedy.pharasnap.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            WalkthroughSlider.PageContent secondPage = new WalkthroughSlider.PageContent(R.string.add_tile_description, "Notification Shortcut");
            if (!isTutorialRestart) {
                secondPage
                        .setButtonText("Skip", true)
                        .onAttachStateChanged(new WalkthroughSlider.PageContent.AttachedStateListener() {
                            @Override
                            protected void onAttach() {
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

                            @Override
                            protected void onDetach() {
                                if (tileAddedSignalBroadcastListener != null) {
                                    LocalBroadcastManager.getInstance(MainActivity.this)
                                            .unregisterReceiver(tileAddedSignalBroadcastListener);
                                    tileAddedSignalBroadcastListener = null;
                                }
                            }
                        });
            }

            pages.add(secondPage);
        }
        pages.add(new WalkthroughSlider.PageContent(
                R.string.capturing_modes_tutorial,
                "Capture Modes"
        ).onDrawGraphics(R.layout.tutorial_graphic_controls));
        pages.add(new WalkthroughSlider.PageContent(R.string.text_copy_tutorial, "Copy Text selection")
                .onDrawGraphics(R.layout.tutorial_graphic_text_selection));
        pages.add(
                new WalkthroughSlider.PageContent(R.string.recent_items_tutorial, "Recent Items")
                        .onDrawGraphics(R.layout.tutorial_graphic_recent_items)
        );
    }

    private void showWalkthroughSlider() {
        setContentView(R.layout.introduction_layout);
        walkthroughSlider = findViewById(R.id.walkthrough_slider);
        walkthroughSlider.start(pages, new WalkthroughSlider.EventHandler() {
            @Override
            public Boolean onResume(int id) {
                if (id == R.string.accessibility_requirement_description || id == R.string.accessibility_requirement_description_first_time)
                    return AccessibilityHandler.isAccessibilityServiceEnabled(MainActivity.this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && id == R.string.overlay_requirement_description)
                    return Settings.canDrawOverlays(MainActivity.this);

                return null;
            }

            @SuppressLint("InlinedApi")
            @Override
            public boolean onButtonPress(int id) {
                if (id == R.string.accessibility_requirement_description || id == R.string.accessibility_requirement_description_first_time) {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return true;
                }
                if (id == R.string.overlay_requirement_description) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                    return true;
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
            WidgetController.launchWidget(this, true, WidgetController.DISABLED_PERMISSION_UNKNOWN);
        });
        TextView versionLabel = findViewById(R.id.version_label);
        versionLabel.setText(versionLabel.getText().toString()
                .replace("x.x", BuildConfig.VERSION_NAME)
        );
        findViewById(R.id.restart_tutorial).setOnClickListener(v -> {
            addTutorials(true);
            showWalkthroughSlider();
        });
        findViewById(R.id.rate_app).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
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
                    needToDisplayInitialWalkthrough ? R.string.accessibility_requirement_description_first_time : R.string.accessibility_requirement_description,
                    needToDisplayInitialWalkthrough ? "Accessibility Service Required" : "Enable Accessibility Service"
            )
                    .setButtonText(needToDisplayInitialWalkthrough ? "Grant Accessibility Access" : "Enable Accessibility", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(Color.WHITE);
        } else
            getWindow().setNavigationBarColor(getResources().getColor(R.color.darkPurple));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0) |
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        } else
            getWindow().setStatusBarColor(getResources().getColor(R.color.darkPurple));
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

