package com.alchemedy.pharasnap.utils.Tutorial;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.utils.FloatingWidget;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.WidgetHighlighter;

public class TutorialGuide extends TutorialSteps{
    private final static TutorialAction[] ForbiddenActions = new TutorialAction[] {
            TutorialAction.LONG_PRESS_WORD,
            TutorialAction.HISTORY_BUTTON_SELECT,
            TutorialAction.SWITCH_MODE,
            TutorialAction.ERASE_ALL_SELECTIONS,
            TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL,
            TutorialAction.OPEN_QUICK_SETTINGS,
            TutorialAction.BOTTOM_BAR_SELECT,
            TutorialAction.MODAL_CLOSE
    };
    private final FloatingWidget floatingWidget;

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

    static class Step {
        TutorialAction action;
        String text = null;
        int stickWidgetId = 0;
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

        Step beforeRun(Runnable preExecution) {
            this.preExecution = preExecution;
            return this;
        }
    }

    TutorialGuide(FloatingWidget floatingWidget, CustomOverlayView rootOverlay, SharedPreferences sharedPreferences, NodeExplorerAccessibilityService accessibilityService) {
        this.rootOverlay = rootOverlay;
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

    public static void clearResources() {
        if (instance != null)
            instance.finishTutorial(false);
    }
    private void finishTutorial(boolean collapseWidget) {
        if (guideOverlay != null) {
            rootOverlay.removeView(guideOverlay);
            if (widgetHighlighter != null)
                rootOverlay.removeView(widgetHighlighter);
            if (collapseWidget)
                floatingWidget.collapseWidget();
        }
        instance = null;
        new MessageHandler(rootOverlay.getContext()).sendBroadcast(
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

    private void evaluatePosition(POSITION initialPosition, int widgetId, boolean shouldHighlightTargetView) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) guideOverlay.getLayoutParams();
        params.setMargins(0, 0, 0, 0);
        if (widgetId != 0) {
            View targetView = rootOverlay.findViewById(widgetId);
            if (shouldHighlightTargetView) {
                if (widgetHighlighter == null) {
                    widgetHighlighter = WidgetHighlighter.create(rootOverlay, targetView);
                } else
                    widgetHighlighter.update(rootOverlay, targetView);
            } else if (widgetHighlighter != null) {
                rootOverlay.removeView(widgetHighlighter);
                widgetHighlighter = null;
            }
            POSITION position = initialPosition;

            if ((position == POSITION.LEFT || position == POSITION.RIGHT)
                    && sharedPreferences.getBoolean(Constants.IS_WIDGET_LEFT_ORIENTED, false)) {
                for (int id : mirrorViewIds) {
                    if (widgetId == id) {
                        position = position == POSITION.LEFT ? POSITION.RIGHT : POSITION.LEFT;
                        break;
                    }
                }
            }

            int[] rootOverlayOffset = new int[2], targetViewOffset = new int[2];
            rootOverlay.getLocationOnScreen(rootOverlayOffset);
            targetView.getLocationOnScreen(targetViewOffset);
            Rect rootOverlayRect = new Rect(rootOverlayOffset[0], rootOverlayOffset[1], rootOverlayOffset[0] + rootOverlay.getWidth(), rootOverlayOffset[1] + rootOverlay.getHeight());
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
            if (initialPosition == POSITION.CENTER) {
                params.gravity = Gravity.CENTER;
            }
        }
        guideOverlay.setLayoutParams(params);
    }

    private void show() {
        guideOverlay = (ViewGroup) LayoutInflater.from(rootOverlay.getContext())
                .inflate(R.layout.tutorial_guide, rootOverlay, false);
        guideOverlay.findViewById(R.id.guide_next).setOnClickListener(
                v -> trigger(TutorialAction.NEXT)
        );
        guideOverlay.findViewById(R.id.guide_skip_tutorial).setOnClickListener(v -> {
            finishTutorial(true);
        });
        rootOverlay.addView(guideOverlay);
    }

    private void hide() {
        rootOverlay.removeView(guideOverlay);
        if (widgetHighlighter != null)
            rootOverlay.removeView(widgetHighlighter);
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
            evaluatePosition(step.position, step.stickWidgetId, step.showTarget);
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
                next.setText("Finish");
        } else
            next.setVisibility(View.GONE);
        evaluatePosition(step.position, step.stickWidgetId, step.showTarget);
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
        } else if (action == TutorialAction.PAUSE_TUTORIAL) {
            instance.populateView(new Step(TutorialAction.PAUSE_TUTORIAL, "Tutorial was interrupted. Expand the widget to restart tutorial", R.id.toggleCollapse, POSITION.LEFT));
        } else if (action == TutorialAction.TAP_WIDGET_BUTTON) {
            // restart tutorial
            instance.index = 1;
            instance.populateView(instance.steps[1]);
        } else {
            for (TutorialAction forbiddenAction : ForbiddenActions) {
                if (forbiddenAction == action) {
                    instance.shake();
                    return true;
                }
            }
        }
        return false;
    }
}
