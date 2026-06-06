package com.alchemedy.pharasnap.utils.Tutorial;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.activities.TutorialActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.utils.FloatingWidget;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.WidgetHighlighter;

public class TutorialGuide extends TutorialSteps{
//    private final static TutorialAction[] ForbiddenActions = new TutorialAction[] {
//            TutorialAction.LONG_PRESS_WORD,
//            TutorialAction.HISTORY_BUTTON_SELECT,
//            TutorialAction.SWITCH_MODE,
//            TutorialAction.ERASE_ALL_SELECTIONS,
//            TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL,
//            TutorialAction.OPEN_QUICK_SETTINGS,
//            TutorialAction.BOTTOM_BAR_SELECT,
//            TutorialAction.MODAL_CLOSE
//    };
private final static TutorialAction[] AllowedActions = new TutorialAction[] {
        TutorialAction.MOVE_BOTTOM_BAR,
        TutorialAction.MOVE_FINISHED_BOTTOM_BAR,
        TutorialAction.MOVE_WIDGET_BUTTON,
        TutorialAction.WIDGET_MOVE_FINISHED,
        TutorialAction.SELECT_FEW_WORDS_ON_TEXT_SELECTION
};

private boolean isShowingTutorialExitConfirmationModal = false;
private boolean isSubActivityStarted;


private static RemoteViewCollector activeRemoteViewCollector = null;

    enum POSITION {
        TOP,
        LEFT,
        BOTTOM,
        RIGHT,
        CENTER
    }

    private static TutorialGuide instance;
    private int index = 0;
    ViewGroup guideOverlay;
    private WidgetHighlighter widgetHighlighter;
    NodeExplorerAccessibilityService accessibilityService;
    SharedPreferences sharedPreferences;

    public interface RemoteViewCollector {
        View getView();
    }

    static class Step {

        TutorialAction action;
        String text = null;
        int stickWidgetId = 0;
        boolean shouldFetchViewRemotely = false;
        POSITION position;
        Runnable preExecution;
        boolean showTarget;

        public Step(TutorialAction action) {
            this.action = action;
        }

        public Step highlightTarget() {
            showTarget = true;
            return this;
        }

        public Step(TutorialAction action, String text, int stickWidgetId, POSITION position) {
            this.action = action;
            this.text = text;
            this.stickWidgetId = stickWidgetId;
            this.position = position;
        }

        public Step fetchViewDynamically() {
            shouldFetchViewRemotely = true;
            return this;
        }

        Step beforeRun(Runnable preExecution) {
            this.preExecution = preExecution;
            return this;
        }
    }

    TutorialGuide(FloatingWidget floatingWidget, CustomOverlayView rootOverlay, SharedPreferences sharedPreferences, NodeExplorerAccessibilityService accessibilityService) {
        this.primaryOverlay = rootOverlay;
        this.floatingWidget = floatingWidget;
        this.accessibilityService = accessibilityService;
        this.sharedPreferences = sharedPreferences;
        AccessibilityServiceInfo serviceInfo = accessibilityService.getServiceInfo();
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        accessibilityService.setServiceInfo(serviceInfo);
        populateView(steps[0]);
    }

    public static boolean isTutorialRunning() {
        return instance != null;
    }

    public static void registerForViewProvider(RemoteViewCollector remoteViewCollector) {
        if (instance != null)
            activeRemoteViewCollector = remoteViewCollector;
    }

