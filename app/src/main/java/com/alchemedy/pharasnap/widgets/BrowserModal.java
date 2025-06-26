package com.alchemedy.pharasnap.widgets;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.alchemedy.pharasnap.R;

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
                            if (!isEntry && valueAnimator.getAnimatedFraction() == 1) {
                                webView.destroy();
                                rootView.removeView(modal);
                                modal = null;
                                webView = null;
                            }
                        });
                        animator.start();
    }

    private void handleClose() {
        animateTransition(false);
    }
    @SuppressLint("SetJavaScriptEnabled")
    public void show(String selectedText) {
        modal = (ViewGroup) LayoutInflater.from(rootView.getContext()).inflate(R.layout.browser_modal, null);
        webView = modal.findViewById(R.id.webView);
       
        webView.getSettings().setJavaScriptEnabled(true);
        modal.findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
        modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
        modal.findViewById(R.id.page_reload).setOnClickListener((v) -> {
            modal.findViewById(R.id.no_network_indicator).setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });
        // backdrop click dismiss..
        modal.setOnClickListener((v) -> handleClose());
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                modal.findViewById(R.id.browser_loader).setVisibility(View.VISIBLE);
                ConnectivityManager connectivityManager = (ConnectivityManager) modal.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo != null) {
                    if (!activeNetworkInfo.isConnectedOrConnecting()) {
                        modal.findViewById(R.id.no_network_indicator).setVisibility(View.VISIBLE);
                        modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
                        webView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                modal.findViewById(R.id.browser_loader).setVisibility(View.GONE);
            }
        };
        webView.setWebViewClient(webViewClient);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
        }
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
