package com.alchemedy.pharasnap.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.alchemedy.pharasnap.R;

public class TabSelector extends LinearLayout {
    int selectedIndex;

    public TabSelector(Context context) {
        super(context);
    }

    public TabSelector(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TabSelector(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public interface OnTabChangeListener {
        void onChange(int index);
    }
    private void selectIndex(int selectedIndex) {
        ViewGroup tabCell = (ViewGroup) getChildAt(this.selectedIndex * 2);
        TextView heading = (TextView) tabCell.getChildAt(0);
        TextView description = (TextView) tabCell.getChildAt(1);

        heading.setTextColor(getContext().getColor(R.color.darkPurple));
        description.setTextColor(getContext().getColor(R.color.darkPurple));
        tabCell.setBackground(null);

        ViewGroup newTabCell = (ViewGroup) getChildAt(selectedIndex * 2);
        TextView newHeading = (TextView) newTabCell.getChildAt(0);
        TextView newDescription = (TextView) newTabCell.getChildAt(1);
        newHeading.setTextColor(Color.WHITE);
        newDescription.setTextColor(Color.WHITE);
        newTabCell.setBackgroundResource(R.drawable.primary_color_round_rect);
        this.selectedIndex = selectedIndex;
    }


    public void setup(String[] tabs, int selectedIndex, OnTabChangeListener onTabChangeListener) {
        this.selectedIndex = selectedIndex;
        setOrientation(LinearLayout.VERTICAL);
        Resources resources = getContext().getResources();
        int contentPadding = (int) (resources.getDisplayMetrics().density * 5f);
        int dividerHorizontalMargin = (int) (resources.getDisplayMetrics().density * 12f);

        setPadding(contentPadding, contentPadding, contentPadding, contentPadding);
        for (int i = 0; i < tabs.length; i+=2) {
            ViewGroup tabCell = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.tab_selector_cell, null);
            TextView heading = (TextView) tabCell.getChildAt(0);
            TextView description = (TextView) tabCell.getChildAt(1);
            int itemIndex = i / 2;

            if (itemIndex > 0) {
                View outline = new View(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) resources.getDisplayMetrics().density
                );
                params.setMarginStart(dividerHorizontalMargin);
                params.setMarginEnd(dividerHorizontalMargin);
                outline.setLayoutParams(params);
                outline.setBackgroundColor(getContext().getColor(R.color.darkPurple));
                addView(outline);
            }

            addView(tabCell);
            heading.setText(tabs[i]);
            description.setText(tabs[i + 1]);
            if (itemIndex == selectedIndex) {
                tabCell.setBackgroundResource(R.drawable.primary_color_round_rect);
                heading.setTextColor(Color.WHITE);
                description.setTextColor(Color.WHITE);
            }

            tabCell.setOnClickListener(view -> {
                selectIndex(itemIndex);
                onTabChangeListener.onChange(itemIndex);
            });
        }
    }
}
