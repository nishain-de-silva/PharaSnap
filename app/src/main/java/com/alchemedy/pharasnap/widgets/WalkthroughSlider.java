package com.alchemedy.pharasnap.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;

import java.util.ArrayList;

public class WalkthroughSlider extends LinearLayout {
    private int currentIndex = -1;
    private ArrayList<PageContent> list = new ArrayList<>();

    public static abstract class EventHandler {
        public abstract Boolean onResume(int id);
        public abstract boolean onButtonPress(int id);
        public abstract void onComplete();
    }
    public static class PageContent {
        public static abstract class CreateCallback {
            protected abstract void onCreate();
        }
        String buttonTitle, title;
        int contentId;
        boolean canRevisit = true;
        CreateCallback createCallback;
        public PageContent(int contentId, String title) {
            this.contentId = contentId;
            this.title = title;
        }

        public PageContent onCreate(CreateCallback createCallback) {
            this.createCallback = createCallback;
            return this;
        }

        public PageContent setButtonText(String title, boolean canRevisit) {
            this.canRevisit = canRevisit;
            buttonTitle = title;
            return this;
        }
    }

    EventHandler eventHandler;
    public WalkthroughSlider(Context context) {
        super(context);
    }

    private String getDefaultButtonText() {
        return  currentIndex == (list.size() - 1) ? (list.size() == 1 ? "Continue" : "Finish") : "Next";
    }
    private void loadPage(boolean animateForwardDirection, boolean isPageReload) {
        int initialWidth = getWidth();
        removeAllViews();
        PageContent page = list.get(currentIndex);
        ViewGroup inflatedPage = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.walkthrough_page, null);
        ((TextView) inflatedPage.findViewById(R.id.page_content)).setText(page.contentId);
        inflatedPage.findViewById(R.id.task_complete_indicator).setVisibility(GONE);
        TextView paginationIndicator = inflatedPage.findViewById(R.id.pagination_indicator);
        if (list.size() > 1)
            paginationIndicator.setText(String.format("%d / %d", currentIndex + 1, list.size()));
        else
            paginationIndicator.setVisibility(GONE);
        Button previousButton = inflatedPage.findViewById(R.id.page_previous);
        if (currentIndex > 0 && list.get(currentIndex - 1).canRevisit) {
            previousButton.setOnClickListener(v -> {
                changePage(false);
            });
        } else
            previousButton.setVisibility(GONE);
        ((TextView) inflatedPage.findViewById(R.id.page_title)).setText(page.title);
        Button actionButton = inflatedPage.findViewById(R.id.page_continue);
        actionButton.setText(page.buttonTitle != null ? page.buttonTitle :
                getDefaultButtonText()
        );
        actionButton.setOnClickListener(v -> {
            if (!eventHandler.onButtonPress(page.contentId))
                changePage(true);
        });
        addView(inflatedPage, new ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        if (!isPageReload) {
            if (page.createCallback != null)
                page.createCallback.onCreate();
            if (currentIndex > 0 || !animateForwardDirection) {
                ValueAnimator entryAnimator = ValueAnimator.ofFloat(0, 1);
                entryAnimator.addUpdateListener(valueAnimator -> {
                    float frameFraction = valueAnimator.getAnimatedFraction();
                    setTranslationX((animateForwardDirection ? 1 : -1) * initialWidth * (1 - frameFraction));
                    setAlpha(frameFraction);
                });
                entryAnimator.setDuration(350);
                entryAnimator.start();
            }
        }
    }

    public void changePage(boolean isForwardDirection) {
        currentIndex += isForwardDirection ? 1 : -1;
        if (isForwardDirection && currentIndex == list.size()) {
            eventHandler.onComplete();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(valueAnimator -> {
            float frameFraction = valueAnimator.getAnimatedFraction();
            setTranslationX(frameFraction * (isForwardDirection ? -1 : 1) * getWidth());
            setAlpha(1 - frameFraction);
            if (frameFraction == 1) {
                loadPage(isForwardDirection, false);
            }
        });
        animator.setDuration(350);
        animator.start();
    }

    public void start(ArrayList<PageContent> list, @Nullable EventHandler eventHandler) {
        this.list = list;
        currentIndex = 0;
        if (eventHandler != null)
            this.eventHandler = eventHandler;
        if (list.size() == 0) return;
        loadPage(true, false);
    }

    public WalkthroughSlider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WalkthroughSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public void indicateTaskCompleted(String indicatorText) {
        Button actionButton = findViewById(R.id.page_continue);
        TextView indicator = findViewById(R.id.task_complete_indicator);
        indicator.setVisibility(VISIBLE);
        indicator.setText(indicatorText);
        actionButton.setText(getDefaultButtonText());
        actionButton.setOnClickListener(v -> {
            changePage(true);
        });
    }

    public void onActivityResume() {
        if (list.size() > 0
                && currentIndex > -1
                && currentIndex < list.size()
        ) {
            Boolean canResume = eventHandler.onResume(list.get(currentIndex).contentId);
            if (canResume == null) return;
            if (canResume)
                indicateTaskCompleted("Completed");
            else
                loadPage(true, true);
        }

    }
}
