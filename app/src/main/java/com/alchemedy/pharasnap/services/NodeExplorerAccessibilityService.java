package com.alchemedy.pharasnap.services;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.service.quicksettings.Tile;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    FloatingWidget floatingWidget;
    private MessageHandler messageHandler;


    public void onStopWidget() {
        if (floatingWidget != null) {
            floatingWidget.releaseResources();
            floatingWidget = null;
            FloatingWidget.isWidgetIsShowing = false;
        }
    }

    private void showFloatingWidgetSafety(boolean shouldExpandWidget) {
        if (floatingWidget == null)
            floatingWidget = new FloatingWidget(NodeExplorerAccessibilityService.this, shouldExpandWidget);
        else
            Toast.makeText(NodeExplorerAccessibilityService.this, "Widget is already launched", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        messageHandler = new MessageHandler(this);
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ShortcutTileLauncher.expectedTileState = Tile.STATE_INACTIVE;
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
        }
        else if (sharedPreferences.getBoolean(Constants.START_WIDGET_AFTER_ACCESSIBILITY_LAUNCH, false)) {
            sharedPreferences.edit().remove(Constants.START_WIDGET_AFTER_ACCESSIBILITY_LAUNCH).apply();
            floatingWidget = new FloatingWidget(this, false);
        }

        messageHandler
                .registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        boolean shouldSkipNotify = intent.hasExtra(Constants.SKIP_NOTIFY_QUICK_TILE);
                        if (intent.hasExtra(Constants.STOP_WIDGET)) {
                            if (floatingWidget != null) {
                                if (shouldSkipNotify)
                                    floatingWidget.skipNotifyOnWidgetStop = true;
                                messageHandler.sendBroadcast(new Intent(Constants.WIDGET_IS_STOPPING));
                                onStopWidget();
                            }
                        } else
                            showFloatingWidgetSafety(shouldSkipNotify);
                    }
                }, Constants.ACCESSIBILITY_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        messageHandler.unregisterReceiver(Constants.ACCESSIBILITY_SERVICE);
        onStopWidget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

    @Override
    public void onInterrupt() {}
}
