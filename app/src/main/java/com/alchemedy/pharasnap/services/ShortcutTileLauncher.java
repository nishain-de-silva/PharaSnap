package com.alchemedy.pharasnap.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import com.alchemedy.pharasnap.activities.NoDisplayHelperActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.AccessibilityHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;
import com.alchemedy.pharasnap.utils.WidgetController;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ShortcutTileLauncher extends TileService {
    public static int expectedTileState = Tile.STATE_UNAVAILABLE;
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Tile tile = getQsTile();
        expectedTileState = Tile.STATE_UNAVAILABLE;
        tile.setState(FloatingWidget.isWidgetIsShowing ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if(!sharedPreferences.getBoolean(Constants.IS_TUTORIAL_SHOWN, false)) {
            new MessageHandler(this).sendBroadcast(new Intent(Constants.TILE_ADDED_WHILE_TUTORIAL));
        }
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }


    int tileState;
    private boolean isAccessibilityServiceRunning;
    private boolean canSystemDraw = false;
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();

        tileState = tile.getState();
        if (expectedTileState == Tile.STATE_UNAVAILABLE) {
            if (tileState == Tile.STATE_ACTIVE) {
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
            } else {
                isAccessibilityServiceRunning = AccessibilityHandler.isAccessibilityServiceEnabled(this);
                canSystemDraw = Settings.canDrawOverlays(this);
                if (isAccessibilityServiceRunning && canSystemDraw) {
                    tile.setState(Tile.STATE_ACTIVE);
                    tile.updateTile();
                }
            }
        } else {
            if (expectedTileState == Tile.STATE_INACTIVE && tileState == Tile.STATE_ACTIVE) {
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
            } else if (expectedTileState == Tile.STATE_ACTIVE && tileState == Tile.STATE_INACTIVE) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.updateTile();
            }
            expectedTileState = Tile.STATE_UNAVAILABLE;
        }
    }

    @Override
    public void onClick() {
        if (tileState == Tile.STATE_ACTIVE) {
            new MessageHandler(this)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE)
                            .putExtra(Constants.STOP_WIDGET, true)
                            .putExtra(Constants.SKIP_NOTIFY_QUICK_TILE, true));
        } else {
            if (!canSystemDraw || !isAccessibilityServiceRunning) {
                startActivityAndCollapse(new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.KNOWN_DISABLED_PERMISSIONS_KEY, !canSystemDraw ? WidgetController.OVERLAY_PERMISSION_DISABLED : WidgetController.ACCESSIBILITY_DISABLED)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                startActivityAndCollapse(new Intent(this, NoDisplayHelperActivity.class)
                        .putExtra(Constants.LAUNCH_SERVICE_WITHOUT_CHECK, true)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
