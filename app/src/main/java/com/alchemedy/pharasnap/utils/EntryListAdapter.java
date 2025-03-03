package com.alchemedy.pharasnap.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.helper.CoordinateF;

import java.util.List;

public class EntryListAdapter extends ArrayAdapter<String> {
    private OnTapListener onTapListener;

    public static abstract class OnTapListener {
        public abstract void onTap(int index, String text);
        public abstract void onTapCopyToClipboardShortcut(int index, String text);

        public abstract void onDelete(int index);
    }
    public EntryListAdapter(@NonNull Context context, @NonNull List<String> objects, OnTapListener onTapListener) {
        super(context, 0, objects);
        this.onTapListener = onTapListener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String text = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_row, null);
        }
        View sliderContainer = convertView.findViewById(R.id.item_slider);
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
                        onTapListener.onTap(position, text);
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
                                view.findViewById(R.id.item_undo).setVisibility(View.VISIBLE);
                                view.findViewById(R.id.item_delete1).setVisibility(View.GONE);
                                view.findViewById(R.id.item_delete2).setVisibility(View.GONE);
                                duration *= (componentWidth / translationMagnitude) / componentWidth;
                                springAnimator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        Handler removeHandler = new Handler();
                                        Runnable removeRunnable = () -> {
                                            if(!((View) view.getParent()).isShown()) return;
                                            onTapListener.onDelete(position);
                                            notifyDataSetChanged();
                                        };
                                        view.findViewById(R.id.item_undo).setOnClickListener(view1 -> {
                                            removeHandler.removeCallbacks(removeRunnable);
                                            sliderContainer.setTranslationX(0);
                                            view.findViewById(R.id.item_undo).setVisibility(View.GONE);
                                            view.findViewById(R.id.item_delete1).setVisibility(View.VISIBLE);
                                            view.findViewById(R.id.item_delete2).setVisibility(View.VISIBLE);
                                        });
                                        removeHandler.postDelayed(removeRunnable, 2000);
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
        convertView.findViewById(R.id.item_undo).setVisibility(View.GONE);
        convertView.findViewById(R.id.item_delete1).setVisibility(View.VISIBLE);
        convertView.findViewById(R.id.item_delete2).setVisibility(View.VISIBLE);
        convertView.findViewById(R.id.item_slider).setTranslationX(0);
        convertView.findViewById(R.id.row_action_copy_shortcut).setOnClickListener(v -> onTapListener.onTapCopyToClipboardShortcut(position, text));
        TextView content = convertView.findViewById(R.id.row_item_content);
        content.setText(text);
        return convertView;
    }
}