    public static void notifyAttemptToCloseWidget(Runnable onContinue) {
        if (instance != null) {
            if (instance.isShowingTutorialExitConfirmationModal)
                return;
            Context context = instance.primaryOverlay.getContext();
            if (instance.isSubActivityStarted) {
                Toast.makeText(context, "Tutorial stopped unexpectedly", Toast.LENGTH_SHORT).show();
                onContinue.run();
                return;
            }

            ViewGroup exitConfirmation = (ViewGroup) LayoutInflater.from(context)
                    .inflate(R.layout.tutorial_exit_confirmation, null);
            WindowManager windowManager = (WindowManager) instance.accessibilityService.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            exitConfirmation.findViewById(R.id.cancel_button).setOnClickListener((v) -> {
                instance.isShowingTutorialExitConfirmationModal = false;
                windowManager.removeView(exitConfirmation);

                instance.accessibilityService.startActivity(new Intent(instance.accessibilityService, TutorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            });
            exitConfirmation.findViewById(R.id.confirm_button).setOnClickListener((v) -> {
                instance.isShowingTutorialExitConfirmationModal = false;
                windowManager.removeView(exitConfirmation);
                onContinue.run();
            });
            instance.isShowingTutorialExitConfirmationModal = true;
            windowManager.addView(exitConfirmation, params);
        } else
            onContinue.run();
    }

    public static void clearResources() {
        if (instance != null) {
            instance.finishTutorial(false);
        }
    }
    private void finishTutorial(boolean shouldCollapseWidget) {
        if (guideOverlay != null) {
            primaryOverlay.removeView(guideOverlay);
            if (widgetHighlighter != null)
                primaryOverlay.removeView(widgetHighlighter);
            if (shouldCollapseWidget)
                floatingWidget.collapseWidget();
        }
        activeRemoteViewCollector = null;
        instance = null;
        new MessageHandler(primaryOverlay.getContext()).sendBroadcast(
                new Intent(Constants.TUTORIAL)
                        .putExtra(Constants.END_TUTORIAL, true)
        );

        AccessibilityServiceInfo serviceInfo = accessibilityService.getServiceInfo();
        serviceInfo.flags &= ~AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        accessibilityService.setServiceInfo(serviceInfo);
    }

    private final int[] mirrorViewIds = new int[] {
            R.id.buttonContainer,
            R.id.toggleMode,
            R.id.eraser,
            R.id.toggleCollapse,
            R.id.list
    };

    private void shake() {
        if (steps[index].action == TutorialAction.NEXT) {
            ObjectAnimator.ofFloat(
                            guideOverlay.findViewById(R.id.guide_next), "translationX", 0, -30, 30, 0, -30, 0)
                    .setDuration(500)
                    .start();
        } else {
            ObjectAnimator.ofFloat(
                            guideOverlay, "translationX", 0, -30, 30, 0, -30, 0)
                    .setDuration(500)
                    .start();
        }
    }

    public static void start(FloatingWidget floatingWidget, CustomOverlayView rootOverlay, SharedPreferences sharedPreferences, NodeExplorerAccessibilityService accessibilityService) {
        instance = new TutorialGuide(floatingWidget, rootOverlay, sharedPreferences, accessibilityService);
    }

    private void evaluatePosition(Step step) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) guideOverlay.getLayoutParams();
        params.setMargins(0, 0, 0, 0);
        if (step.stickWidgetId != 0 || step.shouldFetchViewRemotely) {
            View targetView = step.shouldFetchViewRemotely &&
                    activeRemoteViewCollector != null ? activeRemoteViewCollector.getView() : primaryOverlay.findViewById(step.stickWidgetId);
            if (step.showTarget) {
                if (widgetHighlighter == null) {
                    widgetHighlighter = WidgetHighlighter.create(primaryOverlay, targetView);
                } else
                    widgetHighlighter.update(primaryOverlay, targetView);
            } else if (widgetHighlighter != null) {
                primaryOverlay.removeView(widgetHighlighter);
                widgetHighlighter = null;
            }
            POSITION position = step.position;

            if ((position == POSITION.LEFT || position == POSITION.RIGHT)
                    && sharedPreferences.getBoolean(Constants.IS_WIDGET_LEFT_ORIENTED, false)) {
                for (int id : mirrorViewIds) {
                    if (step.stickWidgetId == id) {
                        position = position == POSITION.LEFT ? POSITION.RIGHT : POSITION.LEFT;
                        break;
                    }
                }
            }

            int[] rootOverlayOffset = new int[2], targetViewOffset = new int[2];
            primaryOverlay.getLocationOnScreen(rootOverlayOffset);
            targetView.getLocationOnScreen(targetViewOffset);
            Rect rootOverlayRect = new Rect(rootOverlayOffset[0], rootOverlayOffset[1], rootOverlayOffset[0] + primaryOverlay.getWidth(), rootOverlayOffset[1] + primaryOverlay.getHeight());
            Rect targetViewRect = new Rect(targetViewOffset[0], targetViewOffset[1], targetViewOffset[0] + targetView.getWidth(), targetViewOffset[1] + targetView.getHeight());

            if (position == POSITION.LEFT) {
                params.rightMargin = rootOverlayRect.right - targetViewRect.left;
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                params.topMargin = targetViewRect.centerY() - rootOverlayRect.centerY();
            } else if (position == POSITION.RIGHT) {
                params.leftMargin = targetViewRect.right - rootOverlayRect.left;
                params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                params.topMargin = targetViewRect.centerY() - rootOverlayRect.centerY();
            } else if (position == POSITION.TOP) {
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                params.bottomMargin = rootOverlayRect.bottom - targetViewRect.top;
            } else if (position == POSITION.BOTTOM) {
                params.topMargin = targetViewRect.bottom - rootOverlayRect.top;
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            }
        } else {
            if (step.position == POSITION.CENTER) {
                params.gravity = Gravity.CENTER;
            }
        }
        guideOverlay.setLayoutParams(params);
    }

