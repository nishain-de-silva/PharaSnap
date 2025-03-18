package com.alchemedy.pharasnap.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.CoordinateF;

import java.util.List;

public class EntryListAdapter extends ArrayAdapter<EntryListAdapter.Item> {
    public static class Item {
        public String content;
        public boolean isDraft;
        public Item(String content, boolean isDraft) {
            this.content = content;
            this.isDraft = isDraft;
        }
    }
    private final OnTapListener onTapListener;

    public static abstract class OnTapListener {
        public abstract void onTap(int index, Item item);
        public abstract void onTapCopyToClipboardShortcut(int index, Item item);

        public abstract void onDelete(int index, EntryListAdapter adapter);
    }
    public EntryListAdapter(@NonNull Context context, @NonNull List<Item> objects, OnTapListener onTapListener) {
        super(context, 0, objects);
        this.onTapListener = onTapListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final Item item = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_row, null);
        }
        View sliderContainer = convertView.findViewById(R.id.item_slider);
        if (!item.isDraft) {
            convertView.setOnClickListener(null);
            convertView.setOnTouchListener(new View.OnTouchListener() {
                CoordinateF downCoordinate;
                boolean shouldRecordMoveDirection = false;
                boolean isSliderGestureSessionStarted = false;
                int componentWidth;

                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    int action = motionEvent.getAction();
                    if (action == MotionEvent.ACTION_DOWN) {
                        shouldRecordMoveDirection = true;
                        componentWidth = view.getWidth();
                        downCoordinate = new CoordinateF(motionEvent.getRawX(), motionEvent.getRawY());
                    } else if (action == MotionEvent.ACTION_UP) {
                        if (downCoordinate.isCloserTo(motionEvent.getRawX(), motionEvent.getRawY(), 15)) {
                            onTapListener.onTap(position, item);
                        } else if (isSliderGestureSessionStarted) {
                            isSliderGestureSessionStarted = false;
                            view.getParent().requestDisallowInterceptTouchEvent(false);
                            final float translation = sliderContainer.getTranslationX();
                            final float translationMagnitude = Math.abs(translation);
                            if (translation != 0) {
                                boolean shouldRemove = translationMagnitude > (0.3 * view.getWidth());
                                ObjectAnimator springAnimator = ObjectAnimator.ofFloat(sliderContainer, "translationX", translation,
                                        shouldRemove ? (translation > 0 ? componentWidth : -componentWidth) : 0);
                                long duration = 400;
                                if (shouldRemove) {
                                    view.findViewById(R.id.item_delete_indicator).setVisibility(View.GONE);

                                    duration *= (componentWidth / translationMagnitude) / componentWidth;
                                    springAnimator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sliderContainer.getLayoutParams();;
                                            ValueAnimator exitAnimator = ValueAnimator.ofInt(sliderContainer.getHeight(), 0).setDuration(200);
                                            exitAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                                @Override
                                                public void onAnimationUpdate(@NonNull ValueAnimator valueAnimator) {
                                                    layoutParams.height = (int) valueAnimator.getAnimatedValue();
                                                    sliderContainer.setLayoutParams(layoutParams);
                                                    if (layoutParams.height == 0)
                                                        onTapListener.onDelete(position, EntryListAdapter.this);
                                                }
                                            });
                                            exitAnimator.start();
                                        }
                                    });
                                }
                                springAnimator.setDuration(duration);
                                springAnimator.start();
                            }
                        }
                        shouldRecordMoveDirection = false;
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        if (!downCoordinate.isCloserTo(motionEvent.getRawX(), motionEvent.getRawY(), 15)) {
                            if (shouldRecordMoveDirection) {
                                shouldRecordMoveDirection = false;
                                if (Math.abs(motionEvent.getRawX() - downCoordinate.x) >
                                        Math.abs(motionEvent.getRawY() - downCoordinate.y)) {
                                    view.getParent().requestDisallowInterceptTouchEvent(true);
                                    isSliderGestureSessionStarted = true;
                                }
                            } else if (isSliderGestureSessionStarted)
                                sliderContainer.setTranslationX(motionEvent.getRawX() - downCoordinate.x);
                        }
                    }
                    return true;
                }
            });
        } else {
            convertView.setOnTouchListener(null);
            convertView.setOnClickListener(view -> onTapListener.onTap(position, item));
        }
        if (sliderContainer.getTranslationX() != 0) {
            sliderContainer.setTranslationX(0);
            FrameLayout.LayoutParams sliderContainerLayoutParams = (FrameLayout.LayoutParams) sliderContainer.getLayoutParams();
            sliderContainerLayoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            sliderContainer.setLayoutParams(sliderContainerLayoutParams);
        }
        convertView.findViewById(R.id.item_draft_indicator).setVisibility(item.isDraft ? View.VISIBLE : View.GONE);
        convertView.findViewById(R.id.item_delete_indicator).setVisibility(View.VISIBLE);


        convertView.findViewById(R.id.row_action_copy_shortcut).setOnClickListener(v -> onTapListener.onTapCopyToClipboardShortcut(position, item));
        TextView content = convertView.findViewById(R.id.row_item_content);
        content.setText(item.content);
        return convertView;
    }
}
