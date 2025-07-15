package com.alchemedy.pharasnap.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;

public class EditTextModalActivity extends AppCompatActivity {
    private EditText editText;
    private MessageHandler messageHandler;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.modal);
        messageHandler = new MessageHandler(this);
        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
        attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().getDecorView().setPadding(0, 0, 0 ,0);
        getWindow().setAttributes(attributes);

        messageHandler.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        }, Constants.WIDGET_IS_STOPPING);

        findViewById(R.id.modal_backdrop).setOnClickListener(view -> {
            new MessageHandler(this).sendBroadcast(
                    new Intent(Constants.PAYLOAD_EDIT_TEXT)
                            .putExtra(Constants.SHOULD_CLOSE_MODAL, true));
                    finish();
        });

        findViewById(R.id.modal_container).setMinimumHeight(0);
        ViewGroup modalContentContainer = findViewById(R.id.modal_content);
        ((TextView) findViewById(R.id.modal_back_title)).setText(R.string.change_text_modal_title);
        findViewById(R.id.modal_back).setOnClickListener(v -> onBackPressed());
        ViewGroup modalContent = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.edit_text_content, null);
        editText = modalContent.findViewById(R.id.changed_text);
        editText.setText(getIntent().getStringExtra(Constants.PAYLOAD_EDIT_TEXT));
        modalContentContainer.addView(modalContent);
        editText.requestFocus();
    }

    @Override
    public void onBackPressed() {
        new MessageHandler(this).sendBroadcast(
                new Intent(Constants.PAYLOAD_EDIT_TEXT)
                        .putExtra(Constants.PAYLOAD_EDIT_TEXT, editText.getText().toString())
        );
        finish();
    }

    @Override
    protected void onDestroy() {
        messageHandler.unregisterReceiver(Constants.WIDGET_IS_STOPPING);
        super.onDestroy();
    }
}
