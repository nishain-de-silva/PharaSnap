package com.alchemedy.pharasnap.services;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.utils.FloatingWidget;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    private BroadcastReceiver startCommandReceiver;
    FloatingWidget floatingWidget;
    private long previousStartTime;

    public void onStopWidget() {
        if (floatingWidget != null) {
            floatingWidget.releaseResources();
            floatingWidget = null;
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
        AccessibilityServiceInfo serviceInfo = getServiceInfo();
        serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        previousStartTime = SystemClock.elapsedRealtime();
        setServiceInfo(serviceInfo);
        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
        else if (sharedPreferences.getBoolean(Constants.START_WIDGET_AFTER_ACCESSIBILITY_LAUNCH, false)) {
            sharedPreferences.edit().remove(Constants.START_WIDGET_AFTER_ACCESSIBILITY_LAUNCH).apply();
            floatingWidget = new FloatingWidget(this, false);
        }

        startCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean shouldSkipNotify = intent.hasExtra(Constants.SKIP_NOTIFY_QUICK_TILE);
                if (intent.hasExtra(Constants.STOP_WIDGET)) {
                    if (floatingWidget != null) {
                        if (shouldSkipNotify)
                            floatingWidget.skipNotifyOnWidgetStop = true;
                        onStopWidget();
                    }
                } else
                    showFloatingWidgetSafety(shouldSkipNotify);
                }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(startCommandReceiver, new IntentFilter(Constants.ACCESSIBILITY_SERVICE));
    }

    @Override
    public void onDestroy() {
        if (startCommandReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(startCommandReceiver);
            startCommandReceiver = null;
        }
        super.onDestroy();
        onStopWidget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        if (accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if ((SystemClock.elapsedRealtime() - previousStartTime) > 3000) {
                AccessibilityServiceInfo serviceInfo = getServiceInfo();
                serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                setServiceInfo(serviceInfo);
            }
        } else
        {
            AccessibilityServiceInfo serviceInfo = getServiceInfo();
            serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            setServiceInfo(serviceInfo);
            previousStartTime = SystemClock.elapsedRealtime();
        }
    }

    @Override
    public void onInterrupt() {}
}
