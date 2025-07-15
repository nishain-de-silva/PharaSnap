package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;

public class TutorialActivity extends ThemeActivity {
    MessageHandler messageHandler;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);
         messageHandler = new MessageHandler(this);
        messageHandler.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(Constants.SHOW_HIDDEN_TEXT_IN_TUTORIAL)) {
                    findViewById(R.id.tutorial_hidden_text).setVisibility(View.VISIBLE);
                } else if (intent.hasExtra(Constants.END_TUTORIAL))
                    finish();
            }
        }, Constants.TUTORIAL);
    }

    @Override
    public void finish() {
        messageHandler.unregisterReceiver(Constants.TUTORIAL);
        super.finish();
    }

    @Override
    public void onBackPressed() {

    }
}
