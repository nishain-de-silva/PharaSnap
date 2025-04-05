package com.alchemedy.pharasnap.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;

public class MediaProjectionRequestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent broadcastIntent = new Intent(Constants.MEDIA_PROJECTION_DATA);
                    int resultCode = result.getResultCode();
                    broadcastIntent.putExtra("resultCode", resultCode);
                    if (resultCode == RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        broadcastIntent.putExtra("data", data);
                    } else
                        Toast.makeText(this, "Screen recording is needed to capture text on images", Toast.LENGTH_SHORT).show();
                    new MessageHandler(MediaProjectionRequestActivity.this)
                            .sendBroadcast(broadcastIntent);
                    finish();
                });
        someActivityResultLauncher.launch(getIntent().getParcelableExtra("screenCaptureIntent"));
    }
}
