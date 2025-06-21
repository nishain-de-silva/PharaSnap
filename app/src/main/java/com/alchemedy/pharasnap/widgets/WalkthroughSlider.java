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
        public abstract boolean onResume(int id);
        public abstract boolean onButtonPress(int id);
        public abstract void onComplete();
    }
    public static class PageContent {
        public static abstract class AttachedStateListener {
            protected abstract void onAttach();
            protected abstract void onDetach();
        }

        String buttonTitle, title;
        int contentId;
        boolean canRevisit = true;

        AttachedStateListener attachedStateListener;
        int graphicsLayoutId = -1;
        public PageContent(int contentId, String title) {
            this.contentId = contentId;
            this.title = title;
        }

        public PageContent onAttachStateChanged(AttachedStateListener attachedStateListener) {
            this.attachedStateListener = attachedStateListener;
            return this;
        }

        public PageContent onDrawGraphics(int graphicLayoutId) {
            this.graphicsLayoutId = graphicLayoutId;
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
    private void loadPage(boolean animateForwardDirection, boolean isAnimating) {
        int initialWidth = getWidth();
        removeAllViews();
        PageContent page = list.get(currentIndex);
        ViewGroup inflatedPage = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.walkthrough_page, null);
        ((TextView) inflatedPage.findViewById(R.id.page_content)).setText(page.contentId);
        inflatedPage.findViewById(R.id.task_complete_indicator).setVisibility(GONE);
        TextView paginationIndicator = inflatedPage.findViewById(R.id.pagination_indicator);
        ViewGroup visualContainer = inflatedPage.findViewById(R.id.page_visual_content);
        if (page.graphicsLayoutId != -1) {
            visualContainer.addView(LayoutInflater.from(getContext()).inflate(page.graphicsLayoutId, null));
        } else
            visualContainer.setVisibility(GONE);
        if (list.size() > 1)
            paginationIndicator.setText(String.format("%d / %d", currentIndex + 1, list.size()));
        else
            paginationIndicator.setVisibility(GONE);
        Button previousButton = inflatedPage.findViewById(R.id.page_previous);
        if (currentIndex > 0 && list.get(currentIndex - 1).canRevisit) {
            previousButton.setOnClickListener(v -> changePage(false, true));
        } else
            previousButton.setVisibility(GONE);
        ((TextView) inflatedPage.findViewById(R.id.page_title)).setText(page.title);
        Button actionButton = inflatedPage.findViewById(R.id.page_continue);
        actionButton.setText(page.buttonTitle != null ? page.buttonTitle :
                getDefaultButtonText()
        );
        actionButton.setOnClickListener(v -> {
            if (page.buttonTitle == null || !eventHandler.onButtonPress(page.contentId))
                changePage(true, true);
        });
        addView(inflatedPage, new ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        if (page.attachedStateListener != null)
            page.attachedStateListener.onAttach();

        if (isAnimating) {
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

    public boolean changePage(boolean isForwardDirection, boolean animating) {
        PageContent currentPage = list.get(currentIndex);
        if (isForwardDirection)
            currentIndex += 1;
        else {
            if (currentIndex < 1 || !list.get(currentIndex - 1).canRevisit)
                return false;
            currentIndex -= 1;
        }
        if (currentPage.attachedStateListener != null)
            currentPage.attachedStateListener.onDetach();

        if (isForwardDirection && currentIndex == list.size()) {
            eventHandler.onComplete();
            return true;
        }
        if (!animating) {
            loadPage(isForwardDirection, false);
            return true;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(valueAnimator -> {
            float frameFraction = valueAnimator.getAnimatedFraction();
            setTranslationX(frameFraction * (isForwardDirection ? -1 : 1) * getWidth());
            setAlpha(1 - frameFraction);
            if (frameFraction == 1) {
                loadPage(isForwardDirection, true);
            }
        });
        animator.setDuration(350);
        animator.start();
        return true;
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
        actionButton.setOnClickListener(v -> changePage(true, true));
    }

    public void onActivityResume() {
        if (!list.isEmpty()
                && currentIndex > -1
                && currentIndex < list.size()
        ) {
            PageContent page = list.get(currentIndex);
            if (eventHandler.onResume(page.contentId)) {
                changePage(true, false);
            }
        }
    }
}
