package com.alchemedy.pharasnap.widgets;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.alchemedy.pharasnap.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

public class BrowserModal extends FrameLayout {
    private WebView webView;

    public BrowserModal(@NonNull Context context) {
        super(context);
    }

    public BrowserModal(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserModal(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void animateTransition(boolean isEntry) {
        View mainContainer = getChildAt(0);
        float offset = ((View) getParent()).getHeight() * 0.6f;

        ValueAnimator animator = ValueAnimator.ofFloat(isEntry ? offset : 0f, isEntry ? 0f : offset)
                .setDuration(300);
                        animator.addUpdateListener(valueAnimator -> {
                            mainContainer.setTranslationY((Float) valueAnimator.getAnimatedValue());
                            if (!isEntry && valueAnimator.getAnimatedFraction() == 1) {
                                webView.destroy();
                                ((CardView) findViewById(R.id.browser_container)).removeView(webView);
                                webView = null;
                                setVisibility(GONE);
                            }
                        });
                        animator.start();
    }

    private void handleClose() {
        animateTransition(false);
    }
    @SuppressLint("SetJavaScriptEnabled")
    public void show(String selectedText) {
        webView = new WebView(getContext());
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        ((CardView) findViewById(R.id.browser_container)).addView(webView, 0);
        webView.getSettings().setJavaScriptEnabled(true);
        findViewById(R.id.no_network_indicator).setVisibility(GONE);
        findViewById(R.id.browser_loader).setVisibility(GONE);
        findViewById(R.id.page_reload).setOnClickListener((v) -> {
            findViewById(R.id.no_network_indicator).setVisibility(GONE);
            webView.setVisibility(VISIBLE);
            webView.reload();
        });
        // backdrop click dismiss..
        setOnClickListener((v) -> handleClose());
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                findViewById(R.id.browser_loader).setVisibility(VISIBLE);
                ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo != null) {
                    if (!activeNetworkInfo.isConnectedOrConnecting()) {
                        findViewById(R.id.no_network_indicator).setVisibility(VISIBLE);
                        findViewById(R.id.browser_loader).setVisibility(GONE);
                        webView.setVisibility(GONE);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                findViewById(R.id.browser_loader).setVisibility(GONE);
            }
        };
        webView.setWebViewClient(webViewClient);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
        }
        setPadding(0, (int) (((View) getParent()).getHeight() * 0.4f), 0, 0);
        animateTransition(true);
        setVisibility(VISIBLE);
        try {
            String url = "https://www.google.com/search?q="+ URLEncoder.encode(selectedText, "UTF-8");
            webView.loadUrl(url);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
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
