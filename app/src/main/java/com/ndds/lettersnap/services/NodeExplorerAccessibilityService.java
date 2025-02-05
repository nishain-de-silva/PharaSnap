package com.ndds.lettersnap.services;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ndds.lettersnap.helper.Constants;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    private BroadcastReceiver tileMessageReceiver;
    private AccessibilityNodeInfo selectedNode;
    private int minArea;

    @Nullable
    private void isInsideElement(AccessibilityNodeInfo node, int coordinateX, int coordinateY) {
        if (node == null) return;
        Rect nodeBound = new Rect();
        node.getBoundsInScreen(nodeBound);
        if (nodeBound.contains(coordinateX, coordinateY)) {
            int area = nodeBound.height() * nodeBound.width();
            if (area < minArea || (area == minArea && !getText(node).isEmpty())) {
                selectedNode = node;
                minArea = area;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        }
        else if ((nodeBound.top == nodeBound.bottom) ) {
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        }
    }

    private String getText(AccessibilityNodeInfo node) {
        CharSequence nodeText = node.getText();
        if (nodeText != null) {
            return nodeText.toString();
        }

        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            return contentDescription.toString();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            CharSequence hintText = node.getHintText();
            if (hintText != null) {
                return hintText.toString();
            }
        }
        return "";
    }

    private void freeSearch(AccessibilityNodeInfo node) {
        Rect outBounds = new Rect();
        node.getBoundsInScreen(outBounds);
        String text = getText(node);
        if (!text.isEmpty())
            Log.d("text", text);
        for (int i = 0; i < node.getChildCount(); i++) {
            freeSearch(node.getChild(i));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if (sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));

        tileMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                float x = intent.getFloatExtra("x", 0);
                float y = intent.getFloatExtra("y", 0);
                selectedNode = null;
                minArea = Integer.MAX_VALUE;
                AccessibilityNodeInfo root = getRootInActiveWindow();
                isInsideElement(root, (int) x, (int) y);
//                freeSearch(root);
                String extractedText = selectedNode == null ? "" : getText(selectedNode).trim();
                Rect bounds = new Rect();
                if (selectedNode != null)
                    selectedNode.getBoundsInScreen(bounds);
                Intent broadcastIntent = new Intent(Constants.BOUND_RESULT)
                        .putExtra(Constants.Result.BOUND, bounds);
                boolean isImageRecognitionEnabled = intent.getBooleanExtra(Constants.IMAGE_RECOGNITION_ENABLED, false);
                if (!isImageRecognitionEnabled) broadcastIntent.putExtra(Constants.Result.TEXT, extractedText);

                LocalBroadcastManager.getInstance(NodeExplorerAccessibilityService.this)
                        .sendBroadcast(broadcastIntent);
                }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(tileMessageReceiver, new IntentFilter(Constants.TAPPED_COORDINATE));
    }

    @Override
    public void onDestroy() {
        if (tileMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tileMessageReceiver);
            tileMessageReceiver = null;
        }
        super.onDestroy();
        removeFloatingWidget();
    }

    private void removeFloatingWidget() {
        stopService(new Intent(this, OverlayService.class));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

    @Override
    public void onInterrupt() {
        removeFloatingWidget();
    }
}
