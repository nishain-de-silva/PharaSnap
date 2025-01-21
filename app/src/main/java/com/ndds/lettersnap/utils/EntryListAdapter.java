package com.ndds.lettersnap.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ndds.lettersnap.R;

import java.util.List;

public class EntryListAdapter extends ArrayAdapter<String> {
    private OnTapListener onTapListener;
    public static abstract class OnTapListener {
        public abstract void onTap(String text);
        public abstract void onTapCopyToClipboardShortcut(String text);
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
        convertView.setOnClickListener(v -> onTapListener.onTap(text));
        convertView.findViewById(R.id.row_action_copy_shortcut).setOnClickListener(v -> onTapListener.onTapCopyToClipboardShortcut(text));
        TextView content = convertView.findViewById(R.id.row_item_content);
        content.setText(text);
        return convertView;
    }
}
