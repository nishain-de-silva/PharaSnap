package com.alchemedy.pharasnap.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alchemedy.pharasnap.R;

public class Modal {
    View overlayView;
    private ModalCallback currentModalCallback;
    public abstract static class ModalCallback {

        public void onBeforeModalShown(ViewGroup inflatedView) {}
        public void onOpened(ViewGroup inflatedView, boolean isModalAlreadyOpened) {}

        public void onHeaderBackPressed(ViewGroup modalWindow) {
            handleDefaultClose(modalWindow);
        }

        public void onModalClosed() {}
    }

    static void handleDefaultClose(ViewGroup modalWindow) {
        View container = modalWindow.findViewById(R.id.modal_container);
        ValueAnimator animator = ValueAnimator.ofFloat(0, container.getHeight());
        animator.addUpdateListener(valueAnimator -> {
            float amount = (float) valueAnimator.getAnimatedValue();
            container.setTranslationY(amount);
            if (valueAnimator.getAnimatedFraction() == 1) {
                modalWindow.setVisibility(View.GONE);
            }
        });
        animator.setDuration(150);
        animator.start();
    }

    public Modal(View rootView) {
        overlayView = rootView;
        ViewGroup modalRootWindow = overlayView.findViewById(R.id.modal_window);
        modalRootWindow.setOnClickListener(v -> {
            currentModalCallback.onModalClosed();
            currentModalCallback = null;
            handleDefaultClose(modalRootWindow);
        });
    }

    public enum ModalType {
        EDIT_SELECTION,
        ENTRY_LIST,
        SETTINGS
    }

    public void closeModal() {
        ViewGroup modalWindow = overlayView.findViewById(R.id.modal_window);
        if(modalWindow.getVisibility() == View.VISIBLE) {
            if (currentModalCallback != null) {
                currentModalCallback.onModalClosed();
                currentModalCallback = null;
            }
            handleDefaultClose(modalWindow);
        }
    }

    public void reLayout() {
        int topPadding = overlayView.getContext().getResources().getDimensionPixelSize(R.dimen.modal_top_margin);
        ViewGroup modalWindow = overlayView.findViewById(R.id.modal_window);
        if(modalWindow.getVisibility() == View.VISIBLE) {
            modalWindow.setPadding(0, topPadding, 0, 0);
        }
    }

    public boolean handleSystemGoBack() {
        ViewGroup modalRootWindow = overlayView.findViewById(R.id.modal_window);
        if(modalRootWindow.getVisibility() == View.VISIBLE) {
            currentModalCallback.onHeaderBackPressed(modalRootWindow);
            return true;
        }
        return false;
    }

    private int getHeadingTextId(ModalType modalType) {
        if (modalType == ModalType.EDIT_SELECTION)
            return R.string.text_selection_modal_title;
        if (modalType == ModalType.ENTRY_LIST)
            return R.string.entry_list_modal_title;
        return R.string.settings_modal_title;
    }

    private int getContentLayoutId(ModalType modalType) {
        if (modalType == ModalType.EDIT_SELECTION)
            return R.layout.edit_text_selection;
        if (modalType == ModalType.ENTRY_LIST)
            return R.layout.list_window;
        return R.layout.settings_modal;
    }


    public void showModal(ModalType modalType, ModalCallback modalCallback) {
        currentModalCallback = modalCallback;
        Context context = overlayView.getContext();
        ViewGroup modalRootWindow = overlayView.findViewById(R.id.modal_window);
        int topPadding = context.getResources().getDimensionPixelSize(R.dimen.modal_top_margin);
        modalRootWindow.setPadding(0, topPadding, 0, 0);
        ViewGroup content = overlayView.findViewById(R.id.modal_content);
        ((TextView) modalRootWindow.findViewById(R.id.modal_back_title)).setText(getHeadingTextId(modalType));
        modalRootWindow.findViewById(R.id.modal_back).setOnClickListener(v -> modalCallback.onHeaderBackPressed(modalRootWindow));

        content.removeAllViews();
        ViewGroup inflatedContent = (ViewGroup) LayoutInflater.from(context).inflate(
                getContentLayoutId(modalType),
                null);
        content.addView(inflatedContent);
        View container = modalRootWindow.findViewById(R.id.modal_container);
        if(modalRootWindow.getVisibility() == View.GONE) {
            container.measure(
                    View.MeasureSpec.makeMeasureSpec(overlayView.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(overlayView.getHeight(), View.MeasureSpec.AT_MOST)
            );


            ValueAnimator animator = ValueAnimator.ofFloat(container.getMeasuredHeight(), 0);
            animator.addUpdateListener(valueAnimator -> {
                float amount = (float) valueAnimator.getAnimatedValue();
                container.setTranslationY(amount);
                if (valueAnimator.getAnimatedFraction() == 1)
                    modalCallback.onOpened(inflatedContent, false);

            });
            animator.setDuration(150);
            modalCallback.onBeforeModalShown(inflatedContent);
            animator.start();
            modalRootWindow.setVisibility(View.VISIBLE);
        } else {
            modalCallback.onBeforeModalShown(inflatedContent);
            modalCallback.onOpened(inflatedContent, true);
        }
    }
}
