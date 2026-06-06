package com.alchemedy.pharasnap.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;

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
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(attributes);

        messageHandler.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        }, Constants.WIDGET_IS_STOPPING);
        ViewGroup rootView = findViewById(R.id.modal_backdrop);
        View modalContainer = findViewById(R.id.modal_container);
        modalContainer.setMinimumHeight(0);
        rootView.setOnClickListener(view -> {
            if (TutorialGuide.trigger(TutorialAction.RESTRICTED_ACTION_ON_TUTORIAL))
                return;

            ObjectAnimator animator = ObjectAnimator
                    .ofFloat(modalContainer, "translationY", 0, modalContainer.getHeight())
                    .setDuration(300);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    new MessageHandler(EditTextModalActivity.this).sendBroadcast(
                            new Intent(Constants.PAYLOAD_EDIT_TEXT)
                                    .putExtra(Constants.CLOSE_PARENT_MODAL, true));
                    finish();
                }
            });
            animator.start();
        });

        ViewGroup modalContentContainer = findViewById(R.id.modal_content);
        ((TextView) findViewById(R.id.modal_back_title)).setText(R.string.change_text_modal_title);
        findViewById(R.id.modal_back).setOnClickListener(v -> onBackPressed());
        ViewGroup modalContent = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.edit_text_content, null);
        editText = modalContent.findViewById(R.id.changed_text);
        modalContent.findViewById(R.id.save_text_changes).setOnClickListener((v) -> {
            if (TutorialGuide.trigger(TutorialAction.MODAL_CLOSE))
                return;
            new MessageHandler(this).sendBroadcast(
                    new Intent(Constants.PAYLOAD_EDIT_TEXT)
                            .putExtra(Constants.PAYLOAD_EDIT_TEXT, editText.getText().toString())
            );
            finish();
        });
        editText.setText(getIntent().getStringExtra(Constants.PAYLOAD_EDIT_TEXT));

        modalContent.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                modalContent.removeOnLayoutChangeListener(this);
                TutorialGuide.changePrimaryOverlay(rootView, true);
                rootView.getViewTreeObserver().addOnGlobalLayoutListener(TutorialGuide::relayout);
            }
        });
        modalContentContainer.addView(modalContent);
        editText.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (TutorialGuide.trigger(TutorialAction.MODAL_CLOSE))
            return;
        messageHandler.sendBroadcast(
                new Intent(Constants.PAYLOAD_EDIT_TEXT)
        );
        finish();
    }

    @Override
    protected void onDestroy() {
        messageHandler.unregisterReceiver(Constants.PAYLOAD_EDIT_TEXT);
        messageHandler.unregisterReceiver(Constants.WIDGET_IS_STOPPING);
        super.onDestroy();
    }
}
