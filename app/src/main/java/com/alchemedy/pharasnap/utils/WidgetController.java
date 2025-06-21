package com.alchemedy.pharasnap.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.helper.WidgetLocationCoordinate;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;

public class WidgetController {
    public static void launchWidget(Context context, boolean showAccessibilityPrompt, boolean isKnownAccessibilityDisabled) {
        if (isKnownAccessibilityDisabled || !AccessibilityHandler.isAccessibilityServiceEnabled(context)) {
            NodeExplorerAccessibilityService.startWidgetAfterAccessibilityLaunch = true;
            if (showAccessibilityPrompt)
                new AlertDialog.Builder(context)
                        .setTitle("Grant Accessibility Access")
                                .setMessage(R.string.accessibility_enable_alert_description)
                        .setPositiveButton("Grant Access", (dialogInterface, i) -> {
                            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                            dialogInterface.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                        .show();
            else {
                Toast.makeText(context, "Please enable the accessibility service", Toast.LENGTH_SHORT).show();
                context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        } else {
            new MessageHandler(context)
                    .sendBroadcast(new Intent(Constants.ACCESSIBILITY_SERVICE));
        }
    }
     class ButtonPosition {
        View button;
        CoordinateF position;
        ButtonPosition(View v) {
            this.button = v;
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
    private boolean isWidgetLeftOriented;
    private final ViewGroup buttonContainer;
    private ButtonPosition[] buttonPositions;
    private static final int duration = 150;
    private int lastKnownDownLevelY;

    public WidgetController(Context context, WindowManager windowManager, WindowManager.LayoutParams params, View overlayView) {
        this.overlayView = overlayView;
        this.windowManager = windowManager;
        this.params = params;
        this.context = context;
        enableButton = overlayView.findViewById(R.id.toggleCollapse);
        buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);
    }

    public void prepareInitialState(boolean shouldExpand, WidgetLocationCoordinate widgetLocationCoordinate) {
        if (!shouldExpand)
            overlayView.findViewById(R.id.text_hint).setVisibility(View.GONE);
        enableButton.setImageResource(shouldExpand ? R.drawable.collapse : R.drawable.copy);
        enableButton.setBackgroundResource(shouldExpand ? R.drawable.yellow_color_circle : R.drawable.primary_blue_circle);

        ViewGroup buttonContainer = overlayView.findViewById(R.id.buttonContainer);
        buttonPositions = new ButtonPosition[buttonContainer.getChildCount() - 1];
        for (int i = 0; i < buttonContainer.getChildCount() - 1; i++) {
            buttonPositions[i] = new ButtonPosition(buttonContainer.getChildAt(i));
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
        isWidgetLeftOriented = sharedPreferences.getBoolean(Constants.IS_WIDGET_LEFT_ORIENTED, false);

        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);

        if (shouldExpand) {
            enableButton.isExpanded = true;
            rootOverlay.enableTouchListener();
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            buttonContainer.setTranslationY(widgetLocationCoordinate.y);
        } else {
            params.y = widgetLocationCoordinate.y;
        }

        configureWidgetOrientation(shouldExpand, false);
    }

    public void hotReloadWidgetOrientation(boolean isWidgetLeftOriented) {
        if (isWidgetLeftOriented == this.isWidgetLeftOriented) return;
        this.isWidgetLeftOriented = isWidgetLeftOriented;
        configureWidgetOrientation(enableButton.isExpanded, true);
    }

    public void configureWidgetOrientation(boolean shouldExpand, boolean shouldUpdateParams) {
        enableButton.isLeftOriented = isWidgetLeftOriented;
        Resources resources = context.getResources();
        int startAngle = 0;
        int sweepAngle = (180 - (startAngle * 2));

        int radius = resources.getDimensionPixelSize(R.dimen.button_size) + resources.getDimensionPixelSize(R.dimen.space_between_buttons);
        int sign = isWidgetLeftOriented ? 1 : -1;
        int unitAngle = sweepAngle / (buttonPositions.length - 1);

        for (int index = 0; index < buttonPositions.length; index++) {
            ButtonPosition buttonPosition = buttonPositions[index];
            int currentAngle = startAngle + (index * unitAngle);
            FrameLayout.LayoutParams buttonLayoutParam = (FrameLayout.LayoutParams) buttonPosition.button.getLayoutParams();
            buttonLayoutParam.gravity = Gravity.CENTER_VERTICAL | (isWidgetLeftOriented ? Gravity.START : Gravity.END);
            buttonPosition.button.setLayoutParams(buttonLayoutParam);
                buttonPosition.setPosition(
                        (int) (sign * Math.sin(Math.toRadians(currentAngle)) * radius),
                        (int) -(Math.cos(Math.toRadians(currentAngle)) * radius)
                );
            if (shouldExpand) {
                buttonPosition.button.setTranslationX(buttonPosition.position.x);
                buttonPosition.button.setTranslationY(buttonPosition.position.y);
            } else
                buttonPosition.button.setVisibility(View.GONE);
        }

        FrameLayout.LayoutParams enableButtonParams = (FrameLayout.LayoutParams) enableButton.getLayoutParams();
        enableButtonParams.gravity = Gravity.CENTER_VERTICAL | (isWidgetLeftOriented ? Gravity.START : Gravity.END);
        enableButton.setLayoutParams(enableButtonParams);
        buttonContainerSize = new Size(
                (int) (resources.getDimensionPixelSize(R.dimen.button_size) + Math.abs(buttonPositions[2].position.x)),
                (int) (resources.getDimensionPixelSize(R.dimen.button_size) + Math.abs(buttonPositions[0].position.y)
                        + Math.abs(buttonPositions[buttonPositions.length - 1].position.y))
        );

        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        buttonContainerLayoutParams.gravity = (isWidgetLeftOriented ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
        if (shouldExpand) {
            buttonContainerLayoutParams.height = buttonContainerSize.getHeight();
            buttonContainerLayoutParams.width = buttonContainerSize.getWidth();
            configureOverlayDimensions(buttonContainerLayoutParams, true, false, false);
            buttonContainer.setLayoutParams(buttonContainerLayoutParams);
        } else {
            params.gravity = Gravity.CENTER_VERTICAL | (isWidgetLeftOriented ? Gravity.START : Gravity.END);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // if initially if on landscape while navigation bar is on right side
                Insets navigationBarInset = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars());
                if ((isWidgetLeftOriented && navigationBarInset.left > 0) || (!isWidgetLeftOriented && navigationBarInset.right > 0)) {
                    landscapeNavigationBarOffset = Math.max(navigationBarInset.left, navigationBarInset.right);
                    params.x = landscapeNavigationBarOffset;
                }
            }
            if (shouldUpdateParams)
                windowManager.updateViewLayout(overlayView, params);
        }
    }

    public void updateLastDownCoordinate() {
        if (enableButton.isExpanded) {
            lastKnownDownLevelY = (int) buttonContainer.getTranslationY();
        } else {
            lastKnownDownLevelY = params.y;
        }
    }

    public void saveWidgetLocation(SharedPreferences sharedPreferences) {
        WidgetLocationCoordinate widgetLocation = new WidgetLocationCoordinate(lastKnownDownLevelY, context);
        if (widgetLocation.isSameAsDefault()) {
            sharedPreferences.edit().remove(Constants.WIDGET_LAST_LOCATION_INFO).apply();
        } else {
            sharedPreferences.edit().putString(Constants.WIDGET_LAST_LOCATION_INFO, widgetLocation.toJSONString()).apply();
        }
    }
    public void configureOverlayDimensions(FrameLayout.LayoutParams buttonContainerParams, boolean isExpanded, boolean shouldResetPosition, boolean shouldUpdateLayout) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets navigationBarInsets = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars());
            boolean isNavigationBarPlacedOnVerticalEdge = Math.max(navigationBarInsets.bottom, navigationBarInsets.top) > 0;
            if (isExpanded) {
                if (isNavigationBarPlacedOnVerticalEdge) {
                    int navigationBarSize = Math.max(navigationBarInsets.bottom, navigationBarInsets.top);
                    params.height = windowManager.getCurrentWindowMetrics().getBounds().height() - navigationBarSize;
                    if (navigationBarInsets.bottom > 0)
                        buttonContainerParams.topMargin = navigationBarSize / 2;
                    else
                        buttonContainerParams.bottomMargin = navigationBarSize / 2;
                    params.width = WindowManager.LayoutParams.MATCH_PARENT;
                    if (shouldResetPosition) {
                        buttonContainer.setTranslationY(0);
                        buttonContainer.setTranslationX(0);
                    }
                    landscapeNavigationBarOffset = 0;
                } else {
                    int navigationBarSize = Math.max(navigationBarInsets.left, navigationBarInsets.right);
                    params.width = windowManager.getCurrentWindowMetrics().getBounds().width() - navigationBarSize;
                    params.height = WindowManager.LayoutParams.MATCH_PARENT;
                    buttonContainerParams.topMargin = 0;
                    buttonContainerParams.bottomMargin = 0;
                    if (shouldResetPosition) {
                        Insets horizontalCutoutInset = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.displayCutout());
                        if (navigationBarInsets.right == 0 && !isWidgetLeftOriented) {
                            buttonContainer.setTranslationX(-horizontalCutoutInset.right);
                        } if (navigationBarInsets.left == 0 && isWidgetLeftOriented)
                            buttonContainer.setTranslationX(horizontalCutoutInset.left);
                        else
                            buttonContainer.setTranslationY(0);
                    }
                    landscapeNavigationBarOffset = isWidgetLeftOriented ? navigationBarInsets.left : navigationBarInsets.right;
                }
                if (shouldUpdateLayout)
                    buttonContainer.setLayoutParams(buttonContainerParams);
            } else {
                if (shouldResetPosition) {
                    if (isNavigationBarPlacedOnVerticalEdge || navigationBarInsets.right == 0) {
                        if (navigationBarInsets.right == 0) {
                            params.x = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.displayCutout()).right;
                        } else
                            params.x = 0;
                    } else {
                        params.x = navigationBarInsets.right;
                    }
                    params.y = 0;
                }
                landscapeNavigationBarOffset = Math.max(navigationBarInsets.right, 0);
            }
            if (isExpanded) {
                params.gravity = (navigationBarInsets.left > 0 ? Gravity.END : Gravity.START) | (navigationBarInsets.bottom > 0 ? Gravity.TOP : Gravity.BOTTOM);
            }
            if (shouldUpdateLayout) {
                windowManager.updateViewLayout(overlayView, params);
            }
        } else {
            params.gravity = Gravity.START | Gravity.TOP;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
        }
    }

    public void open() {
        enableButton.setImageResource(R.drawable.collapse);
        enableButton.setBackgroundResource(R.drawable.yellow_color_circle);
        for(ButtonPosition buttonPosition: buttonPositions)
            buttonPosition.button.setVisibility(View.VISIBLE);
        enableButton.isExpanded = true;
        rootOverlay.enableTouchListener();

        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();

        configureOverlayDimensions(buttonContainerLayoutParams, true, false, false);

        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        buttonContainer.setTranslationY(params.y);
        float proposedTranslationX = (isWidgetLeftOriented ? 1 : -1) * (params.x - landscapeNavigationBarOffset);

        params.y = 0;
        params.x = 0;
        windowManager.updateViewLayout(overlayView, params);
        buttonContainerLayoutParams.height = buttonContainerSize.getHeight();
        buttonContainerLayoutParams.width = buttonContainerSize.getWidth();
        buttonContainer.setLayoutParams(buttonContainerLayoutParams);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(duration);
        int screenWidth;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            screenWidth = windowManager.getCurrentWindowMetrics().getBounds().width();
        else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            screenWidth = displayMetrics.widthPixels;
        }
        if ((Math.abs(proposedTranslationX) + buttonContainerSize.getWidth()) > screenWidth)
            proposedTranslationX = (Math.signum(proposedTranslationX) * (screenWidth - buttonContainerSize.getWidth()));

        buttonContainer.setTranslationX(proposedTranslationX);
        animator.addUpdateListener(valueAnimator -> {
            double fraction = valueAnimator.getAnimatedFraction();
            for (ButtonPosition buttonPosition : buttonPositions) {
                buttonPosition.button.setTranslationX((float) (buttonPosition.position.x * fraction));
                buttonPosition.button.setTranslationY((float) (buttonPosition.position.y * fraction));
            }
        });
        animator.setDuration(duration);
        animator.start();
    }

    public void close() {
        rootOverlay.disableTouchListener();
        enableButton.isExpanded = false;
        FrameLayout.LayoutParams buttonContainerLayoutParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(duration);
        animator.addUpdateListener(valueAnimator -> {
            double fraction = 1 - valueAnimator.getAnimatedFraction();
            for (ButtonPosition buttonPosition: buttonPositions) {
                buttonPosition.button.setTranslationX((float) (buttonPosition.position.x * fraction));
                buttonPosition.button.setTranslationY((float) (buttonPosition.position.y * fraction));
            }
            if (fraction == 0) {
                enableButton.setImageResource(R.drawable.copy);
                enableButton.setBackgroundResource(R.drawable.primary_blue_circle);
                for(ButtonPosition buttonPosition: buttonPositions)
                    buttonPosition.button.setVisibility(View.GONE);
                buttonContainerLayoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
                buttonContainerLayoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    buttonContainerLayoutParams.topMargin = 0;
                    buttonContainerLayoutParams.bottomMargin = 0;
                }

                params.y = (int) buttonContainer.getTranslationY();
                params.x = (int) ((isWidgetLeftOriented ? 1 : -1) * buttonContainer.getTranslationX()) + landscapeNavigationBarOffset;
                buttonContainer.setTranslationY(0);
                buttonContainer.setTranslationX(0);
                buttonContainer.setLayoutParams(buttonContainerLayoutParams);
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity = Gravity.CENTER_VERTICAL | (isWidgetLeftOriented ? Gravity.START : Gravity.END);
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlayView, params);
            }
        });
        animator.start();
    }
}
