package com.alchemedy.pharasnap.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.alchemedy.pharasnap.BuildConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MessageHandler {
    private final Context context;
    private static class MessageEntry {
        public boolean isSingleTime;
        public BroadcastReceiver broadcastReceiver;
        public MessageEntry(boolean isSingleTime, BroadcastReceiver broadcastReceiver) {
            this.isSingleTime = isSingleTime;
            this.broadcastReceiver = broadcastReceiver;
        }
    }

    public MessageHandler(Context context) {
        this.context = context;
    }
    private static final HashMap<String, MessageEntry> broadcastReceiverMap = new HashMap<>();
    private void checkForKeyExistence(String filter) {
        if (BuildConfig.DEBUG && broadcastReceiverMap.containsKey(filter)) {
            throw new RuntimeException("[MessageHandler] filter by name "+filter+" is registered a listener without unregistering previous broadcast listener. Could be behaviour malfunction");
        }
    }
    public void registerReceiver(BroadcastReceiver broadcastMessageReceiver, String filter) {
        checkForKeyExistence(filter);
        broadcastReceiverMap.put(filter, new MessageEntry(false, broadcastMessageReceiver));
    }

    public void registerReceiverOnce(BroadcastReceiver broadcastMessageReceiver, String filter) {
        checkForKeyExistence(filter);
        broadcastReceiverMap.put(filter, new MessageEntry(true, broadcastMessageReceiver));
    }

    public void sendBroadcast(Intent intent) {
        String key = intent.getAction();
        MessageEntry messageEntry = broadcastReceiverMap.get(key);
        if (messageEntry != null) {
            new Handler(context.getMainLooper()).post(() -> messageEntry.broadcastReceiver.onReceive(context, intent));
            if (messageEntry.isSingleTime)
                broadcastReceiverMap.remove(key);
        }
    }

    public void unregisterReceiver(String filter) {
        broadcastReceiverMap.remove(filter);
    }

    public void clearAllExcept(String exceptFilter) {
        MessageEntry messageEntry = broadcastReceiverMap.get(exceptFilter);
        broadcastReceiverMap.clear();
        if (messageEntry != null)
            broadcastReceiverMap.put(exceptFilter, messageEntry);
    }
}
