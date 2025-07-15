package com.alchemedy.pharasnap.utils;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.service.quicksettings.Tile;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.activities.EditTextModalActivity;
import com.alchemedy.pharasnap.activities.MediaProjectionRequestActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.Coordinate;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.helper.EditedTextEntry;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.helper.ScreenInfo;
import com.alchemedy.pharasnap.helper.TextChangedListener;
import com.alchemedy.pharasnap.helper.WidgetLocationCoordinate;
import com.alchemedy.pharasnap.models.CopiedItem;
import com.alchemedy.pharasnap.models.TutorialAction;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.services.ShortcutTileLauncher;
import com.alchemedy.pharasnap.utils.Tutorial.TutorialGuide;
import com.alchemedy.pharasnap.widgets.BrowserModal;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;
import com.alchemedy.pharasnap.widgets.SelectionEditorTextView;
import com.alchemedy.pharasnap.widgets.TabSelector;
import com.alchemedy.pharasnap.widgets.BottomBar;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FloatingWidget {
    public static class Mode {
        public final static int TEXT = 1;
        public final static int CROPPED_IMAGE_CAPTURE = 2;
    }

    public static class TextDetectionMode {
        public final static int AUTO = 0;
        public final static int TEXT = 1;
        public final static int TEXT_RECOGNITION = 2;
    }
    public static boolean isWidgetIsShowing;
    private static int NODE_PROXIMITY_THRESHOLD;
    final private NodeExplorerAccessibilityService hostingService;
    private WindowManager windowManager;
    private ViewGroup overlayView;
    private WidgetController widgetController;
    private int textDetectionMode = TextDetectionMode.AUTO;
    private FloatingDismissWidget floatingDismissWidget;
    private boolean isWidgetExpanded = false;
    public boolean skipNotifyOnWidgetStop = false;
    private Modal modal;
    private BroadcastReceiver systemNavigationButtonTapListener = null;
    private int mode = Mode.TEXT;
    private AccessibilityNodeInfo selectedNode, closeProximitySelectedNode, imageNode;
    private int minArea, minProximityDistance;
    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private Handler mediaProjectionIdleHandler = null;
    private Runnable mediaProjectionIdlePostRunnable = null;
    private ImageReader imageReader = null;
    private ClipboardManager clipboardManager;
    private BottomBar bottomBar;
    private BrowserModal browserModal;
    private VirtualDisplay mediaProjectionDisplay;
    private boolean isAccessibilityServiceBusy;
    private ArrayList<CopiedItem> collectedTexts = null;
    final private MessageHandler messageHandler;

    private CustomOverlayView rootOverlay;
    private int currentOrientation;
    private String insetSignature = "";
    final private SharedPreferences sharedPreferences;
    private BroadcastReceiver configurationChangeReceiver;
    public FloatingWidget(NodeExplorerAccessibilityService hostingService, boolean shouldExpand, boolean showTutorial) {
        isWidgetIsShowing = true;
        sharedPreferences = hostingService.getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
        this.hostingService = hostingService;
        messageHandler = new MessageHandler(hostingService);
        showFloatingWidget(shouldExpand, showTutorial);
    }
    private ScreenInfo getScreenConfiguration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect screenBounds = windowManager.getMaximumWindowMetrics().getBounds();
            return new ScreenInfo(screenBounds.width(), screenBounds.height(), hostingService.getResources().getConfiguration().screenHeightDp);
        } else {
            DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realDisplayMetrics);
            return new ScreenInfo(realDisplayMetrics.widthPixels, realDisplayMetrics.heightPixels, realDisplayMetrics.densityDpi);
        }
    }

    private void showResult(@Nullable CopiedItem copiedItem) {
        if (copiedItem == null) {
            if (mode == Mode.TEXT)
                bottomBar.changeText(hostingService.getString(R.string.text_detection_failed));
            else
                bottomBar.changeText("Something went wrong could not capture the image");
            return;
        }

        View copyIndicator = overlayView.findViewById(R.id.text_copy_indicator);
        if (mode == Mode.CROPPED_IMAGE_CAPTURE) {
            rootOverlay.addSingleBoundingBox(copiedItem.rect);
            bottomBar.changeText("Image is saved to the gallery");
            TutorialGuide.trigger(TutorialAction.PICTURE_SELECT);
            return;
        }
        if (rootOverlay.addNewBoundingBox(collectedTexts, copiedItem)) {
            copyIndicator.setVisibility(View.VISIBLE);
            bottomBar.changeText("Text block added. Tap here to edit selection. Tap on selection block again to remove");
            if (TutorialGuide.isTutorialRunning()) {
                String resourceId = imageNode.getViewIdResourceName();
                if (resourceId != null && resourceId.contains("tutorial_hidden_text"))
                    TutorialGuide.trigger(TutorialAction.SELECT_HIDDEN_TEXT);
                else if (resourceId != null && resourceId.contains("tutorial_poster") && !copiedItem.isOCRText)
                    TutorialGuide.trigger(TutorialAction.TAP_IMAGE_WITH_CONTENT_DESCRIPTION);
                else if (collectedTexts.size() >= 2)
                    TutorialGuide.trigger(TutorialAction.MULTIPLE_TEXT_SELECT);
            }
        } else {
            bottomBar.resetTranslationIfNeeded(ArrayFunctions.map(collectedTexts, i -> i.rect));
            if (collectedTexts.isEmpty()) {
                copyIndicator.setVisibility(View.GONE);
            }
        }
    }

    void saveTextToStorageIfNeeded(@Nullable String textToStore) {
        if (textToStore == null && collectedTexts.isEmpty()) return;
        ArrayList<String> entries = new ArrayList<>();
        String text = textToStore == null ? String.join("\n", ArrayFunctions.map(collectedTexts, i -> i.text)) : textToStore;
        try {
            JSONArray inputJSONArray = new JSONArray(sharedPreferences.getString(Constants.ENTRIES_KEY, "[]"));
            for (int i = 0; i < inputJSONArray.length(); i++) {
                entries.add(inputJSONArray.getString(i));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        entries.add(0, text);
        while (entries.size() > 20)
            entries.remove(entries.size() - 1);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.ENTRIES_KEY, new JSONArray(entries).toString());
        editor.apply();
        copyTextToClipboard(text);
    }

    void copyTextToClipboard(String text) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("copied text", text));
    }
    void setupEditTextContentModal(SelectionEditorTextView selectedText, TextChangedListener textChangedListener) {
        if (TutorialGuide.trigger(TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL))
            return;
        messageHandler
                .registerReceiverOnce(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        overlayView.setVisibility(View.VISIBLE);
                        if (intent.hasExtra(Constants.SHOULD_CLOSE_MODAL)) {
                            if (intent.getBooleanExtra(Constants.SHOULD_CLOSE_MODAL, false))
                                modal.closeModal();
                            return;
                        }
                        String newText = intent.getStringExtra(Constants.PAYLOAD_EDIT_TEXT);
                        if (newText != null && !newText.trim().isEmpty()) {
                            textChangedListener.onTextChanged(newText);
                            selectedText.changeText(newText);
                        }
                    }
                }, Constants.PAYLOAD_EDIT_TEXT);
        overlayView.setVisibility(View.GONE);
        hostingService.startActivity(new Intent(hostingService, EditTextModalActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Constants.PAYLOAD_EDIT_TEXT, selectedText.getText().toString())
        );
    }
    void setupEditSelectionModal(String text, boolean isDraftText, int itemIndex) {
        modal.showModal(Modal.ModalType.EDIT_SELECTION, new Modal.ModalCallback() {
            String editedText = null;
            SelectionEditorTextView selectedText;

            @Override
            public void onHeaderBackPressed(Modal modal) {
                if (isDraftText)
                    super.onHeaderBackPressed(modal);
                else {
                    if (editedText != null)
                        showEntryList(new EditedTextEntry(!collectedTexts.isEmpty() ? itemIndex - 1 : itemIndex, editedText));
                    else
                        showEntryList(null);
                }
            }

            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                selectedText = inflatedView.findViewById(R.id.selectedText);
                selectedText.changeText(text);
                inflatedView.findViewById(R.id.action_copy_entire_text).setOnClickListener(v -> selectedText.selectAllText());
                inflatedView.findViewById(R.id.action_search_web).setOnClickListener(v -> {
                    if (TutorialGuide.trigger(TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL))
                        return;
                    modal.closeModal();
                    browserModal.show(selectedText.getSelectedText());
                });
                inflatedView.findViewById(R.id.action_copy_selected_text).setOnClickListener(v -> {
                    String text = selectedText.getSelectedText();
                    copyTextAndDismiss(text, isDraftText);
                });
                inflatedView.findViewById(R.id.action_change_text_content).setOnClickListener(view -> setupEditTextContentModal(selectedText, new TextChangedListener() {
                    @Override
                    public void onTextChanged(String text) {
                        editedText = text;
                    }
                }));
            }

            @Override
            public void onModalClosed() {
                messageHandler.unregisterReceiver(Constants.PAYLOAD_EDIT_TEXT);
            }
        });
    }


    void showEntryList(@Nullable EditedTextEntry updatedTextEntry) {
        KeyguardManager keyguardManager = (KeyguardManager) hostingService.getSystemService(Context.KEYGUARD_SERVICE);
        if(keyguardManager.isKeyguardLocked()) {
            bottomBar.changeTextAndNotify("Sorry cannot show recent items when device is locked");
            return;
        }
        modal.showModal(Modal.ModalType.ENTRY_LIST, new Modal.ModalCallback() {
            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                String jsonString = sharedPreferences.getString(Constants.ENTRIES_KEY, "[]");
                ArrayList<EntryListAdapter.Item> items = new ArrayList<>();
                if(!collectedTexts.isEmpty())
                    items.add(new EntryListAdapter.Item(String.join(
                            "\n",
                            ArrayFunctions.map(collectedTexts, i -> i.text)),
                            true)
                    );
                try {
                    JSONArray jsonArray = new JSONArray(jsonString);
                    if (updatedTextEntry != null) {
                        jsonArray.remove(updatedTextEntry.itemIndex);
                        jsonArray.put(updatedTextEntry.itemIndex, updatedTextEntry.text);
                        sharedPreferences.edit().putString(Constants.ENTRIES_KEY, jsonArray.toString()).apply();
                    }

                    for (int i = 0; i < jsonArray.length(); i++) {
                        items.add(new EntryListAdapter.Item(jsonArray.getString(i), false));
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                ListView listView = inflatedView.findViewById(R.id.entry_list);
                EntryListAdapter adapter = new EntryListAdapter(hostingService, items, new EntryListAdapter.OnTapListener() {
                    @Override
                    public void onTap(int index, EntryListAdapter.Item item) {
                        TutorialGuide.trigger(TutorialAction.SELECT_RECENT_ITEM);
                        setupEditSelectionModal(item.content, item.isDraft, index);
                    }

                    @Override
                    public void onTapCopyToClipboardShortcut(int index, EntryListAdapter.Item item) {
                        if (TutorialGuide.trigger(TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL))
                            return;
                        copyTextAndDismiss(item.content, item.isDraft);
                    }

                    @Override
                    public void onDelete(int index, EntryListAdapter entryListAdapter) {
                        items.remove(index);
                        JSONArray newArray = new JSONArray();
                        for(EntryListAdapter.Item i : items) {
                            if(!i.isDraft)
                                newArray.put(i.content);
                        }
                        sharedPreferences.edit().putString(Constants.ENTRIES_KEY, newArray.toString())
                                .apply();
                        entryListAdapter.notifyDataSetChanged();
                    }
                });
                inflatedView.findViewById(R.id.list_clear).setOnClickListener(v -> {
                    adapter.clear();
                    sharedPreferences.edit().remove(Constants.ENTRIES_KEY).apply();
                });

                listView.setEmptyView(
                        inflatedView.findViewById(R.id.list_empty)
                );
                listView.setAdapter(adapter);
            }
        });
    }

    void copyTextAndDismiss(String text, boolean shouldStoreText) {
        if (shouldStoreText)
            saveTextToStorageIfNeeded(text);
        else
            copyTextToClipboard(text);
        clearAllSelections(false);
        toggleExpandWidget();
    }

    void startCountdownToTerminateMediaProjection() {
        if (mediaProjection!= null && mediaProjectionIdleHandler == null) {
            mediaProjectionIdlePostRunnable = () -> {
                // completely shutdown if idle for some time
                mediaProjectionIdlePostRunnable = null;
                mediaProjectionIdleHandler = null;
                mediaProjection.stop();
            };
            mediaProjectionIdleHandler = new Handler();
            mediaProjectionIdleHandler.postDelayed(mediaProjectionIdlePostRunnable, 1000 * 7);
        }
    }
    private void setTextRecognitionMode(int newMode, boolean skipCountdownTermination) {
        if (newMode == mode) return;
        clearAllSelections(false);
        mode = newMode;
        bottomBar.changeMode(mode);
        ((ImageButton) overlayView.findViewById(R.id.toggleMode)).setImageResource(newMode == Mode.TEXT ? R.drawable.character : R.drawable.camera);

        if (newMode == Mode.TEXT) {
            if (!skipCountdownTermination)
                startCountdownToTerminateMediaProjection();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (imageReader == null) {
                requestMediaProjection(null);
            } else {
                // if re-visited image capture modes clear the timeout
                clearMediaProjectionIdleWatchDog();
            }
        }
    }

    private void toggleExpandWidget() {
        if (isWidgetExpanded) {
            modal.closeImmediately();
            browserModal.closeImmediately();
            clearAllSelections(true);
            widgetController.close();
            startCountdownToTerminateMediaProjection();
            toggleListenToSystemBroadcastToCollapseWidget(false);
            bottomBar.setVisibility(View.GONE);
            overlayView.findViewById(R.id.active_setting_indicator).setVisibility(View.GONE);
            textDetectionMode = TextDetectionMode.AUTO;
        } else {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R) && mode != Mode.TEXT) {
                if (imageReader == null) {
                    // the mediaProjection has stopped externally while staying on media capture modes then
                    // revert back to text mode.
                    setTextRecognitionMode(Mode.TEXT, true);
                } else {
                    // if on media capture modes and if widget expanded dismiss the current shutdown timer
                    clearMediaProjectionIdleWatchDog();
                }
            }

            bottomBar.changeMode(mode);
            toggleListenToSystemBroadcastToCollapseWidget(true);
            widgetController.open();
        }

        isWidgetExpanded = !isWidgetExpanded;
    }

    private void toggleListenToSystemBroadcastToCollapseWidget(boolean shouldListen) {
        if (shouldListen) {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            systemNavigationButtonTapListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (isWidgetExpanded) {
                        if (overlayView.getVisibility() != View.VISIBLE)
                            overlayView.setVisibility(View.VISIBLE);
                        toggleExpandWidget();
                    }
                }
            };
            ContextCompat.registerReceiver(hostingService, systemNavigationButtonTapListener, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            hostingService.unregisterReceiver(systemNavigationButtonTapListener);
            systemNavigationButtonTapListener = null;
        }
    }

    private boolean clearAllSelections(boolean shouldStoreLastText) {
        bottomBar.resetTranslation();
        if (mode == Mode.CROPPED_IMAGE_CAPTURE) {
            return rootOverlay.clearAllSelections();
        }
        rootOverlay.clearAllSelections();
        if (collectedTexts.isEmpty()) return true;
        if (shouldStoreLastText)
            saveTextToStorageIfNeeded(null);
        collectedTexts.clear();
        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);
        return false;
    }

    private void onOrientationChanged(Context context, WindowManager.LayoutParams params) {
        int newOrientation = context.getResources().getConfiguration().orientation;
        boolean didOrientationChanged = newOrientation != currentOrientation;
        currentOrientation = newOrientation;
        if (didOrientationChanged) {
            if (isWidgetExpanded) {
                clearAllSelections(false);
                modal.reLayout();
            }
            TutorialGuide.relayout();
            if (mediaProjectionDisplay != null) {
                ScreenInfo screenInfo = getScreenConfiguration();
                mediaProjectionDisplay.resize(screenInfo.width, screenInfo.height, screenInfo.densityDpi);
                imageReader.close();
                imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1);
                mediaProjectionDisplay.setSurface(imageReader.getSurface());
            }
        }

        ViewGroup buttonContainer = overlayView.findViewById(R.id.buttonContainer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String newInsetSignature = windowManager.getMaximumWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars()).toString();
            if(!insetSignature.equals(newInsetSignature) || didOrientationChanged) {
                FrameLayout.LayoutParams buttonContainerParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
                widgetController.configureOverlayDimensions(buttonContainerParams, isWidgetExpanded, didOrientationChanged, true);
            }
            insetSignature = newInsetSignature;
        } else if (didOrientationChanged) {
            if(isWidgetExpanded)
                buttonContainer.setTranslationX(0);
            else {
                params.x = 0;
                windowManager.updateViewLayout(overlayView, params);
            }
        }
    }

    public void changeWidgetPositionOrientation(boolean isWidgetLeftOriented) {
        widgetController.hotReloadWidgetOrientation(isWidgetLeftOriented);
    }

    public void startTutorial() {
        TutorialGuide.start(this, rootOverlay, sharedPreferences, hostingService);
    }

    public void collapseWidget() {
        if (isWidgetExpanded)
            toggleExpandWidget();
    }
    private void showFloatingWidget(boolean shouldExpand, boolean showTutorial) {
        windowManager = (WindowManager) hostingService.getSystemService(Context.WINDOW_SERVICE);
        clipboardManager = (ClipboardManager) hostingService.getSystemService(Context.CLIPBOARD_SERVICE);

        // Inflate overlay layout
        overlayView = (ViewGroup) LayoutInflater.from(hostingService).inflate(R.layout.overlay_layout, null);
        modal = new Modal(overlayView);

        currentOrientation = hostingService.getResources().getConfiguration().orientation;
        NODE_PROXIMITY_THRESHOLD = hostingService.getResources().getDimensionPixelSize(R.dimen.node_capture_proximity_radius);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Insets navigationBarInset = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars());
            insetSignature = navigationBarInset.toString();
        }
        collectedTexts = new ArrayList<>();
        floatingDismissWidget = new FloatingDismissWidget(windowManager, overlayView, hostingService);
        // Initial WindowManager params (clicks pass through the overlay)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setFitInsetsIgnoringVisibility(true);
        }

        configurationChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onOrientationChanged(context, params);
            }
        };

        hostingService.registerReceiver(configurationChangeReceiver, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        if (Build.VERSION.SDK_INT >= 34) {
            params.setCanPlayMoveAnimation(false);
        } else {
            try {
                Class<WindowManager.LayoutParams> layoutParamsClass = WindowManager.LayoutParams.class;
                Field privateFlags = layoutParamsClass.getField("privateFlags");
                Field noAnim = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION");
                int privateFlagsValue = privateFlags.getInt(params);
                int noAnimFlag = noAnim.getInt(params);
                privateFlagsValue |= noAnimFlag;
                privateFlags.setInt(params, privateFlagsValue);
            } catch (Exception e) {
                FirebaseCrashlytics.getInstance().recordException(e);
            }
        }
        configureEnableButton(params);
        String lastLocationInfoJSONString = sharedPreferences.getString(Constants.WIDGET_LAST_LOCATION_INFO, null);
        WidgetLocationCoordinate widgetLocationCoordinate;
        try {
            widgetLocationCoordinate = new WidgetLocationCoordinate(lastLocationInfoJSONString, hostingService);
            if (lastLocationInfoJSONString != null && widgetLocationCoordinate.isSameAsDefault())
                sharedPreferences.edit().remove(Constants.WIDGET_LAST_LOCATION_INFO).apply();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        widgetController = new WidgetController(hostingService, windowManager, params, overlayView);
        if (shouldExpand) {
            toggleListenToSystemBroadcastToCollapseWidget(true);
            isWidgetExpanded = true;
            widgetController.prepareInitialState(true, widgetLocationCoordinate);
        } else {
            widgetController.prepareInitialState(false, widgetLocationCoordinate);
        }

        // Add the overlay view
        windowManager.addView(overlayView, params);
        // Find the button in the overlay
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);
        browserModal = new BrowserModal(rootOverlay);

        ImageButton listButton = overlayView.findViewById(R.id.list);
        ImageButton stopButton = overlayView.findViewById(R.id.stop);
        ImageButton settingsButton = overlayView.findViewById(R.id.textCaptureMode);
        ImageButton eraserButton = overlayView.findViewById(R.id.eraser);
        eraserButton.setOnClickListener(v -> {
            if(TutorialGuide.trigger(TutorialAction.ERASE_ALL_SELECTIONS))
                return;
            if (clearAllSelections(false))
                bottomBar.changeText("No text selections to clear");
        });
        bottomBar = overlayView.findViewById(R.id.text_hint);
        bottomBar.setOnTapListener(new BottomBar.OnTapListener() {
            @Override
            public void onTap() {
                if (collectedTexts.isEmpty()) return;
                if (TutorialGuide.trigger(TutorialAction.BOTTOM_BAR_SELECT))
                    return;
                String collectiveText = String.join("\n", ArrayFunctions.map(collectedTexts, i -> i.text));
                setupEditSelectionModal(collectiveText, true, -1);
            }
        });
        overlayView.findViewById(R.id.toggleMode).setOnClickListener(v -> {
            if (TutorialGuide.trigger(TutorialAction.SWITCH_MODE))
                return;
            setTextRecognitionMode(mode == Mode.TEXT ? Mode.CROPPED_IMAGE_CAPTURE : Mode.TEXT, false);
        });
        stopButton.setOnClickListener(v -> onStopWidget());
        listButton.setOnClickListener(v -> {
            if (TutorialGuide.trigger(TutorialAction.HISTORY_BUTTON_SELECT))
                return;
            showEntryList(null);
        });
        settingsButton.setOnClickListener(v -> {
            if (TutorialGuide.trigger(TutorialAction.OPEN_QUICK_SETTINGS))
                return;
            modal.showModal(Modal.ModalType.SETTINGS, new Modal.ModalCallback() {
                @Override
                public void onBeforeModalShown(ViewGroup inflatedView) {
                    TabSelector tabSelector = inflatedView.findViewById(R.id.mode_tab_selector);
                    tabSelector.setup(
                            hostingService.getResources().getStringArray(R.array.text_mode_descriptions),
                            textDetectionMode,
                            index -> {
                                overlayView.findViewById(R.id.active_setting_indicator).setVisibility(index > 0 ? View.VISIBLE : View.GONE);
                                textDetectionMode = index;
                                if (index == 1)
                                    TutorialGuide.trigger(TutorialAction.SELECT_TEXT_ONLY_MODE);
                                modal.closeModal();
                            });
                }
            });
        });

        // Handle touch events on the overlay
        rootOverlay.setOnTapListener(new CustomOverlayView.OnTapListener() {
            @Override
            public void onTap(CoordinateF tappedCoordinate, boolean isLongPress) {
                // Record tap coordinates
                if (isAccessibilityServiceBusy) {
                    bottomBar.changeTextAndNotify("Sorry, we are still busy looking for text element you tapped previously");
                    return;
                }

                if (!isLongPress) {
                    int removedIndex = rootOverlay.removeSelection(tappedCoordinate.x, tappedCoordinate.y);
                    if (removedIndex != -1) {
                        if (mode == Mode.TEXT) {
                            collectedTexts.remove(removedIndex);
                            bottomBar.resetTranslationIfNeeded(ArrayFunctions.map(collectedTexts, (i) -> i.rect));
                            if (collectedTexts.isEmpty())
                                overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);
                        } else
                            bottomBar.resetTranslation();
                        return;
                    }
                }

                overlayView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                        overlayView.removeOnLayoutChangeListener(this);
                        getNodeResult(tappedCoordinate.x, tappedCoordinate.y, isLongPress, new NodeCapturedCallback() {
                            @Override
                            void onResult(@Nullable NodeResult result) {
                                // ...regain focus to overlay view to capture key event on system navigation back press
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                                windowManager.updateViewLayout(overlayView, params);

                                if (isLongPress) {
                                    if (result != null) {
                                        if (TutorialGuide.trigger(TutorialAction.LONG_PRESS_WORD)) return;
                                        browserModal.show(result.text);
                                    }
                                    isAccessibilityServiceBusy = false;
                                    return;
                                }

                                if (result == null) {
                                    showResult(null);
                                    isAccessibilityServiceBusy = false;
                                    return;
                                }

                                if (mode == Mode.CROPPED_IMAGE_CAPTURE) {
                                    captureScreenshot(result.bound, (int) tappedCoordinate.x, (int) tappedCoordinate.y, false, null);
                                } else {
                                    showResult(new CopiedItem(result.text, result.bound, result.isOCRText));
                                    isAccessibilityServiceBusy = false;
                                }
                            }
                        });
                    }
                });

                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlayView, params);
            }

            @Override
            public void onMove(float dragDistance) {
                if (dragDistance > 50) {
                    if (!TutorialGuide.isTutorialRunning())
                        toggleExpandWidget();
                } else {
                    bottomBar.changeText(hostingService.getString(R.string.text_hint_close_overlay));
                }
            }
        });

        rootOverlay.setOnDismissListener(new CustomOverlayView.OnDismissListener() {
            @Override
            protected void onDismiss() {
                if(isWidgetExpanded
                        && !modal.handleSystemGoBack()
                        && !browserModal.close()
                        && !TutorialGuide.trigger(TutorialAction.GENERAL_FORHIBIDDEN_ACTION_ON_TUTORIAL)
                )
                    toggleExpandWidget();
            }
        });
        notifyStateOnQuickTile(true);

        if(showTutorial) {
            new Handler().postDelayed(() -> {
                TutorialGuide.start(this, rootOverlay, sharedPreferences, hostingService);
            }, 200);
        }
    }

    private void configureEnableButton(WindowManager.LayoutParams params) {
        EnableButton enableButton = overlayView.findViewById(R.id.toggleCollapse);
        enableButton.configure(new EnableButton.TouchDelegateListener() {
            @Override
            public void onTap(CoordinateF tappedCoordinate) {
                toggleExpandWidget();
            }

            @Override
            public void onDragGestureStarted() {
                if (isWidgetExpanded)
                    TutorialGuide.trigger(TutorialAction.MOVE_WIDGET_BUTTON);
                else
                    floatingDismissWidget.onGestureStarted();
            }

            @Override
            public void onRelease() {
                TutorialGuide.trigger(TutorialAction.WIDGET_MOVE_FINISHED);
                if(isWidgetExpanded || !floatingDismissWidget.onGestureReleased(enableButton, params))
                    widgetController.updateLastDownCoordinate();
            }
        }, overlayView, windowManager, params);
    }

    private void setIsProcessing(boolean isProcessing) {
        overlayView.setClickable(!isProcessing);
        if (!isProcessing)
            isAccessibilityServiceBusy = false;
        overlayView.findViewById(R.id.buttonContainer).setVisibility(isProcessing ? View.GONE : View.VISIBLE);
        if (isProcessing) {
            View loadingIndicator = overlayView.findViewById(R.id.loading);
            widgetController.syncLoadingIndicatorPosition();
            loadingIndicator.setVisibility(View.VISIBLE);
        } else
            overlayView.findViewById(R.id.loading).setVisibility(View.GONE);
    }

    private void submitCaptureImageToExternalStorage(Bitmap capturedImage) {
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault());
        String displayName = timeStamp.format(new Date()) + ".jpg";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            OutputStream fos;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES); // Gallery folder
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                ContentResolver resolver = hostingService.getContentResolver();
                Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri imageUri = resolver.insert(collection, values);

                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri);
                    capturedImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush();
                    fos.close();

                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(imageUri, values, null, null);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            if (ContextCompat.checkSelfPermission(hostingService, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                bottomBar.changeTextAndNotify("Storage permission has not granted. Please go to settings and grant storage permissions.");
                return;
            }
            String imagesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
            ).toString();

            File folder = new File(imagesDir);

            File image = new File(folder, displayName);
            try {
                FileOutputStream out = new FileOutputStream(image);
                capturedImage.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private NodeResult getNearByTextBlocks(Text.TextBlock tappedTextBlock, List<Text.TextBlock> textBlocks, Coordinate offset) {
        String capturedText = tappedTextBlock.getText();
        Rect proposedRect = new Rect(tappedTextBlock.getBoundingBox());
        List<Text.Line> lines = tappedTextBlock.getLines();
        float sumHeight = 0;
        for (Text.Line line : lines) {
            sumHeight += line.getBoundingBox().height();
        }
        float averageLineHeight = sumHeight / lines.size();

        boolean isCloseProximityBlockAvailable;
        do {
            isCloseProximityBlockAvailable = false;
            for (Text.TextBlock block : textBlocks) {
                Rect blockBoundingBox = block.getBoundingBox();
                if (blockBoundingBox != null &&
                        blockBoundingBox.bottom <= proposedRect.top &&
                        (
                                blockBoundingBox.left < proposedRect.right && proposedRect.left < blockBoundingBox.right
                                        && blockBoundingBox.top < proposedRect.bottom && proposedRect.top < (blockBoundingBox.bottom + NODE_PROXIMITY_THRESHOLD)
                        )
                ) {
                    float differanceRatio = block.getLines().get(0).getBoundingBox().height() / averageLineHeight;
                    if (differanceRatio > 1.25f || differanceRatio < 0.75f)
                        continue;
                    if (blockBoundingBox.left < proposedRect.left)
                        proposedRect.left = blockBoundingBox.left;
                    if (blockBoundingBox.right > proposedRect.right)
                        proposedRect.right = blockBoundingBox.right;
                    proposedRect.top = blockBoundingBox.top;
                    capturedText = block.getText() + "\n" + capturedText;
                    isCloseProximityBlockAvailable = true;
                    break;
                }
            }
        } while (isCloseProximityBlockAvailable);

        do {
            isCloseProximityBlockAvailable = false;
            for (Text.TextBlock block : textBlocks) {
                Rect blockBoundingBox = block.getBoundingBox();
                if (blockBoundingBox != null &&
                        blockBoundingBox.top >= proposedRect.bottom &&
                        (
                                blockBoundingBox.left < proposedRect.right && proposedRect.left < blockBoundingBox.right
                                        && (blockBoundingBox.top - NODE_PROXIMITY_THRESHOLD) < proposedRect.bottom && proposedRect.top < blockBoundingBox.bottom
                        )
                ) {
                    float differanceRatio = block.getLines().get(0).getBoundingBox().height() / averageLineHeight;
                    if (differanceRatio > 1.25f || differanceRatio < 0.75f)
                        continue;
                    if (blockBoundingBox.left < proposedRect.left)
                        proposedRect.left = blockBoundingBox.left;
                    if (blockBoundingBox.right > proposedRect.right)
                        proposedRect.right = blockBoundingBox.right;
                    proposedRect.bottom = blockBoundingBox.bottom;
                    capturedText = capturedText + "\n" + block.getText();
                    isCloseProximityBlockAvailable = true;
                    break;
                }
            }
        } while (isCloseProximityBlockAvailable);
        proposedRect.offset(offset.x, offset.y);

        return new NodeResult(proposedRect, capturedText);
    }

    void processScreenshot(Rect bounds, int tappedCoordinateX, int tappedCoordinateY, Bitmap screenBitmap, boolean isWebSearch, ImageRecognitionResult imageRecognitionResult) {
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        bounds.bottom = Math.min(bounds.bottom, screenBitmap.getHeight());
        bounds.top = Math.max(bounds.top, 0);
        if (bounds.left < 0)
            bounds.left = 0;
        if (bounds.left + bounds.width() > screenBitmap.getWidth())
            bounds.right = screenBitmap.getWidth() - bounds.left;

        Bitmap croppedImage = Bitmap.createBitmap(screenBitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
        if (mode == Mode.CROPPED_IMAGE_CAPTURE && !isWebSearch) {
            submitCaptureImageToExternalStorage(croppedImage);
            showResult(new CopiedItem(bounds));
            setIsProcessing(false);
            return;
        }
        setIsProcessing(true);
        recognizer.process(
                        InputImage.fromBitmap(croppedImage, 0)
                )
                .addOnSuccessListener(result -> {
                    List<Text.TextBlock> textBlocks = result.getTextBlocks();
                    Text.TextBlock selectedCandidate = null;
                    Text.TextBlock closeProximityCandidate = null;
                    int minProximityDistance = Integer.MAX_VALUE;
                    Coordinate relativeTapLocation = new Coordinate(tappedCoordinateX - bounds.left, tappedCoordinateY - bounds.top);
                    for (Text.TextBlock block: textBlocks) {
                        Rect trialBoundingBox = block.getBoundingBox();
                        if (trialBoundingBox != null) {
                            if (trialBoundingBox.contains(relativeTapLocation.x, relativeTapLocation.y)) {
                                if (isWebSearch) {
                                    List<Text.Line> lines = block.getLines();
                                    for (Text.Line line : lines) {
                                        Rect lineBoundingBox = line.getBoundingBox();
                                        if(lineBoundingBox != null && lineBoundingBox.contains(relativeTapLocation.x, relativeTapLocation.y)) {
                                            List<Text.Element> words = line.getElements();
                                            for (Text.Element word : words) {
                                                Rect wordBoundingBox = word.getBoundingBox();
                                                if (wordBoundingBox != null && wordBoundingBox.contains(relativeTapLocation.x, relativeTapLocation.y)) {
                                                    wordBoundingBox.offset(bounds.left, bounds.top);
                                                    String wordText = word.getText();

                                                    if (!wordText.isEmpty())
                                                        imageRecognitionResult.onResult(true, new NodeResult(wordBoundingBox, wordText));
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                    imageRecognitionResult.onResult(false, new NodeResult(null, ""));
                                    return;
                                }
                                selectedCandidate = block;
                            } else {
                                int proximity = getNodeProximityToCoordinate(trialBoundingBox, tappedCoordinateX - bounds.left, tappedCoordinateY - bounds.top);
                                if (proximity <= NODE_PROXIMITY_THRESHOLD && proximity < minProximityDistance) {
                                    minProximityDistance = proximity;
                                    closeProximityCandidate = block;
                                }
                            }
                        }
                    }

                    if (selectedCandidate != null) {
                        imageRecognitionResult.onResult(true, getNearByTextBlocks(selectedCandidate, textBlocks, new Coordinate(bounds.left, bounds.top)));
                        return;
                    }
                    if (closeProximityCandidate != null) {
                        imageRecognitionResult.onResult(true, getNearByTextBlocks(closeProximityCandidate, textBlocks, new Coordinate(bounds.left, bounds.top)));
                        return;
                    }
                    List<Text.TextBlock> blocks = result.getTextBlocks();
                    if (blocks.isEmpty()) {
                        imageRecognitionResult.onResult(false, null);
                        return;
                    }
                    Rect maximumBound = new Rect(Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1);
                    for (Text.TextBlock block : blocks) {
                        Rect blockBoundingRect = block.getBoundingBox();
                        if (blockBoundingRect != null) {
                            blockBoundingRect.offset(bounds.left, bounds.top);
                            if (blockBoundingRect.left < maximumBound.left)
                                maximumBound.left = blockBoundingRect.left;
                            if (blockBoundingRect.top < maximumBound.top)
                                maximumBound.top = blockBoundingRect.top;
                            if (blockBoundingRect.right > maximumBound.right)
                                maximumBound.right = blockBoundingRect.right;
                            if (blockBoundingRect.bottom > maximumBound.bottom)
                                maximumBound.bottom = blockBoundingRect.bottom;
                        }
                    }
                    imageRecognitionResult.onResult(false, new NodeResult(maximumBound, result.getText()));
                }).addOnCompleteListener(tsk -> setIsProcessing(false));
    }

    void changeOverlayContentVisibility(boolean isVisible) {
        overlayView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }
    void captureScreenshot(@Nullable Rect bounds, int tappedCoordinateX, int tappedCoordinateY, boolean isWebSearch, ImageRecognitionResult imageRecognitionResult) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (imageReader == null) {
                requestMediaProjection(() -> captureScreenshot(bounds, tappedCoordinateX, tappedCoordinateY, isWebSearch, imageRecognitionResult));
                return;
            }
            clearMediaProjectionIdleWatchDog();
        }
        if (bounds == null) {
            if (mode == Mode.CROPPED_IMAGE_CAPTURE)
                showResult(null);
            else
                imageRecognitionResult.onResult(false, null);
            isAccessibilityServiceBusy = false;
            return;
        }

        changeOverlayContentVisibility(false);
        new Handler().postDelayed(() -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hostingService.takeScreenshot(Display.DEFAULT_DISPLAY, hostingService.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull AccessibilityService.ScreenshotResult screenshotResult) {
                        changeOverlayContentVisibility(true);
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                        if (bitmap != null) {
                            assert bounds != null;
                            processScreenshot(
                                    bounds,
                                    tappedCoordinateX,
                                    tappedCoordinateY,
                                    bitmap,
                                    isWebSearch,
                                    imageRecognitionResult
                            );
                        }
                        else {
                            changeOverlayContentVisibility(true);
                            isAccessibilityServiceBusy = false;
                        }
                    }

                    @Override
                    public void onFailure(int i) {
                        changeOverlayContentVisibility(true);
                    }
                });
            } else {
                Image image = imageReader.acquireLatestImage();
                changeOverlayContentVisibility(true);
                Image.Plane plane = image.getPlanes()[0];

                Bitmap screenBitmap = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(),
                        image.getHeight(), Bitmap.Config.ARGB_8888);
                screenBitmap.copyPixelsFromBuffer(plane.getBuffer());
                image.close();
                processScreenshot(bounds, tappedCoordinateX, tappedCoordinateY, screenBitmap, isWebSearch, imageRecognitionResult);
            }
        }, 100);
    }

    private void requestMediaProjection(@Nullable Runnable postCallback) {
        overlayView.setVisibility(View.GONE);
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(overlayView, params);

        mediaProjectionManager =
                (MediaProjectionManager) hostingService.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        Intent activityIntent = new Intent(hostingService, MediaProjectionRequestActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra(Constants.CURRENT_CAPTURE_MODE, mode);
        activityIntent.putExtra(Constants.SCREEN_CAPTURE_INTENT, screenCaptureIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                mode == Mode.CROPPED_IMAGE_CAPTURE
                && ContextCompat.checkSelfPermission(hostingService, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activityIntent.putExtra(Constants.REQUEST_STORAGE_PERMISSION, true);
        }

        messageHandler.registerReceiverOnce(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                windowManager.updateViewLayout(overlayView, params);
                overlayView.setVisibility(View.VISIBLE);
                int resultCode = intent == null ? Activity.RESULT_CANCELED : intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                if(resultCode != Activity.RESULT_OK) {
                    // if user rejected the media projection or storage permission request revert back to text mode
                    if (isAccessibilityServiceBusy)
                        isAccessibilityServiceBusy = false;
                    setTextRecognitionMode(Mode.TEXT, true);
                    TutorialGuide.cancelTrigger(TutorialAction.SWITCH_MODE);
                    return;
                }
                Intent data = intent.getParcelableExtra("data");
                String notificationTitle = "Capturing Screenshots";
                String notificationContentDescription = "session to capture screenshots";
                NotificationManager notificationManager = (NotificationManager) hostingService.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.createNotificationChannel(new NotificationChannel("screen_capture", "Screen Capture Session", NotificationManager.IMPORTANCE_DEFAULT));
                    hostingService.startForeground(100, new Notification.Builder(hostingService, "screen_capture").
                            setContentTitle(notificationTitle)
                            .setContentText(notificationContentDescription)
                            .build()
                    );
                } else {
                    hostingService.startForeground(100, new Notification.Builder(hostingService).
                            setContentTitle(notificationTitle)
                            .setContentText(notificationContentDescription)
                            .build()
                    );
                }
                assert data != null;
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                ScreenInfo screenInfo = getScreenConfiguration();

                imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1);
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        super.onStop();
                        mediaProjectionDisplay.release();
                        imageReader.close();
                        mediaProjection = null;
                        mediaProjectionDisplay = null;
                        imageReader = null;
                        hostingService.stopForeground(true);
                        clearMediaProjectionIdleWatchDog();
                        if (mode != Mode.TEXT && overlayView != null && isWidgetExpanded) {
                            setTextRecognitionMode(Mode.TEXT, true);
                        }
                    }
                }, null);
                mediaProjectionDisplay = mediaProjection.createVirtualDisplay(
                        "screenCapture",
                        screenInfo.width,
                        screenInfo.height,
                        screenInfo.densityDpi,  // screen density (adjust as needed)
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null
                );
                if (postCallback != null) {
                    postCallback.run();
                }
            }
        }, Constants.MEDIA_PROJECTION_DATA);
        hostingService.startActivity(activityIntent);
    }

    private void notifyStateOnQuickTile(boolean newState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && newState != sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false)) {
            ShortcutTileLauncher.expectedChange = newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            ShortcutTileLauncher.requestListeningState(hostingService, new ComponentName(hostingService, ShortcutTileLauncher.class));
        }
    }

    private int getNodeProximityToCoordinate(Rect nodeBound, int coordinateX, int coordinateY) {
        if (nodeBound.left <= coordinateX && nodeBound.right >= coordinateX) {
            return nodeBound.top > coordinateY ? nodeBound.top - coordinateY : coordinateY - nodeBound.bottom;
        }
        if (nodeBound.top <= coordinateY && nodeBound.bottom >= coordinateY) {
            return nodeBound.right < coordinateX ? coordinateX - nodeBound.right : nodeBound.left - coordinateX;
        }
        return NODE_PROXIMITY_THRESHOLD + 1;
    }

    private void isInsideElement(AccessibilityNodeInfo node, int coordinateX, int coordinateY) {
        if (node == null) return;
        Rect nodeBound = new Rect();
        node.getBoundsInScreen(nodeBound);
        if (nodeBound.contains(coordinateX, coordinateY) && node.isVisibleToUser()) {
            int area = nodeBound.height() * nodeBound.width();
            if (area <= minArea) {
                if (getText(node) != null)
                    selectedNode = node;
                imageNode = node;
                minArea = area;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        } else {
            if ((nodeBound.top == nodeBound.bottom) || (nodeBound.left == nodeBound.right)) {
                for (int i = 0; i < node.getChildCount(); i++) {
                    isInsideElement(node.getChild(i), coordinateX, coordinateY);
                }
            } else {
                int proximity = getNodeProximityToCoordinate(nodeBound, coordinateX, coordinateY);
                if (proximity < NODE_PROXIMITY_THRESHOLD && node.isVisibleToUser()) {
                    if (proximity <= minProximityDistance) {
                        TextResult textResult = getText(node);
                        if (textResult != null && textResult.type == TextResult.TEXT) {
                            closeProximitySelectedNode = node;
                            minProximityDistance = proximity;
                        }

                        for (int i = 0; i < node.getChildCount(); i++) {
                            isInsideElement(node.getChild(i), coordinateX, coordinateY);
                        }
                    }
                }
            }
        }
    }
    public static class TextResult {
        public static int TEXT = 1;
        public static int CONTENT_DESCRIPTION = 2;
        public int type;
        public String text;

        public TextResult(String text, int type) {
            this.text = text;
            this.type = type;
        }

        public TextResult() {
            type = TEXT;
            text = "";
        }
    }
    private @Nullable TextResult getText(@NonNull AccessibilityNodeInfo node) {
        CharSequence nodeText = node.getText();
        if (nodeText != null) {
            String nodeTextString = nodeText.toString();
            if (!nodeTextString.trim().isEmpty())
                return new TextResult(nodeText.toString(), TextResult.TEXT);
        }

        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            String contentDescriptionString = contentDescription.toString();
            if (!contentDescriptionString.trim().isEmpty())
                return new TextResult(contentDescriptionString, TextResult.CONTENT_DESCRIPTION);
        }
        return null;
    }

    private void freeSearch(AccessibilityNodeInfo node) {
        TextResult textResult = getText(node);
        if (textResult != null) {
            Log.d("text", node.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            freeSearch(node.getChild(i));
        }
    }

    private void clearMediaProjectionIdleWatchDog() {
        if (mediaProjectionIdleHandler != null) {
            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
            mediaProjectionIdlePostRunnable = null;
            mediaProjectionIdleHandler = null;
        }
    }

    public void releaseResources() {
        if (systemNavigationButtonTapListener != null)
            hostingService.unregisterReceiver(systemNavigationButtonTapListener);
        hostingService.unregisterReceiver(configurationChangeReceiver);
        TutorialGuide.clearResources();
        messageHandler.clearAllExcept(Constants.ACCESSIBILITY_SERVICE);
        floatingDismissWidget.clearResource();
        widgetController.saveWidgetLocation(sharedPreferences);
        windowManager.removeView(overlayView);
        overlayView = null;

        saveTextToStorageIfNeeded(null);

        clearMediaProjectionIdleWatchDog();
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (!skipNotifyOnWidgetStop)
            notifyStateOnQuickTile(false);
    }
    private void onStopWidget() {
        if (TutorialGuide.trigger(TutorialAction.STOP_WIDGET)) return;
        hostingService.onStopWidget();
    }

    public static class NodeResult {
        public Rect bound;
        public String text;
        public boolean isOCRText = false;

        NodeResult(@Nullable Rect bound, String text) {
            this.bound = bound;
            this.text = text;
        }

        NodeResult(@Nullable Rect bound, String text, boolean isOCRText) {
            this.bound = bound;
            this.text = text;
            this.isOCRText = isOCRText;
        }
    }
    static abstract class NodeCapturedCallback {
        abstract void onResult(@Nullable NodeResult result);

        public void submit(@Nullable NodeResult result) {
            new Handler(Looper.getMainLooper()).post(() -> onResult(result));
        }
    }

    public interface ImageRecognitionResult {
        void onResult(boolean isIntercepted, @Nullable NodeResult result);
    }

    private String captureTappedWord(String wholeText, Parcelable[] characterBlocks, CoordinateF coordinate) {
        int tappedIndex = -1;
        for (int i = 0; i < characterBlocks.length; i++) {
            if (characterBlocks[i] == null) continue;
            RectF characterBound = (RectF) characterBlocks[i];
            if (coordinate.x <= characterBound.right && coordinate.y <= characterBound.bottom) {
                tappedIndex = i;
                break;
            }
        }


        if (tappedIndex != -1) {
            int startIndex = 0;
            for (int i = 0; i < wholeText.length(); i++) {
                char character = wholeText.charAt(i);
                boolean isSpaceChar = Character.isWhitespace(character) || character == '.' || character == ',';
                if (isSpaceChar) {
                    if (tappedIndex == i)
                        return null;
                    if (tappedIndex < i) {
                        return wholeText.substring(startIndex, i);
                    }
                    startIndex = i + 1;
                }
            }

            return wholeText.substring(startIndex);
        }
        return null;
    }

    private void getNodeResult(float x, float y, boolean isWebSearch, NodeCapturedCallback resultCallback) {
        isAccessibilityServiceBusy = true;
        new Thread(() -> {
            selectedNode = null;
            closeProximitySelectedNode = null;
            imageNode = null;
            minArea = Integer.MAX_VALUE;
            minProximityDistance = Integer.MAX_VALUE;
            AccessibilityNodeInfo root = hostingService.getRootInActiveWindow();
//            freeSearch(root);
            isInsideElement(root, (int) x, (int) y);
            Rect bounds = new Rect();
            if (mode == Mode.CROPPED_IMAGE_CAPTURE && !isWebSearch) {
                if (imageNode == null) {
                    resultCallback.submit(null);
                    return;
                }
                imageNode.getBoundsInScreen(bounds);
                resultCallback.submit(new NodeResult(bounds, ""));
                return;
            }

            TextResult selectedNodeResult = selectedNode == null ? null : getText(selectedNode);

            if (textDetectionMode == TextDetectionMode.TEXT && !isWebSearch) {
                if (
                        (selectedNodeResult == null || selectedNodeResult.type == TextResult.CONTENT_DESCRIPTION)
                        && closeProximitySelectedNode != null
                ) {
                    closeProximitySelectedNode.getBoundsInScreen(bounds);
                    resultCallback.submit(new NodeResult(bounds, closeProximitySelectedNode.getText().toString()));
                    return;
                }
                if (selectedNodeResult == null) {
                    resultCallback.submit(null);
                    return;
                }
                selectedNode.getBoundsInScreen(bounds);
                resultCallback.submit(new NodeResult(bounds, selectedNodeResult.text));
                return;
            }


            if (selectedNodeResult != null && selectedNodeResult.type == TextResult.TEXT
                && selectedNode.getChildCount() == 0
                    && (isWebSearch || textDetectionMode == TextDetectionMode.AUTO)
            ) {
                String[] classNameComponents = selectedNode.getClassName().toString().split("\\.");
                String widgetType = classNameComponents[classNameComponents.length - 1].toLowerCase();
                if (!widgetType.contains("image") &&
                        //  buttons are portrait size could potentially has an image
                        !(widgetType.contains("button") && bounds.height() > bounds.width())) {
                    selectedNode.getBoundsInScreen(bounds);
                    if (isWebSearch) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            List<String> availableData = selectedNode.getAvailableExtraData();
                            boolean hasCharacterLocation = false;
                            for (String extra : availableData) {
                                if (extra.equals(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) {
                                    hasCharacterLocation = true;
                                    break;
                                }
                            }
                            if (hasCharacterLocation) {
                                Bundle args = new Bundle();
                                args.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0);
                                args.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, selectedNodeResult.text.length());
                                selectedNode.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args);
                                Parcelable[] characterBounds = selectedNode.getExtras().getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
                                assert characterBounds != null;
                                String detectedWord = captureTappedWord(selectedNodeResult.text, characterBounds, new CoordinateF(x, y));
                                resultCallback.submit(detectedWord == null ? null : new NodeResult(null, detectedWord));
                                return;
                            }
                        }
                    } else {
                        resultCallback.submit(new NodeResult(bounds, selectedNodeResult.text));
                    }
                    return;
                }
            }

            // can possibly be an image
            if (isWebSearch || imageNode != null && (
                    textDetectionMode == TextDetectionMode.TEXT_RECOGNITION
                    || imageNode.getChildCount() == 0
                    || closeProximitySelectedNode == null)) {
                imageNode.getBoundsInScreen(bounds);
                new Handler(Looper.getMainLooper()).post(() -> captureScreenshot(bounds,(int) x,(int) y, isWebSearch, (isIntercepted, result) -> {
                    if (isWebSearch) {
                        if (result != null && isIntercepted) {
                            resultCallback.onResult(new NodeResult(null, result.text));
                        } else
                            resultCallback.onResult(null);
                        return;
                    }
                    if (result != null) {
                        if (isIntercepted || (
                                textDetectionMode == TextDetectionMode.TEXT_RECOGNITION ||
                                closeProximitySelectedNode == null
                        )) {
                            resultCallback.onResult(
                                    new NodeResult(result.bound, result.text, true)
                            );
                            return;
                        }
                    }
                    if (textDetectionMode == TextDetectionMode.TEXT_RECOGNITION) {
                        resultCallback.submit(null);
                        return;
                    }
                    if (closeProximitySelectedNode != null) {
                        closeProximitySelectedNode.getBoundsInScreen(bounds);
                        resultCallback.onResult(
                                new NodeResult(bounds, closeProximitySelectedNode.getText().toString())
                        );
                        return;
                    }
                    if (selectedNodeResult != null) {
                        selectedNode.getBoundsInScreen(bounds);
                        resultCallback.onResult(
                                new NodeResult(bounds, selectedNodeResult.text)
                        );
                    } else {
                        resultCallback.onResult(null);
                    }
                }));
                return;
            }

            if (textDetectionMode == TextDetectionMode.AUTO) {
                if (closeProximitySelectedNode != null) {
                    closeProximitySelectedNode.getBoundsInScreen(bounds);
                    resultCallback.submit(new NodeResult(bounds, closeProximitySelectedNode.getText().toString()));
                    return;
                } else if (selectedNodeResult != null) {
                    selectedNode.getBoundsInScreen(bounds);
                    resultCallback.submit(new NodeResult(bounds, selectedNodeResult.text));
                    return;
                }
            }
            resultCallback.submit(null);
        }).start();
    }
}
