package com.alchemedy.pharasnap.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.activities.BrowserModalActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;

public class BrowserClient {
    private final View overlayView;
    private final ViewGroup rootContainer;

    public BrowserClient(ViewGroup overlayView, ViewGroup rootContainer) {
        this.overlayView = overlayView;
        this.rootContainer = rootContainer;
    }

    public void show(String text, @Nullable Modal parentModal) {
        if (TutorialGuide.trigger(TutorialAction.BROWSER_OPENING))
            return;
        Context context = overlayView.getContext();
         new MessageHandler(context).registerReceiverOnce(new BroadcastReceiver() {
             @Override
             public void onReceive(Context context, Intent intent) {
                 overlayView.setVisibility(View.VISIBLE);
                 if (parentModal != null && intent.getBooleanExtra(Constants.CLOSE_PARENT_MODAL, false)
                         && !TutorialGuide.isTutorialRunning()) {
                     parentModal.closeImmediately();
                 }
                 TutorialGuide.changePrimaryOverlay(rootContainer, false);
             }
         }, Constants.BROWSER_CLOSED);
        overlayView.setVisibility(View.GONE);
        context.startActivity(new Intent(context, BrowserModalActivity.class)
                .putExtra("selectedText", text)
                        .putExtra("hasParentModal", parentModal != null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
