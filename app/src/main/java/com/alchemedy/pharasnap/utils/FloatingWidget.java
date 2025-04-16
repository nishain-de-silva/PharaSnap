package com.alchemedy.pharasnap.utils;

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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Gravity;
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
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.helper.EditedTextEntry;
import com.alchemedy.pharasnap.helper.MessageHandler;
import com.alchemedy.pharasnap.helper.ScreenInfo;
import com.alchemedy.pharasnap.helper.TextChangedListener;
import com.alchemedy.pharasnap.services.NodeExplorerAccessibilityService;
import com.alchemedy.pharasnap.services.ShortcutTileLauncher;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;
import com.alchemedy.pharasnap.widgets.SelectionEditorTextView;
import com.alchemedy.pharasnap.widgets.TextHint;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class FloatingWidget {
    final private NodeExplorerAccessibilityService hostingService;
    private WindowManager windowManager;
    private View overlayView;
    private WidgetController widgetController;
    private FloatingDismissWidget floatingDismissWidget;
    private boolean isWidgetExpanded = false;
    public boolean skipNotifyOnWidgetStop = false;
    private Modal modal;
    private BroadcastReceiver systemNavigationButtonTapListener = null;
    private boolean isTextRecognitionEnabled = false;
    private AccessibilityNodeInfo selectedNode;
    private int minArea;
    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private Handler mediaProjectionIdleHandler = null;
    private Runnable mediaProjectionIdlePostRunnable = null;
    private ImageReader imageReader = null;
    private ClipboardManager clipboardManager;
    private ImageButton toggleModeButton;
    private TextHint textHint;
    private VirtualDisplay mediaProjectionDisplay;
    private boolean isAccessibilityServiceBusy;
    private ArrayList<String> collectedTexts = null;
    final private MessageHandler messageHandler;

    private CustomOverlayView rootOverlay;
    private int currentOrientation;
    private String insetSignature = "";
    final private SharedPreferences sharedPreferences;
    private BroadcastReceiver configurationChangeReceiver;
    public FloatingWidget(NodeExplorerAccessibilityService hostingService, boolean shouldExpand) {
        NodeExplorerAccessibilityService.isWidgetIsShowing = true;
        sharedPreferences = hostingService.getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
        this.hostingService = hostingService;
        messageHandler = new MessageHandler(hostingService);
        showFloatingWidget(shouldExpand);
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

    private void showResult(@Nullable Rect bound, String text) {
        if (text.isEmpty()) {
            if (isTextRecognitionEnabled)
                textHint.changeText(hostingService.getString(R.string.text_detection_failed));
            else
                textHint.changeText(hostingService.getString(R.string.text_detection_failed_in_text_mode));
            return;
        }
        View copyIndicator = overlayView.findViewById(R.id.text_copy_indicator);
        if (copyIndicator.getVisibility() != View.VISIBLE)
            copyIndicator.setVisibility(View.VISIBLE);
        rootOverlay.addNewBoundingBox(bound, collectedTexts, text);
        textHint.changeText("Text block added. Tap here to edit selection. Tap on selection block again to remove");
    }

    void saveTextToStorageIfNeeded(@Nullable String textToStore) {
        if (textToStore == null && collectedTexts.size() == 0) return;
        ArrayList<String> entries = new ArrayList<>();
        String text = textToStore == null ? String.join("\n", collectedTexts) : textToStore;
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
        messageHandler
                .registerReceiverOnce(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        overlayView.setVisibility(View.VISIBLE);
                        if (intent.hasExtra(Constants.SHOULD_CLOSE_MODAL)) {
                            modal.closeModal();
                            return;
                        }
                        String newText = intent.getStringExtra(Constants.PAYLOAD_EDIT_TEXT);
                        if (newText != null && !newText.trim().isEmpty()) {
                            textChangedListener.onTextChanged(newText);
                            selectedText.setText(newText);
                            new Handler().postDelayed(selectedText::selectAllText, 150);
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
            public void onOpened(ViewGroup inflatedView, boolean isModalAlreadyOpened) {
                if (isModalAlreadyOpened)
                    new Handler().postDelayed(() -> selectedText.selectAllText(), 150);
                else
                    selectedText.selectAllText();
            }

            @Override
            public void onHeaderBackPressed(ViewGroup modalWindow) {
                if (isDraftText)
                    super.onHeaderBackPressed(modalWindow);
                else {
                    if (editedText != null)
                        showEntryList(new EditedTextEntry(collectedTexts.size() > 0 ? itemIndex - 1 : itemIndex, editedText));
                    else
                        showEntryList(null);
                }
            }

            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                selectedText = inflatedView.findViewById(R.id.selectedText);
                selectedText.setText(text);
                inflatedView.findViewById(R.id.action_copy_entire_text).setOnClickListener(v -> {
                    selectedText.selectAllText();
                });
                inflatedView.findViewById(R.id.action_copy_selected_text).setOnClickListener(v -> {
                    String text = selectedText.getSelectedText();
                    copyTextAndDismiss(text, isDraftText);
                });
                inflatedView.findViewById(R.id.action_change_text_content).setOnClickListener(view -> {
                    setupEditTextContentModal(selectedText, new TextChangedListener() {
                        @Override
                        public void onTextChanged(String text) {
                            editedText = text;
                        }
                    });
                });
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
            textHint.changeTextAndNotify("Sorry cannot show recent items when device is locked");
            return;
        }
        modal.showModal(Modal.ModalType.ENTRY_LIST, new Modal.ModalCallback() {
            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                String jsonString = sharedPreferences.getString(Constants.ENTRIES_KEY, "[]");
                ArrayList<EntryListAdapter.Item> items = new ArrayList<>();
                if(collectedTexts.size() > 0)
                    items.add(new EntryListAdapter.Item(String.join("\n", collectedTexts), true));
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
                        setupEditSelectionModal(item.content, item.isDraft, index);
                    }

                    @Override
                    public void onTapCopyToClipboardShortcut(int index, EntryListAdapter.Item item) {
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
    private void toggleTextRecognitionMode(boolean skipCountdownTermination) {
        toggleModeButton.setImageResource(isTextRecognitionEnabled ? R.drawable.character : R.drawable.image);
        if (isTextRecognitionEnabled) {
            if (!skipCountdownTermination)
                startCountdownToTerminateMediaProjection();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (imageReader == null) {
                requestMediaProjection();
            } else {
                if (mediaProjectionIdleHandler != null) {
                    mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                    mediaProjectionIdlePostRunnable = null;
                    mediaProjectionIdleHandler = null;
                }
            }
        }
        isTextRecognitionEnabled = !isTextRecognitionEnabled;
        textHint.changeMode(isTextRecognitionEnabled);
    }

    private void toggleExpandWidget() {
        if (isWidgetExpanded) {
            modal.closeModal();
            clearAllSelections(true);
            widgetController.close();
            if (isTextRecognitionEnabled)
                startCountdownToTerminateMediaProjection();
            toggleListenToSystemBroadcastToCollapseWidget(false);
            textHint.setVisibility(View.GONE);
        } else {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R) && isTextRecognitionEnabled ) {
                if (imageReader == null) {
                    toggleTextRecognitionMode(true);
                } else {
                    if (mediaProjectionIdleHandler != null) {
                        mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                        mediaProjectionIdlePostRunnable = null;
                        mediaProjectionIdleHandler = null;
                    }
                }
            }

            textHint.changeMode(isTextRecognitionEnabled);
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

    private void clearAllSelections(boolean shouldStoreLastText) {
        if (collectedTexts.size() == 0) return;
        rootOverlay.clearAllSelections();
        if (shouldStoreLastText)
            saveTextToStorageIfNeeded(null);
        collectedTexts.clear();
        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);
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

    private void showFloatingWidget(boolean shouldExpand) {
        windowManager = (WindowManager) hostingService.getSystemService(Context.WINDOW_SERVICE);
        clipboardManager = (ClipboardManager) hostingService.getSystemService(Context.CLIPBOARD_SERVICE);

        // Inflate overlay layout
        overlayView = LayoutInflater.from(hostingService).inflate(R.layout.overlay_layout, null);
        modal = new Modal(overlayView);

        currentOrientation = hostingService.getResources().getConfiguration().orientation;
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
        widgetController = new WidgetController(hostingService, windowManager, params, overlayView);
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        if (shouldExpand) {
            toggleListenToSystemBroadcastToCollapseWidget(true);
            isWidgetExpanded = true;
            widgetController.prepareInitialState(true);
        } else
            widgetController.prepareInitialState(false);
        // Add the overlay view
        windowManager.addView(overlayView, params);
        // Find the button in the overlay
        rootOverlay = overlayView.findViewById(R.id.rootOverlay);

        toggleModeButton = overlayView.findViewById(R.id.toggle);
        ImageButton listButton = overlayView.findViewById(R.id.list);
        ImageButton stopButton = overlayView.findViewById(R.id.stop);
        ImageButton eraserButton = overlayView.findViewById(R.id.eraser);
        eraserButton.setOnClickListener(v -> {
            if (collectedTexts.size() == 0)
                textHint.changeText("No text selections to clear");
            else
                clearAllSelections(false);
        });
        textHint = overlayView.findViewById(R.id.text_hint);
        textHint.setOnTapListener(new TextHint.OnTapListener() {
            @Override
            public void onTap() {
                if (collectedTexts.size() == 0) return;
                String collectiveText = String.join("\n", collectedTexts);
                setupEditSelectionModal(collectiveText, true, -1);
            }
        });

        toggleModeButton.setOnClickListener(v -> toggleTextRecognitionMode(false));
        stopButton.setOnClickListener(v -> onStopWidget());
        listButton.setOnClickListener(v -> showEntryList(null));
        overlayView.findViewById(R.id.text_hint_close).setOnClickListener(v -> textHint.setVisibility(View.GONE));


        // Handle touch events on the overlay
        rootOverlay.setOnTapListener(new CustomOverlayView.OnTapListener() {
            @Override
            public void onTap(CoordinateF tappedCoordinate) {
                // Record tap coordinates
                if (isAccessibilityServiceBusy) {
                    textHint.changeTextAndNotify("Sorry, we are still busy looking for text element you tapped previously");
                    return;
                }
                int removedIndex = rootOverlay.removeSelection(tappedCoordinate.x, tappedCoordinate.y);
                if (removedIndex != -1) {
                    collectedTexts.remove(removedIndex);
                    if (collectedTexts.size() == 0)
                        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);
                    return;
                }
                overlayView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                        overlayView.removeOnLayoutChangeListener(this);
                        getNodeResult(tappedCoordinate.x, tappedCoordinate.y, !isTextRecognitionEnabled, new NodeCapturedCallback() {
                            @Override
                            void onResult(NodeResult result) {
                                // ...regain focus to overlay view to capture key event on system navigation back press
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                                windowManager.updateViewLayout(overlayView, params);

                                if (isTextRecognitionEnabled) {
                                    captureScreenshot(result.bound, (int) tappedCoordinate.x, (int) tappedCoordinate.y);
                                } else {
                                    showResult(result.bound, result.text);
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
                if (dragDistance > 50)
                    toggleExpandWidget();
                else {
                    textHint.changeText(hostingService.getString(R.string.text_hint_close_overlay));
                }
            }
        });

        rootOverlay.setOnDismissListener(new CustomOverlayView.OnDismissListener() {
            @Override
            protected void onDismiss() {
                if(isWidgetExpanded && !modal.handleSystemGoBack())
                    toggleExpandWidget();
            }
        });
        notifyStateOnQuickTile(true);
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
                floatingDismissWidget.onGestureStarted();
            }

            @Override
            public void onRelease() {
                floatingDismissWidget.onGestureReleased(enableButton, params);
            }
        }, overlayView, windowManager, params);
    }

    private void setIsProcessing(boolean isProcessing) {
        overlayView.setClickable(!isProcessing);
        if (!isProcessing)
            isAccessibilityServiceBusy = false;
        overlayView.findViewById(R.id.buttonContainer).setVisibility(isProcessing ? View.GONE : View.VISIBLE);
        overlayView.findViewById(R.id.loading).setVisibility(isProcessing ? View.VISIBLE : View.GONE);
    }

    void processScreenshot(Rect bounds, int tappedCoordinateX, int tappedCoordinateY, Bitmap screenBitmap) {
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        bounds.bottom = Math.min(bounds.bottom, screenBitmap.getHeight());
        bounds.top = Math.max(bounds.top, 0);

        Bitmap croppedImage = Bitmap.createBitmap(screenBitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
        setIsProcessing(true);
//            debugImage.setImageBitmap(croppedImage);
        recognizer.process(
                        InputImage.fromBitmap(croppedImage, 0)
                )
                .addOnSuccessListener(result -> {
                    List<Text.TextBlock> textBlocks = result.getTextBlocks();
                    Rect textBoundingBox = null;
                    String tappedText = "";
                    List<Pair<Rect, String>> filteredCandidates = new ArrayList<>();
                    for (Text.TextBlock block: textBlocks) {
                        Rect trialBoundingBox = block.getBoundingBox();
                        if (trialBoundingBox != null && trialBoundingBox.contains(tappedCoordinateX - bounds.left, tappedCoordinateY - bounds.top)) {
                            trialBoundingBox.offset(bounds.left, bounds.top);
                            filteredCandidates.add(new Pair<>(trialBoundingBox, block.getText()));
                        }
                    }

                    int minArea = Integer.MAX_VALUE;
                    for (Pair<Rect, String> value: filteredCandidates) {
                        int area = value.first.width() * value.first.height();
                        if (area < minArea) {
                            minArea = area;
                            textBoundingBox = value.first;
                            tappedText = value.second;
                        }
                    }
                    if (textBoundingBox != null) {
                        showResult(textBoundingBox, tappedText);
                    } else {
                        List<Text.TextBlock> blocks = result.getTextBlocks();
                        Rect maximumBound = new Rect(Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1);
                        if(blocks.size() > 0) {
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
                            showResult(maximumBound, result.getText());
                        } else
                            showResult(bounds, "");
                    }
                }).addOnCompleteListener(tsk -> setIsProcessing(false));
    }

    void changeOverlayContentVisibility(boolean isVisible) {
        overlayView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }
    void captureScreenshot(@Nullable Rect bounds, int tappedCoordinateX, int tappedCoordinateY) {
        if (bounds == null) {
            showResult(null, "");
            isAccessibilityServiceBusy = false;
            return;
        }

        changeOverlayContentVisibility(false);
//        ImageView debugImage = overlayView.findViewById(R.id.debug_image);
//        debugImage.setVisibility(View.GONE);
        new Handler().postDelayed(() -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hostingService.takeScreenshot(Display.DEFAULT_DISPLAY, hostingService.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull AccessibilityService.ScreenshotResult screenshotResult) {
                        changeOverlayContentVisibility(true);
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                        if (bitmap != null)
                            processScreenshot(
                                    bounds,
                                    tappedCoordinateX,
                                    tappedCoordinateY,
                                    bitmap
                            );
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
//            debugImage.setVisibility(View.VISIBLE);
                Image.Plane plane = image.getPlanes()[0];

                Bitmap screenBitmap = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(),
                        image.getHeight(), Bitmap.Config.ARGB_8888);
                screenBitmap.copyPixelsFromBuffer(plane.getBuffer());
                image.close();
                processScreenshot(bounds, tappedCoordinateX, tappedCoordinateY, screenBitmap);
            }
        }, 100);
    }

    private void runForegroundWithNotification(String title, String description) {
        NotificationManager notificationManager = (NotificationManager) hostingService.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("screen_capture", "Screen Capture Session", NotificationManager.IMPORTANCE_DEFAULT));
            hostingService.startForeground(100, new Notification.Builder(hostingService, "screen_capture").
                    setContentTitle(title)
                    .setContentText(description)
                    .build()
            );
        } else {
            hostingService.startForeground(100, new Notification.Builder(hostingService).
                    setContentTitle(title)
                    .setContentText(description)
                    .build()
            );
        }
    }

    private void requestMediaProjection() {
        overlayView.setVisibility(View.GONE);
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(overlayView, params);

        mediaProjectionManager =
                (MediaProjectionManager) hostingService.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        Intent activityIntent = new Intent(hostingService, MediaProjectionRequestActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra("screenCaptureIntent", screenCaptureIntent);

        messageHandler.registerReceiverOnce(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                windowManager.updateViewLayout(overlayView, params);
                overlayView.setVisibility(View.VISIBLE);
                int resultCode = intent == null ? Activity.RESULT_CANCELED : intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                if(resultCode != Activity.RESULT_OK) {
                    // if user rejected the media projection request revert back to text mode
                    toggleTextRecognitionMode(true);
                    return;
                }
                Intent data = intent.getParcelableExtra("data");
                runForegroundWithNotification("Capturing Screenshots", "session to capture screenshots");

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
                        if (mediaProjectionIdleHandler != null) {
                            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
                        }
                        if (isTextRecognitionEnabled && overlayView != null && isWidgetExpanded) {
                            toggleTextRecognitionMode(true);
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
            }
        }, Constants.MEDIA_PROJECTION_DATA);
        hostingService.startActivity(activityIntent);
    }

    private void notifyStateOnQuickTile(boolean newState) {
        if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        newState != sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(hostingService, new ComponentName(hostingService, ShortcutTileLauncher.class));
    }

    private void isInsideElement(AccessibilityNodeInfo node, int coordinateX, int coordinateY) {
        if (node == null) return;
        Rect nodeBound = new Rect();
        node.getBoundsInScreen(nodeBound);
        if (nodeBound.contains(coordinateX, coordinateY) && node.isVisibleToUser()) {
            int area = nodeBound.height() * nodeBound.width();
            if (area <= minArea) {
                if (isTextRecognitionEnabled || !getText(node).isEmpty())
                    selectedNode = node;
                minArea = area;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        } else if ((nodeBound.top == nodeBound.bottom)) {
            for (int i = 0; i < node.getChildCount(); i++) {
                isInsideElement(node.getChild(i), coordinateX, coordinateY);
            }
        }
    }

    private String getText(AccessibilityNodeInfo node) {
        CharSequence nodeText = node.getText();
        if (nodeText != null && !nodeText.toString().isEmpty()) {
            return nodeText.toString();
        }

        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null) {
            return contentDescription.toString();
        }
        return "";
    }

    private void freeSearch(AccessibilityNodeInfo node) {
        String text = getText(node);
        if (!text.isEmpty()) {
            Log.d("text", node.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            freeSearch(node.getChild(i));
        }
    }

    public void releaseResources() {
        if (systemNavigationButtonTapListener != null)
            hostingService.unregisterReceiver(systemNavigationButtonTapListener);
        hostingService.unregisterReceiver(configurationChangeReceiver);
        messageHandler.clearAllExcept(Constants.ACCESSIBILITY_SERVICE);
        floatingDismissWidget.clearResource();
        windowManager.removeView(overlayView);
        overlayView = null;

        saveTextToStorageIfNeeded(null);

        if (mediaProjectionIdleHandler != null) {
            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
            mediaProjectionIdleHandler = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (!skipNotifyOnWidgetStop)
            notifyStateOnQuickTile(false);
    }
    private void onStopWidget() {
        hostingService.onStopWidget();
    }

    static class NodeResult {
        public Rect bound;
        public String text;

        NodeResult(@Nullable Rect bound, String text) {
            this.bound = bound;
            this.text = text;
        }
    }
    static abstract class NodeCapturedCallback {
        abstract void onResult(NodeResult result);
    }
    private void getNodeResult(float x, float y, boolean shouldRetrieveText, NodeCapturedCallback resultCallback) {
        isAccessibilityServiceBusy = true;
        new Thread(() -> {
            selectedNode = null;
            minArea = Integer.MAX_VALUE;
            AccessibilityNodeInfo root = hostingService.getRootInActiveWindow();
//            freeSearch(root);
            isInsideElement(root, (int) x, (int) y);
            String extractedText = !shouldRetrieveText || selectedNode == null ? "" : getText(selectedNode).trim();
            Rect bounds = new Rect();
            NodeResult result;
            if (selectedNode != null) {
                selectedNode.getBoundsInScreen(bounds);
                result = new NodeResult(bounds, extractedText);
            } else {
                result = new NodeResult(null, "");
            }

            new Handler(Looper.getMainLooper()).post(() -> resultCallback.onResult(result));
        }).start();
    }
}
