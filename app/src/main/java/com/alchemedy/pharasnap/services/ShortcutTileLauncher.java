package com.alchemedy.pharasnap.services;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import com.alchemedy.pharasnap.activities.NoDisplayHelperActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ShortcutTileLauncher extends TileService {
    public static int expectedChange = Tile.STATE_UNAVAILABLE;
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Tile tile = getQsTile();
        tile.setState(FloatingWidget.isWidgetIsShowing ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        expectedChange = Tile.STATE_UNAVAILABLE;
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

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (expectedChange != Tile.STATE_UNAVAILABLE) {
            Tile tile = getQsTile();
            int tileState = tile.getState();
            if (expectedChange == Tile.STATE_INACTIVE && tileState == Tile.STATE_ACTIVE) {
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
                updateTileStatusInStorage(false);
            } else if (expectedChange == Tile.STATE_ACTIVE && tileState == Tile.STATE_INACTIVE){
                tile.setState(Tile.STATE_ACTIVE);
                updateTileStatusInStorage(true);
                tile.updateTile();
            }
            expectedChange = Tile.STATE_UNAVAILABLE;
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
        Tile tile = getQsTile();
        if (tile.getState() == Tile.STATE_ACTIVE) {
            new MessageHandler(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.STOP_WIDGET, true)
                            .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
            tile.setState(Tile.STATE_INACTIVE);
            updateTileStatusInStorage(false);
            tile.updateTile();
        } else {
            Intent intent;
            if (!AccessibilityHandler.isAccessibilityServiceEnabled(this)) {
                intent = new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.IS_KNOWN_ACCESSIBILITY_DISABLED, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.LAUNCH_SERVICE_WITHOUT_CHECK, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                tile.setState(Tile.STATE_ACTIVE);
                updateTileStatusInStorage(true);
                tile.updateTile();
            }
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent
                        .getActivity(this, 100, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                );
            } else {
                startActivityAndCollapse(intent);
            }
        }
    }
}
