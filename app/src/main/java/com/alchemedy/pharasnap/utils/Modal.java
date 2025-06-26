package com.alchemedy.pharasnap.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.alchemedy.pharasnap.R;

public class Modal {
    ViewGroup overlayView;
    private ViewGroup modalWindow;
    private ModalCallback currentModalCallback;
    public abstract static class ModalCallback {

        public void onBeforeModalShown(ViewGroup inflatedView) {}
        public void onOpened(ViewGroup inflatedView, boolean isModalAlreadyOpened) {}

        public void onHeaderBackPressed(Modal modal) {
            handleDefaultClose(modal);
        }

        public void onModalClosed() {}
    }

    static void handleDefaultClose(Modal modal) {
        View container = modal.modalWindow.findViewById(R.id.modal_container);
        ValueAnimator animator = ValueAnimator.ofFloat(0, container.getHeight());
        animator.addUpdateListener(valueAnimator -> {
            float amount = (float) valueAnimator.getAnimatedValue();
            container.setTranslationY(amount);
            if (valueAnimator.getAnimatedFraction() == 1) {
                modal.overlayView.removeView(modal.modalWindow);
                modal.modalWindow = null;
            }
        });
        animator.setDuration(150);
        animator.start();
    }

    public Modal(ViewGroup rootView) {
        overlayView = rootView;
    }

    public enum ModalType {
        EDIT_SELECTION,
        ENTRY_LIST,
        SETTINGS
    }

    public void closeModal() {
        if(modalWindow != null) {
            if (currentModalCallback != null) {
                currentModalCallback.onModalClosed();
                currentModalCallback = null;
            }
            handleDefaultClose(this);
        }
    }

    public void reLayout() {
        if (modalWindow == null) return;
        int topPadding = overlayView.getContext().getResources().getDimensionPixelSize(R.dimen.modal_top_margin);
        modalWindow.setPadding(0, topPadding, 0, 0);
    }

    public boolean handleSystemGoBack() {
        if (modalWindow == null) return false;
        currentModalCallback.onHeaderBackPressed(this);
        return true;
    }

    private int getHeadingTextId(ModalType modalType) {
        if (modalType == ModalType.EDIT_SELECTION)
            return R.string.text_selection_modal_title;
        if (modalType == ModalType.ENTRY_LIST)
            return R.string.entry_list_modal_title;
        return R.string.quick_settings_modal_title;
    }

    private int getContentLayoutId(ModalType modalType) {
        if (modalType == ModalType.EDIT_SELECTION)
            return R.layout.edit_text_selection;
        if (modalType == ModalType.ENTRY_LIST)
            return R.layout.list_window;
        return R.layout.settings_modal;
    }


    public void showModal(ModalType modalType, ModalCallback modalCallback) {
        Context context = overlayView.getContext();
        boolean isModalCreated = modalWindow == null;
        if (isModalCreated) {
            modalWindow = (ViewGroup) LayoutInflater.from(context)
                    .inflate( R.layout.modal, null);
            modalWindow.setOnClickListener(v -> {
                currentModalCallback.onModalClosed();
                currentModalCallback = null;
                handleDefaultClose(this);
            });
        }
        currentModalCallback = modalCallback;

        int topPadding = context.getResources().getDimensionPixelSize(R.dimen.modal_top_margin);
        modalWindow.setPadding(0, topPadding, 0, 0);
        ViewGroup content = modalWindow.findViewById(R.id.modal_content);
        ((TextView) modalWindow.findViewById(R.id.modal_back_title)).setText(getHeadingTextId(modalType));
        modalWindow.findViewById(R.id.modal_back).setOnClickListener(v -> modalCallback.onHeaderBackPressed(this));

        if (!isModalCreated)
            content.removeAllViews();
        ViewGroup inflatedContent = (ViewGroup) LayoutInflater.from(context).inflate(
                getContentLayoutId(modalType),
                null);
        content.addView(inflatedContent);
        View container = modalWindow.findViewById(R.id.modal_container);
        if(isModalCreated) {
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
            overlayView.addView(modalWindow);
            animator.start();
        } else {
            modalCallback.onBeforeModalShown(inflatedContent);
            modalCallback.onOpened(inflatedContent, true);
        }
    }
}
