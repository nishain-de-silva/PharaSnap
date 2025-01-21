package com.ndds.lettersnap.services;

import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

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
        } else {
            isAccessibilityServiceRunning = AccessibilityHandler.isAccessibilityServiceEnabled(this);
            canSystemDraw = Settings.canDrawOverlays(this);
            if (isAccessibilityServiceRunning && canSystemDraw) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.updateTile();
            }
        }
    }

    @Override
    public void onClick() {
        if (tileState == Tile.STATE_ACTIVE) {
            startService(new Intent(this, OverlayService.class)
                    .putExtra("STOP_SERVICE", true)
                    .putExtra("SKIP_NOTIFY", true)
            );
        } else {
            if (!canSystemDraw) {
                Toast.makeText(this, "Please provide overlay permission", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(intent);
                return;
            }
            if (isAccessibilityServiceRunning) {
                startService(new Intent(this, OverlayService.class)
                        .putExtra("SKIP_NOTIFY", true)
                );
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