    private void show() {
        guideOverlay = (ViewGroup) LayoutInflater.from(primaryOverlay.getContext())
                .inflate(R.layout.tutorial_guide, primaryOverlay, false);
        guideOverlay.findViewById(R.id.guide_next).setOnClickListener(
                v -> trigger(TutorialAction.NEXT)
        );
        guideOverlay.findViewById(R.id.guide_skip_tutorial).setOnClickListener(v -> {
            finishTutorial(true);
        });
        primaryOverlay.addView(guideOverlay);
    }

    public static void changePrimaryOverlay(ViewGroup newOverlay, boolean isSubActivityStarted) {
        if (instance != null) {
            instance.primaryOverlay = newOverlay;
            trigger(TutorialAction.PRIMARY_OVERLAY_CHANGED);
            instance.isSubActivityStarted = isSubActivityStarted;
        }
    }

    private void hide() {
        primaryOverlay.removeView(guideOverlay);
        if (widgetHighlighter != null)
            primaryOverlay.removeView(widgetHighlighter);
        widgetHighlighter = null;
        guideOverlay = null;
    }

    public static void relayout() {
        if (instance != null) {
            instance.refreshLayout();
            if (instance.widgetHighlighter != null)
                instance.widgetHighlighter.orientationChanged();
        }
    }

    private void refreshLayout() {
        if (index < steps.length && guideOverlay != null) {
            Step step = steps[index];
            evaluatePosition(step);
        }
    }

    private void populateView(Step step) {
        if (guideOverlay == null)
            show();
        String guideTextContent = step.text;
        if (guideTextContent == null) {
            hide();
            return;
        }
        TextView guideContentTextView = guideOverlay.findViewById(R.id.guide_text_content);
        Button next = guideOverlay.findViewById(R.id.guide_next);
        if (step.preExecution != null)
            step.preExecution.run();
        guideContentTextView.setText(guideTextContent);

        if (step.action == TutorialAction.NEXT) {
            next.setVisibility(View.VISIBLE);
            if (index + 1 == steps.length)
                next.setText("Complete");
        } else
            next.setVisibility(View.GONE);
        evaluatePosition(step);
    }
    public static void cancelTrigger(TutorialAction action) {
        if (instance != null && instance.index > 0 && action == instance.steps[instance.index -1].action) {
            instance.index -= 1;
            instance.populateView(instance.steps[instance.index]);
        }
    }

    public static boolean trigger(TutorialAction action) {
        if (instance == null || instance.index >= instance.steps.length) return false;
        Step step = instance.steps[instance.index];
        if (step.action == action) {
            instance.index++;
            if (instance.index == instance.steps.length) {
                instance.finishTutorial(true);
                return false;
            }
            instance.populateView(instance.steps[instance.index]);
        } else {
            boolean shouldShake = true;
            for (TutorialAction allowedAction : AllowedActions) {
                if (allowedAction == action) {
                    shouldShake = false;
                    break;
                }
            }
            if (shouldShake)
                instance.shake();
            return true;
        }
        return false;
    }
}
