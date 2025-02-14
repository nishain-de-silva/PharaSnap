package com.ndds.lettersnap.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ndds.lettersnap.activities.NoDisplayHelperActivity;
import com.ndds.lettersnap.helper.Constants;
import com.ndds.lettersnap.utils.AccessibilityHandler;

public class ShortcutTileLauncher extends TileService {
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Tile tile = getQsTile();
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
        updateTileStatusInStorage(false);
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        updateTileStatusInStorage(false);
    }

    private void updateTileStatusInStorage(boolean isActive) {
        SharedPreferences.Editor sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE).edit();
        if (isActive)
            sharedPreferences.putBoolean(Constants.TILE_ACTIVE_KEY, true);
        else
            sharedPreferences.remove(Constants.TILE_ACTIVE_KEY);
        sharedPreferences.apply();
    }

    int tileState;
    private boolean isAccessibilityServiceRunning;
    private boolean canSystemDraw = false;
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();

        tileState = tile.getState();
        if (tileState == Tile.STATE_ACTIVE) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
            updateTileStatusInStorage(false);
        } else {
            isAccessibilityServiceRunning = AccessibilityHandler.isAccessibilityServiceEnabled(this);
            canSystemDraw = Settings.canDrawOverlays(this);
            if (isAccessibilityServiceRunning && canSystemDraw) {
                tile.setState(Tile.STATE_ACTIVE);
                updateTileStatusInStorage(true);
                tile.updateTile();
            }
        }
    }

    @Override
    public void onClick() {
        if (tileState == Tile.STATE_ACTIVE) {
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.STOP_WIDGET, true)
                            .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
        } else {
            if (!canSystemDraw) {
                Toast.makeText(this, "Please provide overlay permission", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(intent);
                return;
            }
            if (isAccessibilityServiceRunning) {
                LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                                .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
                startActivityAndCollapse(new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.IGNORE_SERVICE_LAUNCH, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(intent);
                Toast.makeText(this, "Please turn on accessibility", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
