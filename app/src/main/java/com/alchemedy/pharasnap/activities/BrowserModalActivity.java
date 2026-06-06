package com.alchemedy.pharasnap.activities;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class BrowserModalActivity extends AppCompatActivity {

    private MessageHandler messageHandler;
    private WebView webView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.browser_modal);
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
        ViewGroup rootView = findViewById(R.id.modal_backdrop);
        rootView.post(() -> animateTransition(true));
        rootView.setOnClickListener(view -> handleClose(true));

        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        findViewById(R.id.page_reload).setOnClickListener((v) -> {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting())
                return;
            findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });

        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting()) {
                    findViewById(R.id.no_network_indicator).setVisibility(View.VISIBLE);
                    findViewById(R.id.browser_loader).setVisibility(View.GONE);
                    webView.setVisibility(View.GONE);
                } else
                    findViewById(R.id.browser_loader).setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                findViewById(R.id.browser_loader).setVisibility(View.GONE);
            }
        };
        webView.setWebViewClient(webViewClient);

        String selectedText = getIntent().getStringExtra("selectedText");
        try {
            String url = "https://www.google.com/search?q="+ URLEncoder.encode(selectedText, "UTF-8");
            webView.loadUrl(url);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack())
            webView.goBack();
        else {
            handleClose(false);
        }
    }

    private void handleClose(boolean isBackdropClick) {
        if(TutorialGuide.trigger(TutorialAction.MODAL_CLOSE)) return;
        boolean shouldAnimateExit = isBackdropClick || !getIntent().getBooleanExtra("hasParentModal", false);
        if (shouldAnimateExit)
            animateTransition(false);
        else {
            messageHandler.sendBroadcast(new Intent(Constants.BROWSER_CLOSED));
            finish();
        }
    }

    private void animateTransition(boolean isEntry) {
        View mainContainer = findViewById(R.id.browser_container);
        ViewGroup rootView = findViewById(R.id.modal_backdrop);
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        float offset = isPortrait ? mainContainer.getHeight() : mainContainer.getWidth();

        ValueAnimator animator = ValueAnimator.ofFloat(isEntry ? offset : 0f, isEntry ? 0f : offset)
                .setDuration(300);
        animator.addUpdateListener(valueAnimator -> {
            mainContainer.setTranslationY((Float) valueAnimator.getAnimatedValue());
            if (valueAnimator.getAnimatedFraction() == 1) {
                if (isEntry)
                    TutorialGuide.changePrimaryOverlay(rootView, true);
                else {
                    messageHandler.sendBroadcast(
                            new Intent(Constants.BROWSER_CLOSED).putExtra(Constants.CLOSE_PARENT_MODAL, true)
                    );
                    finish();
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDestroy() {
        messageHandler.unregisterReceiver(Constants.BROWSER_CLOSED);
        messageHandler.unregisterReceiver(Constants.WIDGET_IS_STOPPING);
        super.onDestroy();
    }
}
