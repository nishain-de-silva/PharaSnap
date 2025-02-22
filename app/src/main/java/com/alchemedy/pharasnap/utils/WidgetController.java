package com.alchemedy.pharasnap.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.provider.Settings;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;

public class WidgetController {
    public static void launchWidget(Context context) {
        if(!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please provide overlay permission", Toast.LENGTH_SHORT).show();
            context.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        } else if (!AccessibilityHandler.isAccessibilityServiceEnabled(context)) {
            Toast.makeText(context, "Please enable the accessibility service", Toast.LENGTH_SHORT).show();
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }  else
            LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE));
    }
     class ButtonPosition {
        ImageButton button;
        CoordinateF position;
        ButtonPosition(ImageButton button, int index) {
            float horizontalOffsetToCenter = (buttonContainerSize.getWidth() / 2f + buttonSize / 2f) - buttonSize;
            float verticalEdgeOffset = buttonContainerSize.getHeight() / 2f - buttonSize * 0.5f;
            this.button = button;
            if (index == 0)
                position = new CoordinateF(-horizontalOffsetToCenter, -verticalEdgeOffset);
            else if (index == 1)
                position = new CoordinateF(-(buttonContainerSize.getWidth() - buttonSize), 0);
            else
                position = new CoordinateF(-horizontalOffsetToCenter, verticalEdgeOffset);
        }
    }
    private View overlayView;
    private WindowManager windowManager;
    private ImageButton toggleModeButton, stopButton, listButton;
    private EnableButton enableButton;
    public int landscapeNavigationBarOffset = 0;

    private CustomOverlayView rootOverlay;
    private WindowManager.LayoutParams params;
    private Size buttonContainerSize;
    private ViewGroup buttonContainer;
    private ButtonPosition[] buttonPositions;
    private static final int duration = 150;
    private int buttonSize;

    public WidgetController(Context context, WindowManager windowManager, WindowManager.LayoutParams params, View overlayView) {
        this.overlayView = overlayView;
        this.windowManager = windowManager;
        this.params = params;
        buttonSize = context.getResources().getDimensionPixelSize(R.dimen.button_size);
        enableButton = overlayView.findViewById(R.id.toggleCollapse);
        toggleModeButton = overlayView.findViewById(R.id.toggle);
        buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);
        stopButton = overlayView.findViewById(R.id.stop);
        listButton = overlayView.findViewById(R.id.list);
    }

    public void prepareInitialState() {
        overlayView.findViewById(R.id.text_hint).setVisibility(View.GONE);
        enableButton.setImageResource(R.drawable.copy);
        enableButton.setBackgroundResource(R.drawable.green_circle);

        ViewGroup buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        FrameLayout.LayoutParams buttonContainerParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        buttonContainerSize = new Size(buttonContainerParams.width, buttonContainerParams.height);

        buttonPositions = new ButtonPosition[] {
                new ButtonPosition(listButton, 0),
                new ButtonPosition(toggleModeButton, 1),
                new ButtonPosition(stopButton, 2),
        };
        for (ButtonPosition buttonPosition: buttonPositions) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) buttonPosition.button.getLayoutParams();

            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            buttonPosition.button.setVisibility(View.GONE);
            buttonPosition.button.setLayoutParams(params);
        }

        buttonContainerParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        buttonContainerParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        buttonContainer.setLayoutParams(buttonContainerParams);
        enableButton.switchToMovableState();
    }

    AnimatorSet playButtonTranslation(boolean isOpening) {
        ObjectAnimator[] animators = new ObjectAnimator[buttonPositions.length * 2];
        for (int i = 0; i < buttonPositions.length; i++) {
            ButtonPosition buttonPosition = buttonPositions[i];
            ObjectAnimator translationX = ObjectAnimator.ofFloat(buttonPosition.button, "translationX", (isOpening ? 1 : 0) * buttonPosition.position.x);
            translationX.setDuration(duration);
            animators[i * 2] = translationX;
            ObjectAnimator translationY = ObjectAnimator.ofFloat(buttonPosition.button, "translationY",(isOpening ? 1 : 0) * buttonPosition.position.y);
            translationY.setDuration(duration);
            animators[i * 2 + 1] = translationY;
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animators);
        return animatorSet;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public void determineOverlayExpandedDimension(FrameLayout.LayoutParams buttonContainerParams, boolean isExpanded, boolean didOrientationChanged) {
        int orientation = overlayView.getContext().getResources().getConfiguration().orientation;
        Insets navigationBarInsets = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars());
        if (isExpanded) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                int navigationBarSize = Math.max(navigationBarInsets.top, navigationBarInsets.bottom);
                params.height = windowManager.getCurrentWindowMetrics().getBounds().height() - navigationBarSize;
                if (navigationBarInsets.top > navigationBarInsets.bottom)
                    buttonContainerParams.bottomMargin = navigationBarSize / 2;
                else
                    buttonContainerParams.topMargin = navigationBarSize / 2;
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                if (didOrientationChanged) {
                    buttonContainer.setTranslationY(0);
                    buttonContainer.setTranslationX(0);
                    landscapeNavigationBarOffset = 0;
                }
            } else {
                int navigationBarSize = Math.max(navigationBarInsets.left, navigationBarInsets.right);
                params.width = windowManager.getCurrentWindowMetrics().getBounds().width() - navigationBarSize;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                buttonContainerParams.topMargin = 0;
                buttonContainerParams.bottomMargin = 0;
                if (didOrientationChanged) {
                    buttonContainer.setTranslationX(0);
                    buttonContainer.setTranslationY(0);
                    landscapeNavigationBarOffset = navigationBarSize;
                }
            }
            if (didOrientationChanged)
                buttonContainer.setLayoutParams(buttonContainerParams);
        } else if (didOrientationChanged) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                params.x = 0;
                landscapeNavigationBarOffset = 0;
            } else {
                int navigationBarSize = Math.max(navigationBarInsets.left, navigationBarInsets.right);
                params.x = navigationBarSize;
                landscapeNavigationBarOffset = navigationBarSize;
            }
            params.y = 0;
        }
        if (isExpanded) {
            params.gravity = Gravity.START | Gravity.TOP;
        }
        if (didOrientationChanged) {
            windowManager.updateViewLayout(overlayView, params);
        }
    }

    public void open() {
        enableButton.setImageResource(R.drawable.collapse);
        enableButton.setBackgroundResource(R.drawable.primary_color_circle);
        for(ButtonPosition buttonPosition: buttonPositions)
            buttonPosition.button.setVisibility(View.VISIBLE);
        enableButton.switchToStationaryState();
        rootOverlay.enableTouchListener();
        buttonContainer.setTranslationY(params.y);
        buttonContainer.setTranslationX(-params.x + landscapeNavigationBarOffset);
        params.y = 0;
        params.x = 0;
        params.gravity = Gravity.START | Gravity.TOP;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            determineOverlayExpandedDimension(buttonContainerLayoutParams, true, false);
        else {
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
        }
        windowManager.updateViewLayout(overlayView, params);
        buttonContainerLayoutParams.height = buttonContainerSize.getHeight();
        buttonContainerLayoutParams.width = buttonContainerSize.getWidth();
        buttonContainer.setLayoutParams(buttonContainerLayoutParams);
        playButtonTranslation(true).start();
    }

    public void close() {
        rootOverlay.disableTouchListener();
        enableButton.switchToMovableState();
        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) overlayView.findViewById(R.id.buttonContainer).getLayoutParams();
        AnimatorSet animatorSet = playButtonTranslation(false);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                enableButton.setImageResource(R.drawable.copy);
                enableButton.setBackgroundResource(R.drawable.green_circle);
                for(ButtonPosition buttonPosition: buttonPositions)
                    buttonPosition.button.setVisibility(View.GONE);
                buttonContainerLayoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
                buttonContainerLayoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    buttonContainerLayoutParams.topMargin = 0;
                    buttonContainerLayoutParams.bottomMargin = 0;
                }
                params.y = (int) buttonContainer.getTranslationY();
                params.x = (int) -buttonContainer.getTranslationX() + landscapeNavigationBarOffset;
                buttonContainer.setTranslationY(0);
                buttonContainer.setTranslationX(0);
                buttonContainer.setLayoutParams(buttonContainerLayoutParams);
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlayView, params);
            }
        });
        animatorSet.start();
    }
}
