package com.alchemedy.pharasnap.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.alchemedy.pharasnap.activities.NoDisplayHelperActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ShortcutTileLauncher extends TileService {
    public static boolean resetTileState;
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Tile tile = getQsTile();
        resetTileState = false;
        tile.setState(FloatingWidget.isWidgetIsShowing ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
        updateTileStatusInStorage(FloatingWidget.isWidgetIsShowing);
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if(!sharedPreferences.getBoolean(Constants.IS_TUTORIAL_SHOWN, false)) {
            new MessageHandler(this).sendBroadcast(new Intent(Constants.TILE_ADDED_WHILE_TUTORIAL));
        }
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        updateTileStatusInStorage(false);
    }


    int tileState;
    private boolean isAccessibilityServiceRunning;
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();

        tileState = tile.getState();
        if (tileState == Tile.STATE_ACTIVE) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
            updateTileStatusInStorage(false);
            resetTileState = false;
        } else {
            if (resetTileState) {
                resetTileState = false;
                return;
            }
            isAccessibilityServiceRunning = AccessibilityHandler.isAccessibilityServiceEnabled(this);
            if (isAccessibilityServiceRunning) {
                tile.setState(Tile.STATE_ACTIVE);
                updateTileStatusInStorage(true);
                tile.updateTile();
            }
        }
    }

    private void updateTileStatusInStorage(boolean isActive) {
        SharedPreferences.Editor sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE).edit();
        if (isActive)
            sharedPreferences.putBoolean(Constants.TILE_ACTIVE_KEY, true);
        else
            sharedPreferences.remove(Constants.TILE_ACTIVE_KEY);
        sharedPreferences.apply();
    }

    @Override
    public void onClick() {
        if (tileState == Tile.STATE_ACTIVE) {
            new MessageHandler(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.STOP_WIDGET, true)
                            .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
        } else {
            if (!isAccessibilityServiceRunning) {
                startActivityAndCollapse(new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.IS_KNOWN_ACCESSIBILITY_DISABLED, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                startActivityAndCollapse(new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.LAUNCH_SERVICE_WITHOUT_CHECK, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
