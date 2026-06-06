package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;

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
                if (intent.hasExtra(Constants.TUTORIAL_PLAYGROUND_STEP)) {
                    int step = intent.getIntExtra(Constants.TUTORIAL_PLAYGROUND_STEP, 0);
                    if (step == 1) {
                        findViewById(R.id.tutorial_hidden_text).setVisibility(View.VISIBLE);
                        TutorialGuide.registerForViewProvider(() -> findViewById(R.id.tutorial_poster));
                    } else if (step == 2) {
                        ((ScrollView) findViewById(R.id.tutorial_scrollview)).fullScroll(ScrollView.FOCUS_DOWN);
                    }
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
}
