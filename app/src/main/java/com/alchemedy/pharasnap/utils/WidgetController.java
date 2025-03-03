package com.alchemedy.pharasnap.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;

public class WidgetController {
    public static void launchWidget(Context context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
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
        ButtonPosition(int id) {
            this.button = overlayView.findViewById(id);
        }

         public void setPosition(int translationX, int translationY) {
             this.position = new CoordinateF(translationX, translationY);
         }
     }
    private final View overlayView;
    private final Context context;
    private final WindowManager windowManager;
    private final EnableButton enableButton;
    public int landscapeNavigationBarOffset = 0;

    private final CustomOverlayView rootOverlay;
    private final WindowManager.LayoutParams params;
    private Size buttonContainerSize;
    private final ViewGroup buttonContainer;
    private ButtonPosition[] buttonPositions;
    private static final int duration = 150;

    public WidgetController(Context context, WindowManager windowManager, WindowManager.LayoutParams params, View overlayView) {
        this.overlayView = overlayView;
        this.windowManager = windowManager;
        this.params = params;
        this.context = context;
        enableButton = overlayView.findViewById(R.id.toggleCollapse);
        buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);
    }

    public void prepareInitialState() {
        overlayView.findViewById(R.id.text_hint).setVisibility(View.GONE);
        enableButton.setImageResource(R.drawable.copy);
        enableButton.setBackgroundResource(R.drawable.green_circle);

        Resources resources = context.getResources();
        buttonPositions = new ButtonPosition[] {
                new ButtonPosition(R.id.list),
                new ButtonPosition(R.id.toggle),
                new ButtonPosition(R.id.eraser),
                new ButtonPosition(R.id.stop),
        };

        int startAngle = 15;
        int sweepAngle = (180 - (startAngle * 2));
        int unitAngle = sweepAngle / (buttonPositions.length - 1);

        int radius = resources.getDimensionPixelSize(R.dimen.button_size) + resources.getDimensionPixelSize(R.dimen.space_between_buttons);
        for (int index = 0; index < buttonPositions.length; index++) {
            ButtonPosition buttonPosition = buttonPositions[index];
            int currentAngle = startAngle + (index * unitAngle);
            buttonPosition.setPosition(
                    (int) -(Math.sin(Math.toRadians(currentAngle)) * radius),
                    (int) -(Math.cos(Math.toRadians(currentAngle)) * radius)
            );

            buttonPosition.button.setVisibility(View.GONE);
        }
        buttonContainerSize = new Size(
                (int) (resources.getDimensionPixelSize(R.dimen.button_size) - buttonPositions[1].position.x),
                (int) (resources.getDimensionPixelSize(R.dimen.button_size)
                                        -buttonPositions[0].position.y
                                + buttonPositions[buttonPositions.length - 1].position.y)
        );
        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);

        enableButton.switchToMovableState();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public void configureOverlayDimensions(FrameLayout.LayoutParams buttonContainerParams, boolean isExpanded, boolean didOrientationChanged) {
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
        enableButton.setBackgroundResource(R.drawable.yellow_color_circle);
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
            configureOverlayDimensions(buttonContainerLayoutParams, true, false);
        else {
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
        }
        windowManager.updateViewLayout(overlayView, params);
        buttonContainerLayoutParams.height = buttonContainerSize.getHeight();
        buttonContainerLayoutParams.width = buttonContainerSize.getWidth();
        buttonContainer.setLayoutParams(buttonContainerLayoutParams);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(duration);
        animator.addUpdateListener(valueAnimator -> {
            double fraction = valueAnimator.getAnimatedFraction();
            for (int i = 0; i < buttonPositions.length; i++) {
                buttonPositions[i].button.setTranslationX((float) (buttonPositions[i].position.x * fraction));
                buttonPositions[i].button.setTranslationY((float) (buttonPositions[i].position.y * fraction));
            }
        });
        animator.setDuration(duration);
        animator.start();
    }

    public void close() {
        rootOverlay.disableTouchListener();
        enableButton.switchToMovableState();
        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) overlayView.findViewById(R.id.buttonContainer).getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(duration);
        animator.addUpdateListener(valueAnimator -> {
            double fraction = 1 - valueAnimator.getAnimatedFraction();
            for (int i = 0; i < buttonPositions.length; i++) {
                buttonPositions[i].button.setTranslationX((float) (buttonPositions[i].position.x * fraction));
                buttonPositions[i].button.setTranslationY((float) (buttonPositions[i].position.y * fraction));
            }
            if (fraction == 0) {
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
        animator.start();
    }
}
