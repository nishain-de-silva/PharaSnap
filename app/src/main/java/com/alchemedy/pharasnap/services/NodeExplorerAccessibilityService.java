package com.alchemedy.pharasnap.services;

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
import android.content.res.Configuration;
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
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.alchemedy.pharasnap.R;
import com.alchemedy.pharasnap.activities.MediaProjectionRequestActivity;
import com.alchemedy.pharasnap.helper.Constants;
import com.alchemedy.pharasnap.helper.CoordinateF;
import com.alchemedy.pharasnap.helper.OnTapListener;
import com.alchemedy.pharasnap.helper.ScreenInfo;
import com.alchemedy.pharasnap.utils.EntryListAdapter;
import com.alchemedy.pharasnap.utils.Modal;
import com.alchemedy.pharasnap.utils.WidgetController;
import com.alchemedy.pharasnap.widgets.CustomOverlayView;
import com.alchemedy.pharasnap.widgets.EnableButton;
import com.alchemedy.pharasnap.widgets.SelectionEditorTextView;
import com.alchemedy.pharasnap.widgets.TextHint;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class NodeExplorerAccessibilityService extends android.accessibilityservice.AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private WidgetController widgetController;
    private boolean isWidgetExpanded = false;
    private boolean skipNotifyOnWidgetStop = false;
    private Modal modal;
    private BroadcastReceiver systemNavigationButtonTapListener = null;
    private boolean isTextRecognitionEnabled = false;
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

    private CustomOverlayView rootOverlay;

    private int currentOrientation;
    private SharedPreferences sharedPreferences;

    private BroadcastReceiver tileMessageReceiver;
    private AccessibilityNodeInfo selectedNode;
    private int minArea;


    private ScreenInfo getScreenConfiguration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect screenBounds = windowManager.getMaximumWindowMetrics().getBounds();
            return new ScreenInfo(screenBounds.width(), screenBounds.height(), getResources().getConfiguration().screenHeightDp);
        } else {
            DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realDisplayMetrics);
            return new ScreenInfo(realDisplayMetrics.widthPixels, realDisplayMetrics.heightPixels, realDisplayMetrics.densityDpi);
        }
    }

    private void showResult(Rect bound, String text) {
        if (text.isEmpty()) {
            textHint.changeText(getString(R.string.text_detection_failed));
            return;
        }
        collectedTexts.add(text);
        View copyIndicator = overlayView.findViewById(R.id.text_copy_indicator);
        if (copyIndicator.getVisibility() != View.VISIBLE)
            copyIndicator.setVisibility(View.VISIBLE);
        rootOverlay.addNewBoundingBox(bound);
        textHint.changeText("Text block added. Tap here to edit selection. Tap on selection block again to remove");
    }

    void saveTextToStorageIfNeeded(@Nullable String textToStore) {
        if (collectedTexts.size() == 0) return;
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

    void setupEditSelectionModal(String text, boolean isDraftText) {
        modal.showModal(Modal.ModalType.EDIT_SELECTION, new Modal.ModalCallback() {
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
                else
                    showEntryList();
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
            }
        });
    }

    void showEntryList() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if(keyguardManager.isKeyguardLocked()) {
            textHint.changeText("Sorry cannot show recent items when device is locked");
            return;
        }
        modal.showModal(Modal.ModalType.ENTRY_LIST, new Modal.ModalCallback() {
            @Override
            public void onBeforeModalShown(ViewGroup inflatedView) {
                String jsonString = sharedPreferences.getString(Constants.ENTRIES_KEY, "[]");
                ArrayList<String> items = new ArrayList<>();
                try {
                    JSONArray jsonArray = new JSONArray(jsonString);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        items.add(jsonArray.getString(i));
                    }

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                EntryListAdapter adapter = new EntryListAdapter(NodeExplorerAccessibilityService.this, items, new EntryListAdapter.OnTapListener() {
                    @Override
                    public void onTap(int index, String text) {
                        setupEditSelectionModal(text, false);
                    }

                    @Override
                    public void onTapCopyToClipboardShortcut(int index, String text) {
                        copyTextAndDismiss(text, false);
                    }

                    @Override
                    public void onDelete(int index) {
                        items.remove(index);
                        sharedPreferences.edit().putString(Constants.ENTRIES_KEY, new JSONArray(items).toString())
                                .apply();
                    }
                });
                inflatedView.findViewById(R.id.list_clear).setOnClickListener(v -> {
                    adapter.clear();
                    sharedPreferences.edit().remove(Constants.ENTRIES_KEY).apply();
                });
                ListView listView = inflatedView.findViewById(R.id.entry_list);
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
        else {
            copyTextToClipboard(text);
            // collectedTexts should be size 0 to prevent storing the text
            collectedTexts.clear();
        }
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
            mediaProjectionIdleHandler.postDelayed(mediaProjectionIdlePostRunnable, 1000 * 10);
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
            unregisterReceiver(systemNavigationButtonTapListener);
            systemNavigationButtonTapListener = null;
            textHint.setVisibility(View.GONE);
        } else {
            if (isTextRecognitionEnabled) {
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
            systemNavigationButtonTapListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
//                    String reason = intent.getStringExtra("reason");
//                    if("homekey".equals(reason) || "recentapps".equals(reason)) {
//
//                    }
                    if(isWidgetExpanded)
                        toggleExpandWidget();
                }
            };
            textHint.changeMode(isTextRecognitionEnabled);
            registerReceiver(systemNavigationButtonTapListener, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            widgetController.open();
        }

        isWidgetExpanded = !isWidgetExpanded;
    }

    private void clearAllSelections(boolean shouldStoreLastText) {
        rootOverlay.clearAllSelections();
        if (shouldStoreLastText)
            saveTextToStorageIfNeeded(null);
        collectedTexts.clear();
        overlayView.findViewById(R.id.text_copy_indicator).setVisibility(View.GONE);
    }

    private boolean showFloatingWidget(boolean shouldExpand) {
        if (overlayView != null) {
            Toast.makeText(this, "Widget is already launched", Toast.LENGTH_SHORT).show();
            return false;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        // Inflate overlay layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        modal = new Modal(overlayView);

        currentOrientation = getResources().getConfiguration().orientation;
        collectedTexts = new ArrayList<>();
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
        }

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
            } catch (IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

        ((EnableButton) overlayView.findViewById(R.id.toggleCollapse))
                .configure(new OnTapListener() {
                    @Override
                    public void onTap(CoordinateF tappedCoordinate) {
                        toggleExpandWidget();
                    }
                }, overlayView, windowManager, params);

        widgetController = new WidgetController(this, windowManager, params, overlayView);
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Insets navigationBarInset = windowManager.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.navigationBars());
                widgetController.landscapeNavigationBarOffset = Math.max(navigationBarInset.left, navigationBarInset.right);
                params.x = widgetController.landscapeNavigationBarOffset;
            }
        }
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;

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
            clearAllSelections(false);
        });
        textHint = overlayView.findViewById(R.id.text_hint);
        textHint.onTextCaptured(new TextHint.OnTapListener() {
            @Override
            public void onTap() {
                if (collectedTexts.size() == 0) return;
                String collectiveText = String.join("\n", collectedTexts);
                setupEditSelectionModal(collectiveText, true);
            }
        });

        toggleModeButton.setOnClickListener(v -> toggleTextRecognitionMode(false));
        stopButton.setOnClickListener(v -> onStopWidget());
        listButton.setOnClickListener(v -> showEntryList());
        overlayView.findViewById(R.id.text_hint_close).setOnClickListener(v -> textHint.setVisibility(View.GONE));

        widgetController.prepareInitialState();

        // Handle touch events on the overlay
        rootOverlay.setOnTapListener(new OnTapListener() {
            @Override
            public void onTap(CoordinateF tappedCoordinate) {
                // Record tap coordinates
                if (isAccessibilityServiceBusy) {
                    textHint.changeText("Sorry, we are still busy looking for text element you tapped previously");
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
                                isAccessibilityServiceBusy = false;
                                // ...regain focus to overlay view to capture key event on system navigation back press
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                                windowManager.updateViewLayout(overlayView, params);

                                if (isTextRecognitionEnabled) {
                                    captureScreenshot(result.bound, (int) tappedCoordinate.x, (int) tappedCoordinate.y);
                                } else
                                    showResult(result.bound, result.text);
                            }
                        });
                    }
                });

                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(overlayView, params);
            }

            @Override
            public void onDrag(float dragDistance) {
                if (dragDistance > 50)
                    toggleExpandWidget();
                else {
                    textHint.changeText(getString(R.string.text_hint_close_overlay));
                }
            }
        });

        if (shouldExpand) {
            toggleExpandWidget();
        }

        rootOverlay.setOnDismissListener(new CustomOverlayView.OnDismissListener() {
            @Override
            protected void onDismiss() {
                if(isWidgetExpanded && !modal.handleSystemGoBack())
                    toggleExpandWidget();
            }
        });

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlayView != null && newConfig.orientation != currentOrientation) {
            currentOrientation = newConfig.orientation;
            if (isWidgetExpanded)
                clearAllSelections(false);
            if (mediaProjectionDisplay != null) {
                ScreenInfo screenInfo = getScreenConfiguration();
                mediaProjectionDisplay.resize(screenInfo.width, screenInfo.height, screenInfo.densityDpi);
                imageReader.close();
                imageReader = ImageReader.newInstance(screenInfo.width, screenInfo.height, PixelFormat.RGBA_8888, 1);
                mediaProjectionDisplay.setSurface(imageReader.getSurface());
            }

            ViewGroup buttonContainer = overlayView.findViewById(R.id.buttonContainer);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FrameLayout.LayoutParams buttonContainerParams = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
                widgetController.configureOverlayDimensions(buttonContainerParams, isWidgetExpanded, true);
            } else {
                if(isWidgetExpanded)
                    buttonContainer.setTranslationX(0);
                else {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
                    params.x = 0;
                    windowManager.updateViewLayout(overlayView, params);
                }
            }
        }
    }

    private void setIsProcessing(boolean isProcessing) {
        overlayView.setClickable(!isProcessing);
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
                    String tappedText = result.getText();
                    List<Pair<Rect, String>> candidates = new ArrayList<>();
                    for (Text.TextBlock block: textBlocks) {
                        Rect trialBoundingBox = block.getBoundingBox();
                        if (trialBoundingBox != null && trialBoundingBox.contains(tappedCoordinateX - bounds.left, tappedCoordinateY - bounds.top)) {
                            trialBoundingBox.offset(bounds.left, bounds.top);
                            candidates.add(new Pair<>(trialBoundingBox, block.getText()));
                        }
                    }

                    int minArea = Integer.MAX_VALUE;
                    for (Pair<Rect, String> value: candidates) {
                        int area = value.first.width() * value.first.height();
                        if (area < minArea) {
                            minArea = area;
                            textBoundingBox = value.first;
                            tappedText = value.second;
                        }
                    }
                    if (textBoundingBox != null) {
                        showResult(textBoundingBox, tappedText);
                    } else
                        showResult(bounds, tappedText);
                }).addOnCompleteListener(tsk -> setIsProcessing(false));
    }

    void changeOverlayContentVisibility(boolean isVisible) {
        overlayView.findViewById(R.id.buttonContainer).setVisibility(isVisible ? View.VISIBLE : View.GONE);
        textHint.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    void captureScreenshot(Rect bounds, int tappedCoordinateX, int tappedCoordinateY) {
        changeOverlayContentVisibility(false);
//        ImageView debugImage = overlayView.findViewById(R.id.debug_image);
//        debugImage.setVisibility(View.GONE);
        new Handler().postDelayed(() -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
                        changeOverlayContentVisibility(true);
                        processScreenshot(
                                bounds,
                                tappedCoordinateX,
                                tappedCoordinateY,
                                Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace())
                        );
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
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel("screen_capture", "Screen Capture Session", NotificationManager.IMPORTANCE_DEFAULT));
            startForeground(100, new Notification.Builder(NodeExplorerAccessibilityService.this, "screen_capture").
                    setContentTitle(title)
                    .setContentText(description)
                    .build()
            );
        } else {
            startForeground(100, new Notification.Builder(NodeExplorerAccessibilityService.this).
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
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();;
        Intent activityIntent = new Intent(this, MediaProjectionRequestActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra("screenCaptureIntent", screenCaptureIntent);

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                windowManager.updateViewLayout(overlayView, params);
                overlayView.setVisibility(View.VISIBLE);
                LocalBroadcastManager.getInstance(NodeExplorerAccessibilityService.this).unregisterReceiver(this);
                int resultCode = intent == null ? Activity.RESULT_CANCELED : intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                if(resultCode != Activity.RESULT_OK) {
                    Toast.makeText(context, "Screen recording is needed to capture text on images", Toast.LENGTH_SHORT).show();
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
        }, new IntentFilter("MEDIA_PROJECTION_DATA"));
        startActivity(activityIntent);
    }

    private void notifyStateOnQuickTile(boolean newState) {
        if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                newState != sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
    }

    private void isInsideElement(AccessibilityNodeInfo node, int coordinateX, int coordinateY) {
        if (node == null) return;
        Rect nodeBound = new Rect();
        node.getBoundsInScreen(nodeBound);
        if (nodeBound.contains(coordinateX, coordinateY)) {
            int area = nodeBound.height() * nodeBound.width();
            if ((area < minArea || (area == minArea && !getText(node).isEmpty())) && node.isVisibleToUser()) {
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
        Rect outBounds = new Rect();
        node.getBoundsInScreen(outBounds);
        String text = getText(node);
        if (!text.isEmpty())
            Log.d("text", text);
        for (int i = 0; i < node.getChildCount(); i++) {
            freeSearch(node.getChild(i));
        }
    }

    private void onStopWidget() {
        if (overlayView == null) return;
        if (systemNavigationButtonTapListener != null) {
            unregisterReceiver(systemNavigationButtonTapListener);
            systemNavigationButtonTapListener = null;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
        rootOverlay = null;
        toggleModeButton = null;
        textHint = null;
        modal = null;
        widgetController = null;
        windowManager = null;
        saveTextToStorageIfNeeded(null);
        clipboardManager = null;
        collectedTexts = null;

        if (mediaProjectionIdleHandler != null) {
            mediaProjectionIdleHandler.removeCallbacks(mediaProjectionIdlePostRunnable);
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (!skipNotifyOnWidgetStop)
            notifyStateOnQuickTile(false);
        skipNotifyOnWidgetStop = false;
        isWidgetExpanded = false;
        isTextRecognitionEnabled = false;
        isAccessibilityServiceBusy = false;
    }

    static class NodeResult {
        public Rect bound;
        public String text;

        NodeResult(Rect bound, String text) {
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
            AccessibilityNodeInfo root = getRootInActiveWindow();
            isInsideElement(root, (int) x, (int) y);
//            freeSearch(root);
            String extractedText = !shouldRetrieveText || selectedNode == null ? "" : getText(selectedNode).trim();
            Rect bounds = new Rect();
            if (selectedNode != null)
                selectedNode.getBoundsInScreen(bounds);

            new Handler(Looper.getMainLooper()).post(() -> resultCallback.onResult(new NodeResult(bounds, extractedText)));
        }).start();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // when this service is created at phone-reboot or re-created by system my memory cleanup
        // it will check the current stale active state and turn it off.
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCE_KEY, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sharedPreferences.getBoolean(Constants.TILE_ACTIVE_KEY, false))
            ShortcutTileLauncher.requestListeningState(this, new ComponentName(this, ShortcutTileLauncher.class));
        tileMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean shouldSkipNotify = intent.hasExtra(Constants.SKIP_NOTIFY_QUICK_TILE);
                if (intent.hasExtra(Constants.STOP_WIDGET)) {
                    if (shouldSkipNotify)
                        skipNotifyOnWidgetStop = true;
                    onStopWidget();
                } else if(!shouldSkipNotify) {
                    if (showFloatingWidget(false))
                        notifyStateOnQuickTile(true);
                } else
                    showFloatingWidget(true);
                }
        };
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(tileMessageReceiver, new IntentFilter(Constants.ACCESSIBILITY_SERVICE));
    }

    @Override
    public void onDestroy() {
        if (tileMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(tileMessageReceiver);
            tileMessageReceiver = null;
        }
        super.onDestroy();
        onStopWidget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

    @Override
    public void onInterrupt() {
        onStopWidget();
    }
}
