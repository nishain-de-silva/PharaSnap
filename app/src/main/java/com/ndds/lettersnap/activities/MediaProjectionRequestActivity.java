package com.ndds.lettersnap.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ndds.lettersnap.R;

public class MediaProjectionRequestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent broadcastIntent = new Intent("MEDIA_PROJECTION_DATA");
                    int resultCode = result.getResultCode();
                    broadcastIntent.putExtra("resultCode", resultCode);
                    if (resultCode == RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        Log.d("debug", "sending back the data");
                        broadcastIntent.putExtra("data", data);
                    }
                    LocalBroadcastManager.getInstance(MediaProjectionRequestActivity.this)
                            .sendBroadcast(broadcastIntent);
                    finish();
                });
        someActivityResultLauncher.launch(getIntent().getParcelableExtra("screenCaptureIntent"));
    }
}
