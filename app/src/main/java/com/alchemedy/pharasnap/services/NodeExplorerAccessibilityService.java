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

import com.alchemedy.pharasnap.helper.LongPressButtonTriggerInfo;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    FloatingWidget floatingWidget;
    private MessageHandler messageHandler;
    public static boolean startWidgetAfterAccessibilityLaunch = false;
    private long previousStartTime;
    boolean isCapturingLongPressingButton;
    LongPressButtonTriggerInfo longPressButtonTrigger;

    public void onStopWidget() {
        if (floatingWidget != null) {
            floatingWidget.releaseResources();
            floatingWidget = null;
            FloatingWidget.isWidgetIsShowing = false;
            startWidgetAfterAccessibilityLaunch = false;
        }
    }

    private void showFloatingWidgetSafety(boolean shouldExpandWidget, boolean showTutorial) {
        if (floatingWidget == null)
            floatingWidget = new FloatingWidget(NodeExplorerAccessibilityService.this, shouldExpandWidget, showTutorial);
        else {
            if (showTutorial)
                floatingWidget.startTutorial();
            else
                Toast.makeText(NodeExplorerAccessibilityService.this, "Widget is already launched", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        String longPressButtonTriggerJSON = sharedPreferences.getString(Constants.NAVIGATION_LONG_PRESS.ACTIVE_BUTTON, null);
        AccessibilityServiceInfo serviceInfo = getServiceInfo();
        serviceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        if (longPressButtonTriggerJSON != null) {
            longPressButtonTrigger = new LongPressButtonTriggerInfo(longPressButtonTriggerJSON);
            serviceInfo.eventTypes |= AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
        }
        setServiceInfo(serviceInfo);

        messageHandler = new MessageHandler(this);

        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false)) {
            ShortcutTileLauncher.expectedChange = Tile.STATE_INACTIVE;
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
        }  else if (startWidgetAfterAccessibilityLaunch) {
            startWidgetAfterAccessibilityLaunch = false;
            floatingWidget = new FloatingWidget(this, false, false);
        }

        messageHandler
                .registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (handleMessagesFromSettings(intent)) return;
                        boolean shouldSkipNotify = intent.hasExtra(Constants.SKIP_NOTIFY_QUICK_TILE);
                        if (intent.hasExtra(Constants.STOP_WIDGET)) {
                            if (floatingWidget != null) {
                                if (shouldSkipNotify)
                                    floatingWidget.skipNotifyOnWidgetStop = true;
                                messageHandler.sendBroadcast(new Intent(Constants.WIDGET_IS_STOPPING));
                                onStopWidget();
                            }
                        } else
                            showFloatingWidgetSafety(shouldSkipNotify, intent.hasExtra(Constants.SHOULD_SHOW_TUTORIAL_GUIDE));
                    }
                }, Constants.ACCESSIBILITY_SERVICE);
    }

    private boolean handleMessagesFromSettings(Intent intent) {
        if (intent.hasExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION)) {
            int captureStatus = intent.getIntExtra(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION, 0);
            if (captureStatus == Constants.NAVIGATION_LONG_PRESS.START_CAPTURE) {
                AccessibilityServiceInfo updatedServiceInfo = getServiceInfo();
                if((updatedServiceInfo.eventTypes
                        & AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                    updatedServiceInfo.eventTypes |= AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
                    setServiceInfo(updatedServiceInfo);
                }
                isCapturingLongPressingButton = true;
            } else {
                if (captureStatus == Constants.NAVIGATION_LONG_PRESS.DISABLE_BUTTON) {
                    longPressButtonTrigger = null;
                    AccessibilityServiceInfo updatedServiceInfo = getServiceInfo();
                    updatedServiceInfo.eventTypes &= ~AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
                    setServiceInfo(updatedServiceInfo);
                }
                isCapturingLongPressingButton = false;
            }
            return true;
        }

        if (intent.hasExtra(Constants.IS_WIDGET_LEFT_ORIENTED) && floatingWidget != null) {
            floatingWidget.changeWidgetPositionOrientation(intent.getBooleanExtra(Constants.IS_WIDGET_LEFT_ORIENTED, false));
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        messageHandler.unregisterReceiver(Constants.ACCESSIBILITY_SERVICE);
        onStopWidget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        int eventType = accessibilityEvent.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if ((SystemClock.elapsedRealtime() - previousStartTime) > 3000) {
                AccessibilityServiceInfo serviceInfo = getServiceInfo();
                serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                setServiceInfo(serviceInfo);
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (isCapturingLongPressingButton) {
                messageHandler.sendBroadcast(new Intent(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION)
                        .putExtra(Constants.NAVIGATION_LONG_PRESS.NEW_BUTTON_PAYLOAD, ""));
                Toast.makeText(this, "Button assignment canceled", Toast.LENGTH_SHORT).show();
                isCapturingLongPressingButton = false;
            }
            AccessibilityServiceInfo serviceInfo = getServiceInfo();
            serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            setServiceInfo(serviceInfo);
            previousStartTime = SystemClock.elapsedRealtime();
        } else {
            if (isCapturingLongPressingButton) {
                CharSequence contentDescription = accessibilityEvent.getContentDescription();
                if (contentDescription != null && contentDescription.length() > 0) {
                    longPressButtonTrigger = new LongPressButtonTriggerInfo(contentDescription.toString(), accessibilityEvent.getPackageName().toString());
                    messageHandler.sendBroadcast(new Intent(Constants.NAVIGATION_LONG_PRESS.CAPTURE_SESSION)
                            .putExtra(Constants.NAVIGATION_LONG_PRESS.NEW_BUTTON_PAYLOAD, longPressButtonTrigger.toJSONString()));
                    isCapturingLongPressingButton = false;
                } else
                    Toast.makeText(this, "That does not seems to be a system navigation button", Toast.LENGTH_SHORT).show();
            } else if (
                    longPressButtonTrigger != null &&
                            longPressButtonTrigger.systemPackageName.contentEquals(accessibilityEvent.getPackageName())) {
                CharSequence contentDescription = accessibilityEvent.getContentDescription();
                if (contentDescription != null && longPressButtonTrigger.contentDescription.contentEquals(contentDescription)) {
                    showFloatingWidgetSafety(true, false);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
