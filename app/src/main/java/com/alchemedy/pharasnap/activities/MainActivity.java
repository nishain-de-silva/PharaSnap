package com.alchemedy.pharasnap.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.WidgetController;
import com.alchemedy.pharasnap.widgets.WalkthroughSlider;

import java.util.ArrayList;

public class MainActivity extends ThemeActivity {
    WalkthroughSlider walkthroughSlider;
    boolean needToDisplayInitialWalkthrough = false;
    private ArrayList<WalkthroughSlider.PageContent> pages = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private MessageHandler messageHandler;

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
                                messageHandler
                                        .registerReceiverOnce(new BroadcastReceiver() {
                                            @Override
                                            public void onReceive(Context context, Intent intent) {
                                                walkthroughSlider.indicateTaskCompleted("Well done on adding the shortcut!");
                                            }
                                        }, Constants.TILE_ADDED_WHILE_TUTORIAL);
                            }

                            @Override
                            protected void onDetach() {
                                messageHandler
                                        .unregisterReceiver(Constants.TILE_ADDED_WHILE_TUTORIAL);
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
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            pages.add(new WalkthroughSlider.PageContent(R.string.media_projection_request_explanation, "Media Projection Requests"));
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
            public boolean onResume(int id) {
                if (id == R.string.accessibility_requirement_description || id == R.string.accessibility_requirement_description_first_time)
                    return AccessibilityHandler.isAccessibilityServiceEnabled(MainActivity.this);

                return false;
            }

            @SuppressLint("InlinedApi")
            @Override
            public boolean onButtonPress(int id) {
                if (id == R.string.accessibility_requirement_description || id == R.string.accessibility_requirement_description_first_time) {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return true;
                }
                if (id == R.string.add_tile_description) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Are you sure ?")
                            .setMessage(R.string.add_tile_requirement)
                            .setPositiveButton("No wait", (dialogInterface, i) -> dialogInterface.dismiss())
                            .setNegativeButton("Skip anyway", (dialogInterface, i) -> {
                                walkthroughSlider.changePage(true, true);
                                dialogInterface.dismiss();
                            })
                            .show();
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

    @Override
    public void onBackPressed() {
        if (walkthroughSlider != null) {
            if(!walkthroughSlider.changePage(false, true))
                super.onBackPressed();
        } else
            super.onBackPressed();
    }

    private void showMainMenu() {
        setContentView(R.layout.activity_main);
        walkthroughSlider = null;
        View launchButton = findViewById(R.id.launch_widget);
        launchButton.setOnClickListener(v -> WidgetController.launchWidget(this, true, false));
        findViewById(R.id.troubleshoot).setOnClickListener(v -> startActivity(new Intent(this, TroubleshootActivity.class)));
        findViewById(R.id.settings).setOnClickListener(v -> {
            if (AccessibilityHandler.isAccessibilityServiceEnabled(MainActivity.this)) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Grant Accessibility Access")
                        .setMessage(R.string.accessibility_enable_alert_description)
                        .setPositiveButton("Grant Access", (dialogInterface, i) -> {
                            MainActivity.this.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                            dialogInterface.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                        .show();
        });
        findViewById(R.id.restart_tutorial).setOnClickListener(v -> {
            addTutorials(true);
            showWalkthroughSlider();
        });
        findViewById(R.id.rate_app).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()))));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        messageHandler = new MessageHandler(this);
        pages = new ArrayList<>();
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        needToDisplayInitialWalkthrough = !sharedPreferences.getBoolean(Constants.IS_TUTORIAL_SHOWN, false);
        if(!AccessibilityHandler.isAccessibilityServiceEnabled(this))
            pages.add(new WalkthroughSlider.PageContent(
                    needToDisplayInitialWalkthrough ? R.string.accessibility_requirement_description_first_time : R.string.accessibility_requirement_description,
                    needToDisplayInitialWalkthrough ? "Accessibility Service Required" : "Enable Accessibility Service"
            )
                    .setButtonText(needToDisplayInitialWalkthrough ? "Grant Accessibility Access" : "Enable Accessibility", false));

        if(needToDisplayInitialWalkthrough) {
            addTutorials(false);
        }
        if (!pages.isEmpty())
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
        messageHandler.unregisterReceiver(Constants.TILE_ADDED_WHILE_TUTORIAL);
        super.onDestroy();
    }
}

