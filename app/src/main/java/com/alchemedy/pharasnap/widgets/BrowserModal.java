package com.alchemedy.pharasnap.widgets;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class BrowserModal {
    private WebView webView;
    private final ViewGroup rootView;
    private ViewGroup modal;
    
    public BrowserModal(ViewGroup rootView) {
        this.rootView = rootView;
    }

    private void animateTransition(boolean isEntry) {
        View mainContainer = modal.getChildAt(0);
        float offset = rootView.getHeight() * 0.6f;

        ValueAnimator animator = ValueAnimator.ofFloat(isEntry ? offset : 0f, isEntry ? 0f : offset)
                .setDuration(300);
                        animator.addUpdateListener(valueAnimator -> {
                            mainContainer.setTranslationY((Float) valueAnimator.getAnimatedValue());
                            if (valueAnimator.getAnimatedFraction() == 1) {
                                if (!isEntry) {
                                    webView.destroy();
                                    rootView.removeView(modal);
                                    modal = null;
                                    webView = null;
                                }
                                TutorialGuide.trigger(isEntry ? TutorialAction.MODAL_OPENED : TutorialAction.MODAL_CLOSED);
                            }
                        });
                        animator.start();
    }

    private void handleClose() {
        if (TutorialGuide.trigger(TutorialAction.MODAL_CLOSE))
            return;
        animateTransition(false);
    }
    @SuppressLint("SetJavaScriptEnabled")
    public void show(String selectedText) {
        modal = (ViewGroup) LayoutInflater.from(rootView.getContext()).inflate(R.layout.browser_modal, null);
        webView = modal.findViewById(R.id.webView);
       
        webView.getSettings().setJavaScriptEnabled(true);
        modal.findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
        modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
        ConnectivityManager connectivityManager = (ConnectivityManager) modal.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        modal.findViewById(R.id.page_reload).setOnClickListener((v) -> {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting())
                return;
            modal.findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });
        // backdrop click dismiss..
        modal.setOnClickListener((v) -> handleClose());
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnectedOrConnecting()) {
                    modal.findViewById(R.id.no_network_indicator).setVisibility(View.VISIBLE);
                    modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
                    webView.setVisibility(View.GONE);
                } else
                    modal.findViewById(R.id.browser_loader).setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
            }
        };
        webView.setWebViewClient(webViewClient);
        modal.setPadding(0, (int) (rootView.getHeight() * 0.4f), 0, 0);
        rootView.addView(modal);
        animateTransition(true);

        try {
            String url = "https://www.google.com/search?q="+ URLEncoder.encode(selectedText, "UTF-8");
            webView.loadUrl(url);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeImmediately() {
        if (modal != null) {
            rootView.removeView(modal);
        }
    }

    public boolean close() {
        if (webView != null) {
            if (webView.canGoBack())
                webView.goBack();
            else
                handleClose();
            return true;
        }
        return false;
    }
}
