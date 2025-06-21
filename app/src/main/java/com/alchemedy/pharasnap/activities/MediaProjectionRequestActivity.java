package com.alchemedy.pharasnap.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.FloatingWidget;

public class MediaProjectionRequestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityResultLauncher<Intent> mediaProjectionRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent broadcastIntent = new Intent(Constants.MEDIA_PROJECTION_DATA);
                    int resultCode = result.getResultCode();
                    broadcastIntent.putExtra("resultCode", resultCode);
                    if (resultCode == RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        broadcastIntent.putExtra("data", data);
                    } else {
                        int mode = getIntent().getIntExtra(Constants.CURRENT_CAPTURE_MODE, FloatingWidget.Mode.TEXT);
                        Toast.makeText(this, mode == FloatingWidget.Mode.TEXT ?
                                "Screen recording is needed to capture text on images"
                                : "Screen recording is needed capture cropped images"
                                , Toast.LENGTH_SHORT).show();
                    }
                    new MessageHandler(MediaProjectionRequestActivity.this)
                            .sendBroadcast(broadcastIntent);
                    finish();
                });

        if (getIntent().hasExtra(Constants.REQUEST_STORAGE_PERMISSION)) {
            ActivityResultLauncher<String> storagePermissionRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted)
                    mediaProjectionRequestLauncher.launch(getIntent().getParcelableExtra(Constants.SCREEN_CAPTURE_INTENT));
                else {
                    Toast.makeText(this, "Storage Permission is required to screenshots in your device", Toast.LENGTH_SHORT).show();
                    Intent broadcastIntent = new Intent(Constants.MEDIA_PROJECTION_DATA);
                    broadcastIntent.putExtra("resultCode", RESULT_CANCELED);
                    new MessageHandler(MediaProjectionRequestActivity.this)
                            .sendBroadcast(broadcastIntent);
                    finish();
                }
            });
            new AlertDialog.Builder(this).setTitle("Grant Storage Access")
                    .setMessage("Grant media storage access to store cropped screenshot in your device")
                    .setCancelable(false)
                    .setPositiveButton("Grant Access", (dialogInterface, i) -> {
                        dialogInterface.dismiss();
                        storagePermissionRequestLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }).show();
        } else
            mediaProjectionRequestLauncher.launch(getIntent().getParcelableExtra(Constants.SCREEN_CAPTURE_INTENT));
    }
}
